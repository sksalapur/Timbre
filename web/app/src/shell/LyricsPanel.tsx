// Registers <am-lyrics>.
import '@uimaxbai/am-lyrics/am-lyrics.js';
import { Check, ChevronDown, Loader2, Minus, Settings2 } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useCanvas } from '@/data/spotify/canvas';
import { getBridge } from '@/extension/bridge';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { LyricClock } from '@/lyrics/clock';
import { onUngrantedChange, ungrantedOrigins } from '@/lyrics/http';
import type { SourceState } from '@/lyrics/repository';
import { enabledSources } from '@/lyrics/settings';
import { sourceById, type LyricsSourceId } from '@/lyrics/sources';
import { useLyrics, useLyricsUi } from '@/lyrics/useLyrics';
import { getAudio, usePlayer } from '@/state/player';
import { LyricsSettings } from './LyricsSettings';
import styles from './LyricsPanel.module.css';

type AmLyricsElement = HTMLElement & {
  currentTime: number;
  ttml?: string;
  interpolate: boolean;
  autoScroll: boolean;
  highlightColor: string;
  fontFamily?: string;
  updateComplete?: Promise<unknown>;
};

/**
 * am-lyrics' own footer offers to fetch from its own providers; ours decide
 * (prompt §10.2). It stays in the layout, only unseen: am-lyrics measures its
 * scroll range to the footer, and `display: none` collapsed that range to zero,
 * so the lyrics never scrolled.
 */
const HIDE_FOOTER = '.lyrics-footer { visibility: hidden !important; pointer-events: none !important; }';

/** The phone's lyric typeface (@font-face in LyricsPanel.module.css), then the app's. */
const LYRIC_FONT = "'BitChord SF Pro Display', 'DM Sans', system-ui, sans-serif";

let lyricFont: Promise<void> | null = null;
let lyricFontLoaded = false;

/**
 * Both weights, fetched once. am-lyrics measures its lines for the scroll and
 * the wipe, so it's only built once the face is in: built earlier, it laid
 * out in the fallback and then jumped when the font arrived. A slow network
 * gives up after 1.5 s and lets the fallback show.
 */
function loadLyricFont(): Promise<void> {
  lyricFont ??= Promise.race([
    Promise.all([document.fonts.load(`700 28px ${LYRIC_FONT}`), document.fonts.load(`400 28px ${LYRIC_FONT}`)]),
    new Promise((resolve) => setTimeout(resolve, 1500)),
  ])
    .catch(() => undefined)
    .then(() => {
      lyricFontLoaded = true;
    });
  return lyricFont;
}

function useLyricFont(): boolean {
  const [ready, setReady] = useState(lyricFontLoaded);
  useEffect(() => {
    if (!ready) void loadLyricFont().then(() => setReady(true));
  }, [ready]);
  return ready;
}

/**
 * am-lyrics fades the four lines either side of the current one (0.7, 0.58,
 * 0.46) and leaves every line past them at its base 0.8, so the fifth line on
 * came back brighter. Those stay at the farthest step. Gaps keep their own
 * opacity, and the library's !important rules (scrolling by hand, unsynced
 * lyrics) still win.
 */
const FAR_LINES =
  '.lyrics-line:not(.active):not(.pre-active):not(.lyrics-activest):not(.post-active-line):not(.next-active-line)' +
  ':not(.prev-2):not(.prev-3):not(.next-2):not(.next-3):not(.lyrics-gap) { opacity: 0.46; }';

/**
 * am-lyrics, exactly as it is, fed our TTML and a steered clock (prompt §10.2).
 * The clock reads the media time each frame, stamps the reading with when it
 * was taken, and is told about every seek and track change: the "lyric clock
 * dates readings by sample time" lesson. No React state per frame: the time is
 * written straight onto the element.
 */
function LyricsView({ ttml }: { ttml: string }) {
  const host = useRef<HTMLDivElement>(null);
  const element = useRef<AmLyricsElement | null>(null);
  const deck = usePlayer((s) => s.deck);
  const videoId = usePlayer((s) => s.nowPlaying?.videoId);

  // Created by hand, with its TTML set before it's attached: am-lyrics starts
  // loading the moment it connects, and through React the properties arrive
  // a beat later, so its first load went to its own providers with no title
  // and finished after ours, wiping it.
  useEffect(() => {
    const el = document.createElement('am-lyrics') as AmLyricsElement;
    el.ttml = ttml;
    el.interpolate = true;
    el.autoScroll = true;
    el.highlightColor = '#ffffff';
    el.fontFamily = LYRIC_FONT;
    el.className = styles.lyrics!;
    const onLineClick = (event: Event) => {
      const timestamp = (event as CustomEvent<{ timestamp: number }>).detail?.timestamp;
      if (typeof timestamp === 'number') usePlayer.getState().seek(timestamp / 1000);
    };
    el.addEventListener('line-click', onLineClick);
    host.current?.append(el);
    element.current = el;
    void Promise.resolve(el.updateComplete).then(() => {
      const root = el.shadowRoot;
      if (!root) return;
      const sheet = new CSSStyleSheet();
      sheet.replaceSync(HIDE_FOOTER + FAR_LINES);
      root.adoptedStyleSheets = [...root.adoptedStyleSheets, sheet];
    });
    return () => {
      el.removeEventListener('line-click', onLineClick);
      el.remove();
      element.current = null;
    };
    // Mounted once; a new provider's TTML is handed over below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (element.current && element.current.ttml !== ttml) element.current.ttml = ttml;
  }, [ttml]);

  useEffect(() => {
    const audio = getAudio();
    const el = element.current;
    if (!audio || !el) return;
    const clock = new LyricClock(audio.currentTime * 1000);
    let discontinuity = 0;
    let frame = 0;
    const tick = () => {
      const now = performance.now();
      // The reading and its stamp are taken together.
      el.currentTime = clock.frame(now, Math.trunc(audio.currentTime * 1000), now, discontinuity);
      frame = requestAnimationFrame(tick);
    };
    const hold = () => {
      cancelAnimationFrame(frame);
      const ms = audio.currentTime * 1000;
      clock.hold(ms, performance.now());
      el.currentTime = ms;
    };
    const resume = () => {
      cancelAnimationFrame(frame);
      clock.restart();
      frame = requestAnimationFrame(tick);
    };
    const onSeeking = () => {
      discontinuity++;
      if (audio.paused) hold();
    };
    audio.addEventListener('seeking', onSeeking);
    audio.addEventListener('seeked', onSeeking);
    audio.addEventListener('pause', hold);
    audio.addEventListener('play', resume);
    if (audio.paused) hold();
    else resume();
    return () => {
      cancelAnimationFrame(frame);
      audio.removeEventListener('seeking', onSeeking);
      audio.removeEventListener('seeked', onSeeking);
      audio.removeEventListener('pause', hold);
      audio.removeEventListener('play', resume);
    };
  }, [deck, videoId, ttml]);

  return <div ref={host} className={styles.host} />;
}

function useUngranted(): string[] {
  return useSyncExternalStore(onUngrantedChange, () => [...ungrantedOrigins].join(','), () => '')
    .split(',')
    .filter(Boolean);
}

const NO_STATES: Partial<Record<LyricsSourceId, SourceState>> = {};

/** The provider picker: which provider these lyrics are from, and the others' answers for this track. */
function ProviderPicker({ current }: { current: LyricsSourceId | null }) {
  const videoId = usePlayer((s) => s.nowPlaying?.videoId ?? '');
  const states = useLyricsUi((s) => s.states[videoId]) ?? NO_STATES;
  const picked = useLyricsUi((s) => s.picked[videoId]);
  const pick = useLyricsUi((s) => s.pick);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [settings, setSettings] = useState(false);
  const close = useCallback(() => setAnchor(null), []);
  const label = current ? sourceById(current)?.label : 'Lyrics';

  return (
    <div className={styles.bar}>
      <button className={styles.picker} aria-haspopup="menu" onClick={(e) => setAnchor(e.currentTarget)}>
        <span>{label}</span>
        <ChevronDown size={14} />
      </button>
      <button className={styles.icon} aria-label="Lyric settings" onClick={() => setSettings(true)}>
        <Settings2 size={16} />
      </button>
      {anchor && (
        <Menu anchor={anchor} placement="below-end" onClose={close} label="Lyrics provider">
          <MenuItem checked={!picked} onSelect={() => (close(), pick(videoId, null))}>
            Best available
          </MenuItem>
          <MenuSeparator />
          <MenuHeader>Providers</MenuHeader>
          {enabledSources().map((id) => {
            const state = states[id];
            return (
              <MenuItem
                key={id}
                checked={picked === id}
                trailing={
                  state === 'pending' ? (
                    <Loader2 size={13} className={styles.spin} />
                  ) : state === 'found' ? (
                    <Check size={13} />
                  ) : state === 'missing' ? (
                    <Minus size={13} />
                  ) : undefined
                }
                onSelect={() => (close(), pick(videoId, id))}
              >
                {sourceById(id)?.label}
              </MenuItem>
            );
          })}
          <MenuSeparator />
          <MenuItem onSelect={() => (close(), setSettings(true))}>Lyric Settings…</MenuItem>
        </Menu>
      )}
      {settings && <LyricsSettings onClose={() => setSettings(false)} />}
    </div>
  );
}

function PermissionNotice({ origins }: { origins: string[] }) {
  const names = origins.map((o) => new URL(o).host.replace(/^www\./, ''));
  return (
    <div className={styles.notice}>
      <span>
        {names.slice(0, 2).join(', ')}
        {names.length > 2 ? ` and ${names.length - 2} more` : ''} need the extension’s permission.
      </span>
      <button
        // The notice stays until the extension confirms the grant (checked when this tab comes back).
        onClick={() => void getBridge()?.call('lyrics.requestPermissions', { origins })}
      >
        Allow
      </button>
    </div>
  );
}

/**
 * The track's Spotify Canvas behind the lyrics, dimmed so they stay legible.
 * Muted and looping, it fades in once it can play and pauses with the music.
 */
function CanvasBackdrop({ url }: { url: string }) {
  const video = useRef<HTMLVideoElement>(null);
  const [ready, setReady] = useState(false);
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  useEffect(() => setReady(false), [url]);
  useEffect(() => {
    const el = video.current;
    if (!el || !ready) return;
    if (playing) void el.play().catch(() => undefined);
    else el.pause();
  }, [playing, ready]);
  return (
    <video
      ref={video}
      key={url}
      className={styles.canvas}
      data-ready={ready || undefined}
      src={url}
      muted
      loop
      playsInline
      preload="auto"
      aria-hidden="true"
      onCanPlay={() => setReady(true)}
      onError={() => setReady(false)}
    />
  );
}

/** The Lyrics side panel (report §7). */
export function LyricsPanel() {
  const lyrics = useLyrics();
  const hasTrack = usePlayer((s) => s.nowPlaying !== null);
  const ungranted = useUngranted();
  const canvas = useCanvas();
  const fontReady = useLyricFont();
  const result = lyrics.data;

  return (
    <div className={styles.panel}>
      {canvas && <CanvasBackdrop url={canvas} />}
      <ProviderPicker current={result?.source ?? null} />
      {/* Only worth raising when nothing was found: then the hosts behind the extension might have it. */}
      {ungranted.length > 0 && !result && !lyrics.isFetching && <PermissionNotice origins={ungranted} />}
      {!hasTrack ? (
        <div className={styles.empty}>
          <p className={styles.emptyTitle}>No lyrics available</p>
          <p>Play a song to see its lyrics here.</p>
        </div>
      ) : (lyrics.isPending && lyrics.fetchStatus !== 'idle') || (result && !fontReady) ? (
        <div className={styles.empty}>
          <Loader2 size={20} className={styles.spin} />
        </div>
      ) : result ? (
        <LyricsView ttml={result.ttml} />
      ) : (
        <div className={styles.empty}>
          <p className={styles.emptyTitle}>No lyrics available</p>
          <p>None of the lyric providers has this song. Try another in the list above.</p>
        </div>
      )}
    </div>
  );
}
