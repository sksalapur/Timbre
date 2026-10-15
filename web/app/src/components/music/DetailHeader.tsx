import { Shuffle } from 'lucide-react';
import { useState } from 'react';
import { PrefetchLink } from '@/components/PrefetchLink';
import { PlayIcon } from '@/components/icons/Transport';
import type { HeaderInfo } from '@/data/music/types';
import { Artwork } from './Artwork';
import styles from './DetailHeader.module.css';
import { ExplicitBadge } from './ExplicitBadge';
import { artistHref } from './links';

interface DetailHeaderProps {
  info: HeaderInfo;
  onPlay: () => void;
  onShuffle: () => void;
  canPlay: boolean;
}

/**
 * Album and playlist header (report §3, §14): artwork with its glow, title,
 * artist, metadata, a three-line description, then Shuffle · Play.
 */
export function DetailHeader({ info, onPlay, onShuffle, canPlay }: DetailHeaderProps) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div className={styles.header}>
      <div className={styles.artwork}>
        {info.thumbnail && (
          <div className={styles.radiosity} aria-hidden="true">
            <Artwork src={info.thumbnail} displayWidth={80} radius="var(--radius-artwork-large)" />
          </div>
        )}
        <Artwork src={info.thumbnail} displayWidth={270} radius="var(--radius-artwork-large)" eager />
      </div>
      <div className={styles.headings}>
        <h1 className={styles.title}>
          {info.title}
          {info.explicit && <ExplicitBadge />}
        </h1>
        {info.artists.length > 0 && (
          <div className={styles.artists}>
            {info.artists.map((artist, i) => {
              const href = artistHref(artist.id);
              return (
                <span key={`${artist.name}-${i}`}>
                  {i > 0 && ', '}
                  {href ? (
                    <PrefetchLink to={href} className={styles.artistLink}>
                      {artist.name}
                    </PrefetchLink>
                  ) : (
                    artist.name
                  )}
                </span>
              );
            })}
          </div>
        )}
        <div className={styles.meta}>{[info.subtitle, info.meta].filter(Boolean).join(' · ')}</div>
      </div>
      {info.description && (
        <div className={styles.description}>
          <p className={expanded ? styles.descriptionText : `${styles.descriptionText} ${styles.clamped}`}>
            {info.description}
          </p>
          {!expanded && info.description.length > 180 && (
            <button className={styles.more} onClick={() => setExpanded(true)}>
              MORE
            </button>
          )}
        </div>
      )}
      <div className={styles.actions}>
        <button className={styles.circle} aria-label="Shuffle" onClick={onShuffle} disabled={!canPlay}>
          <Shuffle size={18} strokeWidth={2.2} />
        </button>
        <button className={styles.pill} onClick={onPlay} disabled={!canPlay}>
          <PlayIcon size={18} />
          <span>Play</span>
        </button>
      </div>
    </div>
  );
}
