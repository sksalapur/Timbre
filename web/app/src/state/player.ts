import type { DetectedExtension } from '@bitchord/shared/bridge';
import { create } from 'zustand';
import { extensionQueryKey } from '@/extension/useExtension';
import { artworkAtPixels } from '@/lib/artwork';
import { queryClient } from '@/lib/queryClient';
import { trackLog } from '@/playback/trackLog';
import type { LoadedTrack, YouTubeEngine } from '@/playback/youtube/engine';

export interface NowPlaying {
  videoId: string;
  title: string;
  artist: string;
  /** Largest available; size it for display with sizedArtwork(). */
  artworkUrl: string | null;
  durationSec: number;
  /** "251 opus 140kbps via VISIONOS", once known; for the debug panel. */
  streamInfo: string | null;
}

export type PlayerStatus = 'idle' | 'loading' | 'playing' | 'paused' | 'buffering' | 'error';

interface PlayerState {
  nowPlaying: NowPlaying | null;
  status: PlayerStatus;
  error: string | null;
  /** Plays a YouTube track. `hint` fills the player chrome before the stream resolves. */
  playVideo: (videoId: string, hint?: Partial<Omit<NowPlaying, 'videoId'>>) => Promise<void>;
  togglePlay: () => void;
  seek: (seconds: number) => void;
}

// ---- The engine, loaded on first play ---------------------------------------------

let audio: HTMLAudioElement | null = null;
let enginePromise: Promise<YouTubeEngine> | null = null;

/** The one media element, if playback has started at least once. */
export function getAudio(): HTMLAudioElement | null {
  return audio;
}

function getEngine(): Promise<YouTubeEngine> {
  enginePromise ??= (async () => {
    // Shaka, YouTube.js and googlevideo are the heavy part of the app; they
    // load on the first play, not with the page (prompt §3).
    const { YouTubeEngine } = await import('@/playback/youtube/engine');
    audio = new Audio();
    audio.preload = 'auto';
    // Media from another origin is silent inside an AudioContext unless it
    // is fetched with CORS (prompt §6, the Web Audio caveat).
    audio.crossOrigin = 'anonymous';
    bindElement(audio);
    // For browser-driven tests: the element isn't in the DOM.
    if (import.meta.env.DEV) Object.assign(window, { __bcAudio: audio });
    const engine = new YouTubeEngine(audio);
    engine.onFatal = (_videoId, message) => usePlayer.setState({ status: 'error', error: message });
    engine.onRecovered = (track) => applyTrack(track);
    return engine;
  })();
  return enginePromise;
}

function extensionReady(): boolean {
  const detected = queryClient.getQueryData<DetectedExtension | null>(extensionQueryKey);
  return !!detected?.info.capabilities.includes('yt');
}

// ---- Store ------------------------------------------------------------------------

export const usePlayer = create<PlayerState>()((set, get) => ({
  nowPlaying: null,
  status: 'idle',
  error: null,

  async playVideo(videoId, hint = {}) {
    if (!extensionReady()) {
      // Shown in the pill, so the tap visibly did something.
      set({
        nowPlaying: {
          videoId,
          title: hint.title || 'Can’t play this here yet',
          artist: hint.artist ?? '',
          artworkUrl: hint.artworkUrl ?? null,
          durationSec: hint.durationSec ?? 0,
          streamInfo: null,
        },
        status: 'error',
        error: 'YouTube playback needs the BitChord extension.',
      });
      return;
    }
    set({
      nowPlaying: {
        videoId,
        title: hint.title ?? '',
        artist: hint.artist ?? '',
        artworkUrl: hint.artworkUrl ?? null,
        durationSec: hint.durationSec ?? 0,
        streamInfo: null,
      },
      status: 'loading',
      error: null,
    });
    updateMediaSession(get().nowPlaying);
    try {
      const engine = await getEngine();
      const track = await engine.load(videoId);
      if (get().nowPlaying?.videoId !== videoId) return;
      applyTrack(track, hint);
    } catch (error) {
      if (error instanceof Error && error.name === 'SupersededError') return;
      if (get().nowPlaying?.videoId !== videoId) return;
      set({ status: 'error', error: error instanceof Error ? error.message : String(error) });
    }
  },

  togglePlay() {
    if (!audio || !get().nowPlaying) return;
    if (audio.paused) void audio.play().catch((e: unknown) => trackLog.w(`play() refused: ${String(e)}`));
    else audio.pause();
  },

  seek(seconds) {
    if (!audio) return;
    audio.currentTime = Math.max(0, Math.min(seconds, audio.duration || seconds));
    updatePositionState();
  },
}));

function applyTrack(track: LoadedTrack, hint: Partial<Omit<NowPlaying, 'videoId'>> = {}) {
  const nowPlaying: NowPlaying = {
    videoId: track.videoId,
    // YouTube Music metadata (from the caller) beats the video's own title.
    title: hint.title || track.title,
    // Auto-generated YouTube channels are named "Artist - Topic".
    artist: hint.artist || track.author.replace(/ - Topic$/, ''),
    artworkUrl: hint.artworkUrl ?? track.thumbnails[0]?.url ?? null,
    durationSec: track.durationSec || hint.durationSec || 0,
    streamInfo: `${track.format} via ${track.client}`,
  };
  usePlayer.setState({ nowPlaying, error: null });
  updateMediaSession(nowPlaying);
}

/** The element's own events are the source of truth for play state. */
function bindElement(element: HTMLAudioElement) {
  const set = (status: PlayerStatus) => {
    if (usePlayer.getState().status === 'error' && status !== 'playing') return;
    usePlayer.setState({ status });
  };
  element.addEventListener('playing', () => set('playing'));
  element.addEventListener('pause', () => set('paused'));
  element.addEventListener('waiting', () => set('buffering'));
  element.addEventListener('ended', () => set('paused'));
  element.addEventListener('durationchange', updatePositionState);
  element.addEventListener('ratechange', updatePositionState);
  element.addEventListener('seeked', updatePositionState);
  element.addEventListener('play', () => {
    if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'playing';
  });
  element.addEventListener('pause', () => {
    if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'paused';
  });
}

// ---- Media Session (prompt §5.2 step 6) ---------------------------------------------

let handlersBound = false;

function updateMediaSession(track: NowPlaying | null) {
  if (!('mediaSession' in navigator)) return;
  const session = navigator.mediaSession;
  if (!track) {
    session.metadata = null;
    return;
  }
  session.metadata = new MediaMetadata({
    title: track.title,
    artist: track.artist,
    artwork: track.artworkUrl
      ? [96, 256, 512].map((px) => ({ src: artworkAtPixels(track.artworkUrl!, px), sizes: `${px}x${px}` }))
      : [],
  });

  if (handlersBound) return;
  handlersBound = true;
  const handle = (action: MediaSessionAction, handler: MediaSessionActionHandler | null) => {
    try {
      session.setActionHandler(action, handler);
    } catch {
      // Not every browser knows every action.
    }
  };
  handle('play', () => void audio?.play());
  handle('pause', () => audio?.pause());
  handle('seekto', (details) => {
    if (details.seekTime !== undefined) usePlayer.getState().seek(details.seekTime);
  });
  handle('seekbackward', (details) => usePlayer.getState().seek((audio?.currentTime ?? 0) - (details.seekOffset ?? 10)));
  handle('seekforward', (details) => usePlayer.getState().seek((audio?.currentTime ?? 0) + (details.seekOffset ?? 10)));
  // Previous/next arrive with the queue in phase 6.
  handle('previoustrack', null);
  handle('nexttrack', null);
}

function updatePositionState() {
  if (!audio || !('mediaSession' in navigator) || !Number.isFinite(audio.duration) || audio.duration <= 0) return;
  try {
    navigator.mediaSession.setPositionState({
      duration: audio.duration,
      playbackRate: audio.playbackRate,
      position: Math.min(audio.currentTime, audio.duration),
    });
  } catch {
    // Position past duration during a seek; the next event corrects it.
  }
}
