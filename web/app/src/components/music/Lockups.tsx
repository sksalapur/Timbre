import { PrefetchLink } from '@/components/PrefetchLink';
import { PlayIcon } from '@/components/icons/Transport';
import type { MusicItem } from '@/data/music/types';
import { Artwork } from './Artwork';
import { ExplicitBadge } from './ExplicitBadge';
import { artistHref, hrefFor } from './links';
import styles from './Lockups.module.css';
import { playTrack } from './play';

const isPlayable = (item: MusicItem) => item.kind === 'song' || item.kind === 'video';

/** Artist names as links where YouTube links them, else the given subtitle. */
function Byline({ item }: { item: MusicItem }) {
  const linked = item.artists.some((a) => a.id);
  if (!linked || item.kind === 'album' || item.kind === 'playlist') return <>{item.subtitle}</>;
  return (
    <>
      {item.artists.map((artist, i) => {
        const href = artistHref(artist.id);
        return (
          <span key={`${artist.name}-${i}`}>
            {i > 0 && ', '}
            {href ? (
              <PrefetchLink to={href} className={styles.link}>
                {artist.name}
              </PrefetchLink>
            ) : (
              artist.name
            )}
          </span>
        );
      })}
    </>
  );
}

function PlayPlatter({ item }: { item: MusicItem }) {
  return (
    <button
      className={styles.platter}
      aria-label={`Play ${item.title}`}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        playTrack(item);
      }}
    >
      <PlayIcon size={16} />
    </button>
  );
}

/** The artwork with its hover scrim, linking to the item or playing it. */
function LockupArtwork({ item, width, aspect }: { item: MusicItem; width: number; aspect: number }) {
  const href = hrefFor(item);
  return (
    <div className={styles.artworkWrap}>
      <Artwork src={item.thumbnail} displayWidth={width} aspect={aspect} radius="var(--radius-artwork-medium)" />
      {href ? (
        <PrefetchLink to={href} className={styles.overlayLink} aria-label={item.title} />
      ) : (
        <button className={styles.overlayLink} aria-label={`Play ${item.title}`} onClick={() => playTrack(item)} />
      )}
      <div className={styles.controls}>{isPlayable(item) && <PlayPlatter item={item} />}</div>
    </div>
  );
}

/** Square lockup (report §2): artwork, hover scrim with play platter, title and subtitle. */
export function SquareLockup({ item, width = 220 }: { item: MusicItem; width?: number }) {
  const href = hrefFor(item);
  return (
    <div className={styles.lockup}>
      <LockupArtwork item={item} width={width} aspect={1} />
      <div className={styles.caption}>
        <div className={styles.title}>
          {href ? (
            <PrefetchLink to={href} className={styles.titleLink}>
              {item.title}
            </PrefetchLink>
          ) : (
            <span>{item.title}</span>
          )}
          {item.explicit && <ExplicitBadge />}
        </div>
        <div className={styles.subtitle}>
          <Byline item={item} />
        </div>
      </div>
    </div>
  );
}

/** 16:9 video lockup (report §2, type C). */
export function VideoLockup({ item, width = 300 }: { item: MusicItem; width?: number }) {
  return (
    <div className={styles.lockup}>
      <LockupArtwork item={item} width={width} aspect={16 / 9} />
      <div className={styles.caption}>
        <div className={styles.title}>
          <span>{item.title}</span>
          {item.explicit && <ExplicitBadge />}
        </div>
        <div className={styles.subtitle}>{item.subtitle}</div>
      </div>
    </div>
  );
}

/** Circular artist lockup (report §2, type H). */
export function CircleLockup({ item, width = 160 }: { item: MusicItem; width?: number }) {
  return (
    <PrefetchLink to={hrefFor(item) ?? '#'} className={styles.circle}>
      <Artwork src={item.thumbnail} displayWidth={width} radius="50%" />
      <span className={styles.circleName}>{item.title}</span>
    </PrefetchLink>
  );
}

/** A song row inside a shelf (report §2, track lockup): 40px art, title, byline. */
export function TrackLockup({ item, onPlay }: { item: MusicItem; onPlay?: () => void }) {
  const play = onPlay ?? (() => playTrack(item));
  return (
    <div className={styles.track}>
      <button className={styles.trackArt} aria-label={`Play ${item.title}`} onClick={play}>
        <Artwork src={item.thumbnail} displayWidth={40} radius="var(--radius-artwork-small)" />
        <span className={styles.trackPlay} aria-hidden="true">
          <PlayIcon size={16} />
        </span>
      </button>
      <div className={styles.trackText}>
        <div className={styles.trackTitle}>
          <button className={styles.trackTitleButton} onClick={play}>
            {item.title}
          </button>
          {item.explicit && <ExplicitBadge />}
        </div>
        <div className={styles.trackSubtitle}>
          <Byline item={item} />
        </div>
      </div>
    </div>
  );
}
