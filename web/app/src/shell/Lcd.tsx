import { Maximize2 } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { LogoMark } from '@/components/icons/LogoMark';
import { albumHref, artistHref } from '@/components/music/links';
import { PrefetchLink } from '@/components/PrefetchLink';
import { artistCredits, creditSegments } from '@/lib/artistCredits';
import { sizedArtwork } from '@/lib/artwork';
import type { Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import styles from './Lcd.module.css';
import { albumPageFor, artistPageFor } from './nowPlayingLinks';
import { useScrubber } from './useScrubber';

/**
 * A name in the LCD that opens a page: a plain link when the page is known,
 * else a button that works out where it goes (nowPlayingLinks.ts) and then goes.
 */
interface PageLinkProps {
  href: string | null;
  resolve: () => Promise<string>;
  className: string;
  children: ReactNode;
}

export function PageLink({ href, resolve, className, children }: PageLinkProps) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  if (href) {
    return (
      <PrefetchLink to={href} className={className}>
        {children}
      </PrefetchLink>
    );
  }
  return (
    <button
      className={className}
      aria-busy={busy || undefined}
      onClick={() => {
        if (busy) return;
        setBusy(true);
        void resolve()
          .then((to) => navigate(to))
          .finally(() => setBusy(false));
      }}
    >
      {children}
    </button>
  );
}

/**
 * The artists, each its own link, as the phone draws its credit line: the
 * byline as written, with every credited name in it opening that artist's page.
 */
export function Credits({
  videoId,
  line,
  artists,
  className = styles.link!,
}: {
  videoId: string;
  line: string;
  artists: Track['artists'];
  className?: string;
}) {
  const segments = creditSegments(line, artistCredits(line, artists));
  return segments.map((segment, i) =>
    segment.credit ? (
      <PageLink
        key={i}
        className={className}
        href={artistHref(segment.credit.id)}
        resolve={() => artistPageFor(videoId, segment.credit!)}
      >
        {segment.text}
      </PageLink>
    ) : (
      segment.text
    ),
  );
}

/** The now-playing tile inside the pill (report §1, "LCD"). */
export function Lcd() {
  const nowPlaying = usePlayer((s) => s.nowPlaying);
  const status = usePlayer((s) => s.status);
  const error = usePlayer((s) => s.error);
  const openFullScreen = useUi((s) => s.setFullScreen);
  // The queue's row for what's playing: it carries the album and each artist's channel.
  const track = usePlayer((s) => {
    const entry = s.queue[s.index]?.track;
    return entry && entry.videoId === s.nowPlaying?.videoId ? entry : null;
  });

  if (!nowPlaying) {
    return (
      <div className={styles.empty}>
        <LogoMark size={26} />
      </div>
    );
  }

  const { videoId, title, artist } = nowPlaying;
  const album = track?.album ?? null;
  return (
    <div className={styles.lcd}>
      {/* As Apple's: the artwork opens the full-screen player, with an expand glyph on hover. */}
      <button className={styles.artwork} aria-label="Open full screen player" onClick={() => openFullScreen(true)}>
        {nowPlaying.artworkUrl && (
          <img
            key={nowPlaying.artworkUrl}
            src={sizedArtwork(nowPlaying.artworkUrl, 34)}
            alt=""
            referrerPolicy="no-referrer"
            decoding="async"
            onLoad={(e) => e.currentTarget.setAttribute('data-loaded', '')}
          />
        )}
        <span className={styles.expand} aria-hidden="true">
          <Maximize2 size={14} strokeWidth={2.25} />
        </span>
      </button>
      <div className={styles.meta}>
        {title ? (
          // The song's name opens its album.
          <PageLink
            className={`${styles.title} ${styles.link}`}
            href={albumHref(album?.id ?? null)}
            resolve={() => albumPageFor(videoId, album, [title, artist].filter(Boolean).join(' '))}
          >
            {title}
          </PageLink>
        ) : (
          <span className={styles.title}>{status === 'loading' ? 'Loading…' : ''}</span>
        )}
        {status === 'error' ? (
          <span className={`${styles.subtitle} ${styles.error}`}>{error}</span>
        ) : (
          <span className={styles.subtitle}>
            {artist ? <Credits videoId={videoId} line={artist} artists={track?.artists ?? []} /> : status === 'loading' ? 'Loading…' : ''}
          </span>
        )}
      </div>
      <Progress />
    </div>
  );
}

/**
 * Elapsed time as a 2px line along the LCD's bottom (report §1). Hovering
 * shows the times; pressing or dragging on the line seeks.
 */
function Progress() {
  const { bar, elapsed, remaining, dragging, handlers } = useScrubber();
  return (
    <div className={styles.scrubber} data-dragging={dragging || undefined} {...handlers}>
      <span ref={elapsed} className={`${styles.time} ${styles.elapsed}`} />
      <span ref={remaining} className={`${styles.time} ${styles.remaining}`} />
      <div className={styles.progress} aria-hidden="true">
        <div ref={bar} className={styles.track} />
      </div>
    </div>
  );
}
