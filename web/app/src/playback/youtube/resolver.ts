import type { ApiResponse, Innertube, YT } from 'youtubei.js/web';
import { trackLog } from '../trackLog';
import { botguard } from './botguard';

/**
 * Which identity asks YouTube for a track, in order (prompt §5.2 step 5,
 * following StreamResolver.kt / InnerTubeXResolver.kt).
 *
 * InnerTubeX's automatic catalog leads with VISIONOS, then the TV clients,
 * then WEB_REMIX and the other web clients. Measured from a browser-shaped
 * request (text/plain, Chrome UA, 2026-10-15): VISIONOS, YTMUSIC (YouTube.js's
 * name for WEB_REMIX), WEB and MWEB all answer OK with a SABR URL; TV answers
 * "The page needs to be reloaded" and WEB_EMBEDDED "This video is unavailable",
 * so those two are left out. googlevideo's own SABR example uses VISIONOS.
 */
export const CLIENT_ORDER = ['VISIONOS', 'YTMUSIC', 'WEB', 'MWEB'] as const;
export type PlayerClientName = (typeof CLIENT_ORDER)[number];

/**
 * The BotGuard (WebPO) clients. As on Android (PoTokenGenerator /
 * InnerTubeX's WEB_BOTGUARD provider), they get two tokens: one bound to the
 * session's visitor data for the player request, and one bound to the
 * videoId for the stream. VISIONOS is not one of them.
 */
const WEBPO_CLIENTS: ReadonlySet<PlayerClientName> = new Set(['YTMUSIC', 'WEB', 'MWEB']);

export function usesWebPo(client: PlayerClientName): boolean {
  return WEBPO_CLIENTS.has(client);
}

/** The player-request token for [client], or undefined for a client that doesn't take one. */
async function playerRequestToken(yt: Innertube, client: PlayerClientName): Promise<string | undefined> {
  if (!WEBPO_CLIENTS.has(client)) return undefined;
  const visitorData = yt.session.context.client.visitorData;
  if (!visitorData) return undefined;
  return botguard.mint(decodeURIComponent(visitorData));
}

/** StreamResolver: how many clients are worth trying for one track. */
export const CLIENT_ATTEMPTS = 3;
/** InnerTubeXResolver.EXCLUDE_MS: a client that refused a track sits out for this long. */
const EXCLUDE_MS = 10 * 60_000;
/** StreamResolver.UNPLAYABLE_TTL_MS: a settled "no" isn't asked again for this long. */
const UNPLAYABLE_TTL_MS = 10 * 60_000;
const MAX_REMEMBERED = 64;

/**
 * A track no client will play, for a reason that will read the same in ten
 * seconds: an age gate, a region block, a takedown. Its own type so callers
 * can tell it from a failure worth retrying (StreamResolver's
 * PermanentlyUnplayableException).
 */
export class UnplayableError extends Error {
  override readonly name = 'UnplayableError';
}

/** A client answered OK, with a stream to play (direct URLs, a SABR URL, or both). */
export interface ResolvedPlayer {
  videoId: string;
  client: PlayerClientName;
  info: YT.VideoInfo;
}

// ---- Verdicts and exclusions ----------------------------------------------------

const unplayable = new Map<string, { reason: string; at: number }>();
const excluded = new Map<string, Map<PlayerClientName, number>>();

export function unplayableReason(videoId: string): string | null {
  const verdict = unplayable.get(videoId);
  if (!verdict) return null;
  if (Date.now() - verdict.at < UNPLAYABLE_TTL_MS) return verdict.reason;
  unplayable.delete(videoId);
  return null;
}

function rememberUnplayable(videoId: string, reason: string) {
  if (unplayable.size > MAX_REMEMBERED) unplayable.clear();
  unplayable.set(videoId, { reason, at: Date.now() });
  trackLog.w(`not playable: ${reason}; not asking again for 10 minutes`, videoId);
}

/** Records that [client] failed [videoId], so the next walk skips it for a while. */
export function excludeClient(videoId: string, client: PlayerClientName, why: string) {
  trackLog.w(`${client} refused: ${why}; skipping it for this track`, videoId);
  let entries = excluded.get(videoId);
  if (!entries) {
    if (excluded.size > MAX_REMEMBERED) excluded.clear();
    entries = new Map();
    excluded.set(videoId, entries);
  }
  entries.set(client, Date.now() + EXCLUDE_MS);
}

/** Debug override (Debug page): localStorage "bitchord:debug:clients" = "YTMUSIC,WEB". */
export const DEBUG_CLIENTS_KEY = 'bitchord:debug:clients';

function clientOrder(): readonly PlayerClientName[] {
  const forced = localStorage.getItem(DEBUG_CLIENTS_KEY)
    ?.split(',')
    .map((c) => c.trim())
    .filter((c): c is PlayerClientName => (CLIENT_ORDER as readonly string[]).includes(c));
  return forced?.length ? forced : CLIENT_ORDER;
}

export function clientsFor(videoId: string): PlayerClientName[] {
  const entries = excluded.get(videoId);
  const now = Date.now();
  return clientOrder().filter((client) => {
    const until = entries?.get(client);
    return until === undefined || until <= now;
  });
}

/** Signing in or out changes what YouTube will serve; old verdicts don't apply (StreamResolver.onSessionChanged). */
export function onSessionChanged() {
  unplayable.clear();
  excluded.clear();
}

// ---- The player request ------------------------------------------------------

/**
 * Asks [client] for [videoId]'s player response. The parameters follow
 * googlevideo's example; `reloadPlaybackContext` is set when the SABR server
 * asks for a fresh response mid-stream.
 *
 * No `adPlaybackContext`. Kira sends `{ pyv: true }`, and with it YouTube
 * mints streams that googlevideo cuts off after ~1.3 MB, about 70s of audio
 * (measured 2026-10-15: 4 of 4 VISIONOS URLs refused a range past that point
 * with it, 0 of 4 without; the browser saw the same wall over SABR).
 */
export async function playerRequest(
  yt: Innertube,
  videoId: string,
  client: PlayerClientName,
  reloadPlaybackContext?: unknown,
): Promise<ApiResponse> {
  const poToken = await playerRequestToken(yt, client);
  return yt.actions.execute('/player', {
    ...(poToken && { serviceIntegrityDimensions: { poToken } }),
    videoId,
    contentCheckOk: true,
    racyCheckOk: true,
    client,
    playbackContext: {
      contentPlaybackContext: { signatureTimestamp: yt.session.player?.signature_timestamp },
      ...(reloadPlaybackContext !== undefined && { reloadPlaybackContext }),
    },
  });
}

/** Why a playability status is a verdict on the track rather than on the client, or null if it isn't. */
function permanentReason(status: string | undefined, reason: string | undefined, signedIn: boolean): string | null {
  const text = (reason ?? '').toLowerCase();
  if (/confirm your age|age-restricted|inappropriate for some users/.test(text)) {
    return signedIn
      ? 'YouTube will not serve this age-restricted track to this app.'
      : 'This track is age-restricted. Sign in to YouTube to play it.';
  }
  if (/country|region/.test(text)) return 'This track isn’t available in your country';
  if (/private/.test(text)) return 'This track is private';
  if (/terminated/.test(text)) return 'The channel behind this track was terminated';
  if (/premium/.test(text)) return 'This track needs YouTube Music Premium';
  if (status === 'ERROR' && /removed|no longer available|does not exist/.test(text)) return 'This track was removed from YouTube';
  return null;
}

/**
 * Walks the clients until one answers OK. Each refusal is logged; if every
 * client refuses for a reason that is about the track, the verdict is
 * remembered so retries above this layer are answered without a request.
 *
 * @param skip clients the caller has already seen fail at the streaming stage.
 */
export async function resolvePlayer(
  yt: Innertube,
  videoId: string,
  skip: ReadonlySet<PlayerClientName> = new Set(),
  signedIn = false,
): Promise<ResolvedPlayer> {
  const known = unplayableReason(videoId);
  if (known) throw new UnplayableError(known);

  const { Utils, YT } = await import('youtubei.js/web');
  const candidates = clientsFor(videoId).filter((c) => !skip.has(c)).slice(0, CLIENT_ATTEMPTS);
  let lastReason = 'No YouTube client would play this track';
  let permanent: string | null = null;

  for (const client of candidates) {
    const started = performance.now();
    try {
      const response = await playerRequest(yt, videoId, client);
      const info = new YT.VideoInfo([response], yt.actions, Utils.generateRandomString(16));
      const status = info.playability_status?.status;
      const ms = Math.round(performance.now() - started);
      const streaming = info.streaming_data;
      const playable = !!streaming?.server_abr_streaming_url || !!streaming?.adaptive_formats.some((f) => f.has_audio && !f.has_video);
      if (status === 'OK' && playable) {
        trackLog.d(`${client} answered OK in ${ms}ms`, videoId);
        return { videoId, client, info };
      }
      const reason = info.playability_status?.reason ?? (status === 'OK' ? 'no audio stream in the response' : status ?? 'no status');
      lastReason = reason;
      permanent = permanentReason(status, reason, signedIn) ?? permanent;
      excludeClient(videoId, client, `${status}: ${reason} (${ms}ms)`);
    } catch (error) {
      lastReason = String(error);
      excludeClient(videoId, client, `request failed: ${String(error)}`);
    }
  }

  if (permanent) {
    rememberUnplayable(videoId, permanent);
    throw new UnplayableError(permanent);
  }
  throw new Error(lastReason);
}
