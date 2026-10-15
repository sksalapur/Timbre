import type { YtAccount, YtAuthState, YtChannel } from '@bitchord/shared/bridge';
import { installExtensionFetchRule } from './rules';

/**
 * YouTube Music sign-in (prompt §5.3): the user's own browser session, used
 * from here and nowhere else. The cookies stay in the browser's jar (requests
 * go out with `credentials: 'include'`), the SAPISIDHASH is computed here, and
 * the page only ever learns names and pictures.
 *
 * The scheme is the one Innertube.kt and DesktopYouTubeAuth.kt use: the
 * signature over SAPISID, `X-Goog-AuthUser` to pick the login in the jar, and
 * `X-Goog-PageId` for a brand channel. Logins and channels are listed the way
 * the desktop app lists them: `account/account_menu` for each login, then
 * `account/accounts_list` for that login's channels.
 */

export const MUSIC_ORIGIN = 'https://music.youtube.com';

/** The cookies the signature can be taken over, in the order Google's clients prefer them. */
const SIGNING_COOKIES = ['SAPISID', '__Secure-3PAPISID', '__Secure-1PAPISID'];
/** Cookies whose arrival or departure means a sign-in or sign-out (SIDCC and friends rotate constantly). */
const SESSION_COOKIES = new Set([...SIGNING_COOKIES, 'SID', '__Secure-1PSID', '__Secure-3PSID']);

/** Any recent WEB_REMIX version is accepted for the account endpoints (Innertube.kt keeps a fallback the same way). */
const WEB_REMIX_VERSION = '1.20251006.03.00';
/** How many logins a jar is searched for. Google allows ten; more than five in one browser is rare. */
const MAX_LOGINS = 5;
/** Accounts rarely change; a cookie change clears this at once anyway. */
const ACCOUNTS_TTL_MS = 10 * 60_000;

// ---- Preferences --------------------------------------------------------------

interface Prefs {
  /** "Disconnect" sets this false; the Google session itself is left alone. */
  connected: boolean;
  selectedChannelId: string | null;
}

const PREFS_KEY = 'yt.prefs';

async function loadPrefs(): Promise<Prefs> {
  const stored = (await browser.storage.local.get(PREFS_KEY))[PREFS_KEY] as Partial<Prefs> | undefined;
  return { connected: stored?.connected ?? true, selectedChannelId: stored?.selectedChannelId ?? null };
}

async function savePrefs(change: Partial<Prefs>): Promise<void> {
  await browser.storage.local.set({ [PREFS_KEY]: { ...(await loadPrefs()), ...change } });
}

// ---- Signing --------------------------------------------------------------------

async function signingSecret(): Promise<string | null> {
  for (const name of SIGNING_COOKIES) {
    const cookie = await browser.cookies.get({ url: `${MUSIC_ORIGIN}/`, name }).catch(() => null);
    if (cookie?.value) return cookie.value;
  }
  return null;
}

/** `SAPISIDHASH <seconds>_<sha1(seconds + " " + secret + " " + origin)>`, as DesktopYouTubeAuth.sapisidHash. */
async function sapisidHash(secret: string, origin: string): Promise<string> {
  const timestamp = Math.floor(Date.now() / 1000);
  const digest = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(`${timestamp} ${secret} ${origin}`));
  const hex = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
  return `SAPISIDHASH ${timestamp}_${hex}`;
}

/** Who a signed request acts as. */
interface Identity {
  authUser: string;
  pageId: string | null;
}

export class SignedOutError extends Error {
  readonly code = 'unauthenticated';
}

let ruleReady: Promise<void> | null = null;

/**
 * A request made as [identity]. The browser attaches the cookies; the
 * extension's session rule gives it the YouTube Origin the signature was
 * computed for (rules.ts, installExtensionFetchRule).
 */
export async function signedFetch(
  url: string,
  init: { method?: string; headers?: Record<string, string>; body?: BodyInit | null },
  identity: Identity,
): Promise<Response> {
  const secret = await signingSecret();
  if (!secret) throw new SignedOutError('The browser is not signed in to YouTube.');
  ruleReady ??= installExtensionFetchRule().catch((error: unknown) => {
    ruleReady = null;
    throw error;
  });
  await ruleReady;

  const origin = new URL(url).origin;
  const headers = new Headers(init.headers);
  headers.set('Authorization', await sapisidHash(secret, origin));
  headers.set('X-Goog-AuthUser', identity.authUser);
  headers.set('X-Origin', origin);
  if (identity.pageId) headers.set('X-Goog-PageId', identity.pageId);
  else headers.delete('X-Goog-PageId');
  return fetch(url, { method: init.method ?? 'GET', headers, body: init.body ?? null, credentials: 'include' });
}

async function musicApi(endpoint: string, identity: Identity): Promise<unknown> {
  const response = await signedFetch(
    `${MUSIC_ORIGIN}/youtubei/v1/${endpoint}?prettyPrint=false`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Youtube-Client-Name': '67',
        'X-Youtube-Client-Version': WEB_REMIX_VERSION,
      },
      body: JSON.stringify({
        context: { client: { clientName: 'WEB_REMIX', clientVersion: WEB_REMIX_VERSION, hl: 'en', gl: 'US' }, user: {} },
      }),
    },
    identity,
  );
  if (!response.ok) throw new Error(`${endpoint} answered ${response.status}`);
  return response.json();
}

// ---- Reading the account renderers (DesktopAccountParser) ----------------------

type Json = Record<string, unknown>;

/** Every object stored under [key], anywhere inside. */
function renderers(root: unknown, key: string, out: Json[] = []): Json[] {
  if (Array.isArray(root)) for (const item of root) renderers(item, key, out);
  else if (root && typeof root === 'object') {
    for (const [k, v] of Object.entries(root)) {
      if (k === key && v && typeof v === 'object' && !Array.isArray(v)) out.push(v as Json);
      else renderers(v, key, out);
    }
  }
  return out;
}

/** The first string under [key], anywhere inside. */
function findString(root: unknown, key: string): string | null {
  if (Array.isArray(root)) {
    for (const item of root) {
      const found = findString(item, key);
      if (found) return found;
    }
  } else if (root && typeof root === 'object') {
    for (const [k, v] of Object.entries(root)) {
      if (k === key && typeof v === 'string' && v) return v;
      const found = findString(v, key);
      if (found) return found;
    }
  }
  return null;
}

/** A `runs` or `simpleText` field as plain text. */
function text(node: unknown): string {
  const n = node as { runs?: { text?: string }[]; simpleText?: string } | undefined;
  if (n?.runs) return n.runs.map((r) => r.text ?? '').join('');
  return n?.simpleText ?? '';
}

function photo(item: Json): string | null {
  const thumbnails = (item.accountPhoto as { thumbnails?: { url?: string }[] } | undefined)?.thumbnails;
  return thumbnails?.at(-1)?.url ?? null;
}

interface StoredChannel extends YtChannel {
  authUser: string;
  pageId: string | null;
  /** YouTube's own choice for this login, which is the default here too. */
  selectedOnWeb: boolean;
}

interface StoredAccount extends Omit<YtAccount, 'channels'> {
  channels: StoredChannel[];
}

function channelId(authUser: string, pageId: string | null): string {
  return `${authUser}:${pageId ?? 'own'}`;
}

function parseChannels(root: unknown, authUser: string): StoredChannel[] {
  const channels: StoredChannel[] = [];
  for (const item of renderers(root, 'accountItem')) {
    const name = text(item.accountName);
    if (!name) continue;
    const pageId = findString(item, 'pageId');
    const handle = text(item.channelHandle) || text(item.accountByline) || null;
    const id = channelId(authUser, pageId);
    if (channels.some((c) => c.id === id)) continue;
    channels.push({
      id,
      name,
      handle,
      avatarUrl: photo(item),
      brand: pageId !== null,
      authUser,
      pageId,
      selectedOnWeb: item.isSelected === true,
    });
  }
  return channels;
}

async function readAccounts(): Promise<StoredAccount[]> {
  const accounts: StoredAccount[] = [];
  const seen = new Set<string>();
  for (let index = 0; index < MAX_LOGINS; index++) {
    const authUser = String(index);
    const identity = { authUser, pageId: null };
    let header: Json | undefined;
    try {
      header = renderers(await musicApi('account/account_menu', identity), 'activeAccountHeaderRenderer')[0];
    } catch {
      break;
    }
    if (!header) break;
    const name = text(header.accountName);
    const email = text(header.email) || null;
    // Past the last login Google answers with the first one again.
    const key = email ?? `${name}|${text(header.channelHandle)}`;
    if (seen.has(key)) break;
    seen.add(key);

    let channels: StoredChannel[] = [];
    try {
      channels = parseChannels(await musicApi('account/accounts_list', identity), authUser);
    } catch {
      // The switcher isn't answered for every account; the login's own channel always exists.
    }
    if (channels.length === 0) {
      channels = [
        {
          id: channelId(authUser, null),
          name: name || 'YouTube Music',
          handle: text(header.channelHandle) || email,
          avatarUrl: photo(header),
          brand: false,
          authUser,
          pageId: null,
          selectedOnWeb: true,
        },
      ];
    }
    accounts.push({ authUser, name, email, avatarUrl: photo(header), channels });
  }
  return accounts;
}

// ---- State --------------------------------------------------------------------

let cache: { at: number; accounts: StoredAccount[] } | null = null;
let reading: Promise<StoredAccount[]> | null = null;

async function accounts(refresh: boolean): Promise<StoredAccount[]> {
  if (!refresh && cache && Date.now() - cache.at < ACCOUNTS_TTL_MS) return cache.accounts;
  reading ??= readAccounts()
    .then((list) => {
      cache = { at: Date.now(), accounts: list };
      return list;
    })
    .finally(() => (reading = null));
  return reading;
}

/** Forgets the account list, after a sign-in or sign-out in the browser. */
export function invalidateAccounts(): void {
  cache = null;
}

export function isSessionCookie(cookie: { name: string; domain: string }): boolean {
  return SESSION_COOKIES.has(cookie.name) && /(^|\.)youtube\.com$/.test(cookie.domain.replace(/^\./, ''));
}

function chooseChannel(list: StoredAccount[], selectedId: string | null): StoredChannel | null {
  const all = list.flatMap((a) => a.channels);
  return (
    all.find((c) => c.id === selectedId) ??
    list[0]?.channels.find((c) => c.selectedOnWeb) ??
    list[0]?.channels[0] ??
    null
  );
}

function publicChannel({ id, name, handle, avatarUrl, brand }: StoredChannel): YtChannel {
  return { id, name, handle, avatarUrl, brand };
}

export async function authState(refresh = false): Promise<YtAuthState> {
  const prefs = await loadPrefs();
  const browserSignedIn = (await signingSecret()) !== null;
  if (!browserSignedIn || !prefs.connected) {
    return { browserSignedIn, connected: prefs.connected, signedIn: false, accounts: [], active: null };
  }
  const list = await accounts(refresh);
  const active = chooseChannel(list, prefs.selectedChannelId);
  return {
    browserSignedIn,
    connected: true,
    signedIn: active !== null,
    accounts: list.map((a) => ({ ...a, channels: a.channels.map(publicChannel) })),
    active: active && { ...publicChannel(active), authUser: active.authUser },
  };
}

/** The identity signed requests use right now, or null when they can't be made. */
export async function activeIdentity(): Promise<Identity | null> {
  const prefs = await loadPrefs();
  if (!prefs.connected || (await signingSecret()) === null) return null;
  const active = chooseChannel(await accounts(false), prefs.selectedChannelId);
  return active && { authUser: active.authUser, pageId: active.pageId };
}

export async function selectChannel(id: string): Promise<YtAuthState> {
  const list = await accounts(false);
  if (!list.some((a) => a.channels.some((c) => c.id === id))) throw new Error(`No channel ${id} in this browser`);
  await savePrefs({ selectedChannelId: id });
  return authState();
}

export async function setConnected(connected: boolean): Promise<YtAuthState> {
  await savePrefs({ connected });
  return authState(connected);
}
