import { Maximize, Minimize, X } from 'lucide-react';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { useLocation } from 'react-router';
import {
  BackwardIcon,
  ForwardIcon,
  LyricsActiveIcon,
  LyricsIcon,
  PauseIcon,
  PlayIcon,
  RepeatIcon,
  RepeatOneIcon,
  ShuffleIcon,
  UpNextIcon,
} from '@/components/icons/Transport';
import { FavoriteButton } from '@/components/music/FavoriteButton';
import { albumHref } from '@/components/music/links';
import { MotionArtwork } from '@/components/music/MotionArtwork';
import { useTrackMotion } from '@/data/apple/motionArt';
import { sizedArtwork } from '@/lib/artwork';
import { useLyrics } from '@/lyrics/useLyrics';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import styles from './FullScreenPlayer.module.css';
import { Credits, PageLink } from './Lcd';
import { LyricsPanel } from './LyricsPanel';
import { MeshBackdrop } from './MeshBackdrop';
import { albumPageFor } from './nowPlayingLinks';
import { UpNext } from './UpNext';
import { useScrubber } from './useScrubber';

function useBrowserFullScreen(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      document.addEventListener('fullscreenchange', onChange);
      return () => document.removeEventListener('fullscreenchange', onChange);
    },
    () => document.fullscreenElement !== null,
  );
}

/** The seek line under the track's names: thicker on hover, with the times beneath its ends. */
function Scrubber() {
  const { bar, elapsed, remaining, dragging, handlers } = useScrubber();
  return (
    <div className={styles.scrubber} data-dragging={dragging || undefined}>
      <div className={styles.hit} {...handlers}>
        <div className={styles.line} aria-hidden="true">
          <div ref={bar} className={styles.fill} />
        </div>
      </div>
      <div className={styles.times}>
        <span ref={elapsed} />
        <span ref={remaining} />
      </div>
    </div>
  );
}

/** Shuffle, previous, play/pause, next, repeat. No volume: the pill keeps that. */
function Transport() {
  const hasTrack = usePlayer((s) => s.nowPlaying !== null);
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  const hasNext = usePlayer((s) => s.index + 1 < s.queue.length || s.repeat !== 'off' || (s.autoplay && s.index >= 0));
  const shuffle = usePlayer((s) => s.shuffle);
  const repeat = usePlayer((s) => s.repeat);
  const { togglePlay, next, previous, toggleShuffle, cycleRepeat } = usePlayer.getState();

  return (
    <div className={styles.transport}>
      <button className={styles.toggle} aria-label="Shuffle" aria-pressed={shuffle} onClick={toggleShuffle}>
        <ShuffleIcon size={30} />
      </button>
      <button className={styles.skip} disabled={!hasTrack} aria-label="Previous" onClick={previous}>
        <BackwardIcon size={40} />
      </button>
      <button className={styles.play} disabled={!hasTrack} aria-label={playing ? 'Pause' : 'Play'} onClick={togglePlay}>
        {playing ? <PauseIcon size={48} /> : <PlayIcon size={48} />}
      </button>
      <button className={styles.skip} disabled={!hasTrack || !hasNext} aria-label="Next" onClick={next}>
        <ForwardIcon size={40} />
      </button>
      <button
        className={styles.toggle}
        aria-label={repeat === 'one' ? 'Repeat one' : repeat === 'all' ? 'Repeat all' : 'Repeat'}
        aria-pressed={repeat !== 'off'}
        onClick={cycleRepeat}
      >
        {repeat === 'one' ? <RepeatOneIcon size={30} /> : <RepeatIcon size={30} />}
      </button>
    </div>
  );
}

/**
 * The full-screen player (report §7): the artwork on the left with the
 * track's names, the seek line and the transport under it; the lyrics, the
 * same am-lyrics panel as the side panel's, on the right; all over a mesh
 * gradient of the artwork's colours. Without lyrics, the artwork column sits
 * in the middle on its own, as Apple's does.
 */
export function FullScreenPlayer() {
  const nowPlaying = usePlayer((s) => s.nowPlaying);
  // The queue's row for what's playing: it carries the album and each artist's channel.
  const track = usePlayer((s) => {
    const entry = s.queue[s.index]?.track;
    return entry && entry.videoId === s.nowPlaying?.videoId ? entry : null;
  });
  // Apple's animated sleeve over the cover, as the phone's now-playing shows it.
  const motion = useTrackMotion(nowPlaying?.title, nowPlaying?.artist, track?.album?.name);
  const { pathname } = useLocation();
  const openedAt = useRef(pathname);
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  const setFullScreen = useUi((s) => s.setFullScreen);
  const lyrics = useLyrics();
  // The right-hand column: the lyrics, Up Next, or nothing. Lyrics toggle bottom left, Up Next bottom right.
  const [panel, setPanel] = useState<'lyrics' | 'queue' | null>('lyrics');
  const browserFullScreen = useBrowserFullScreen();
  const close = useRef<HTMLButtonElement>(null);
  // Whether this player put the page in the browser's full screen, so closing it leaves it again.
  const enteredBrowserFullScreen = useRef(false);

  // The lyrics column is there while lyrics are on and there are lyrics, or might be.
  const hasLyrics = !!lyrics.data || (lyrics.isPending && lyrics.fetchStatus !== 'idle');
  const showLyrics = panel === 'lyrics' && hasLyrics;
  const showQueue = panel === 'queue';
  const togglePanel = (p: 'lyrics' | 'queue') => setPanel((on) => (on === p ? null : p));

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    close.current?.focus({ preventScroll: true });
    return () => {
      if (enteredBrowserFullScreen.current && document.fullscreenElement) void document.exitFullscreen();
      opener?.focus({ preventScroll: true });
    };
  }, []);

  // A title, artist or album link went somewhere: get out of the way so the page shows.
  useEffect(() => {
    if (pathname !== openedAt.current) setFullScreen(false);
  }, [pathname, setFullScreen]);

  // Nothing left to show once the queue ends.
  useEffect(() => {
    if (!nowPlaying) setFullScreen(false);
  }, [nowPlaying, setFullScreen]);

  const toggleBrowserFullScreen = () => {
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else {
      // The whole page, not this overlay: menus and dialogs portal to <body> and must stay visible.
      void document.documentElement.requestFullscreen().then(
        () => (enteredBrowserFullScreen.current = true),
        () => undefined,
      );
    }
  };

  if (!nowPlaying) return null;
  const { videoId, title, artist } = nowPlaying;
  const album = track?.album ?? null;

  return (
    <div
      className={styles.overlay}
      role="dialog"
      aria-modal="true"
      aria-label="Now Playing"
      data-full-screen
      data-paused={!playing || undefined}
    >
      <MeshBackdrop artworkUrl={nowPlaying.artworkUrl} playing={playing} />

      <button ref={close} className={`${styles.chrome} ${styles.close}`} aria-label="Close" onClick={() => setFullScreen(false)}>
        <X size={20} />
      </button>
      {document.fullscreenEnabled && (
        <button
          className={`${styles.chrome} ${styles.expand}`}
          aria-label={browserFullScreen ? 'Exit full screen' : 'Enter full screen'}
          onClick={toggleBrowserFullScreen}
        >
          {browserFullScreen ? <Minimize size={18} /> : <Maximize size={18} />}
        </button>
      )}

      <div className={styles.layout} data-lyrics={showLyrics || showQueue || undefined}>
        <section className={styles.player} aria-label="Player">
          <div className={styles.artwork}>
            {nowPlaying.artworkUrl && (
              <>
                <img
                  key={`glow-${nowPlaying.artworkUrl}`}
                  className={styles.glow}
                  src={sizedArtwork(nowPlaying.artworkUrl, 60)}
                  alt=""
                  referrerPolicy="no-referrer"
                  decoding="async"
                  onLoad={(e) => e.currentTarget.setAttribute('data-loaded', '')}
                />
                <img
                  key={nowPlaying.artworkUrl}
                  className={styles.cover}
                  src={sizedArtwork(nowPlaying.artworkUrl, 600)}
                  alt=""
                  referrerPolicy="no-referrer"
                  decoding="async"
                  onLoad={(e) => e.currentTarget.setAttribute('data-loaded', '')}
                />
              </>
            )}
            {motion && <MotionArtwork key={motion.url} motion={motion} className={styles.motion} playing={playing} />}
          </div>

          <div className={styles.info}>
            {/* As the mini player's: the title opens the album, each credited name its artist. */}
            <div className={styles.meta}>
              <PageLink
                className={`${styles.title} ${styles.link}`}
                href={albumHref(album?.id ?? null)}
                resolve={() => albumPageFor(videoId, album, [title, artist].filter(Boolean).join(' '))}
              >
                {title}
              </PageLink>
              <span className={styles.subtitle}>
                {artist && <Credits videoId={videoId} line={artist} artists={track?.artists ?? []} className={styles.link} />}
                {artist && album?.name && ' — '}
                {album?.name && (
                  <PageLink
                    className={styles.link!}
                    href={albumHref(album.id ?? null)}
                    resolve={() => albumPageFor(videoId, album, [title, artist].filter(Boolean).join(' '))}
                  >
                    {album.name}
                  </PageLink>
                )}
              </span>
            </div>
            <FavoriteButton videoId={nowPlaying.videoId} className={styles.favorite} size={18} />
          </div>

          <Scrubber />
          <Transport />
        </section>

        {showLyrics && (
          <section className={styles.lyrics} aria-label="Lyrics">
            <LyricsPanel variant="full" />
          </section>
        )}
        {showQueue && (
          <section className={styles.queue} aria-label="Up Next">
            <UpNext />
          </section>
        )}
      </div>

      {hasLyrics && (
        <button
          className={`${styles.chrome} ${styles.lyricsToggle}`}
          aria-label="Lyrics"
          aria-pressed={showLyrics}
          onClick={() => togglePanel('lyrics')}
        >
          {showLyrics ? <LyricsActiveIcon size={18} /> : <LyricsIcon size={18} />}
        </button>
      )}
      <button
        className={`${styles.chrome} ${styles.queueToggle}`}
        aria-label="Up Next"
        aria-pressed={showQueue}
        onClick={() => togglePanel('queue')}
      >
        <UpNextIcon size={18} />
      </button>
    </div>
  );
}
