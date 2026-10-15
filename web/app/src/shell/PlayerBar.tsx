import { ListMusic, MessageSquareQuote, Repeat, Shuffle, Volume2 } from 'lucide-react';
import { BackwardIcon, ForwardIcon, PauseIcon, PlayIcon } from '@/components/icons/Transport';
import { FavoriteButton } from '@/components/music/FavoriteButton';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { Lcd } from './Lcd';
import styles from './PlayerBar.module.css';

/**
 * The floating glass player pill (report §1). Previous/next, shuffle and
 * repeat arrive with the queue in phase 6; the scrubber and volume slider too.
 */
export function PlayerBar() {
  const sidePanel = useUi((s) => s.sidePanel);
  const toggleSidePanel = useUi((s) => s.toggleSidePanel);
  const hasLyrics = false;
  const hasTrack = usePlayer((s) => s.nowPlaying !== null);
  const videoId = usePlayer((s) => s.nowPlaying?.videoId ?? null);
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  const togglePlay = usePlayer((s) => s.togglePlay);

  return (
    <div className={styles.bar} data-panel-open={sidePanel !== null}>
      <div className={styles.pill} role="region" aria-label="Player">
        <div className={styles.controls}>
          <button className={styles.small} disabled aria-label="Shuffle">
            <Shuffle size={16} strokeWidth={2.2} />
          </button>
          <div className={styles.main}>
            <button className={styles.skip} disabled aria-label="Previous">
              <BackwardIcon size={22} />
            </button>
            <button
              className={styles.play}
              disabled={!hasTrack}
              aria-label={playing ? 'Pause' : 'Play'}
              onClick={togglePlay}
            >
              {playing ? <PauseIcon size={24} /> : <PlayIcon size={24} />}
            </button>
            <button className={styles.skip} disabled aria-label="Next">
              <ForwardIcon size={22} />
            </button>
          </div>
          <button className={styles.small} disabled aria-label="Repeat">
            <Repeat size={16} strokeWidth={2.2} />
          </button>
        </div>

        <div className={styles.lcd}>
          <Lcd />
        </div>

        <div className={styles.actions}>
          <FavoriteButton videoId={videoId} className={styles.action} />
          {hasLyrics && (
            <button
              className={styles.action}
              aria-label="Lyrics"
              aria-pressed={sidePanel === 'lyrics'}
              onClick={() => toggleSidePanel('lyrics')}
            >
              <MessageSquareQuote size={18} strokeWidth={2} />
            </button>
          )}
          <button
            className={styles.action}
            aria-label="Up Next"
            aria-pressed={sidePanel === 'up-next'}
            onClick={() => toggleSidePanel('up-next')}
          >
            <ListMusic size={18} strokeWidth={2} />
          </button>
          <button className={styles.action} aria-label="Volume">
            <Volume2 size={18} strokeWidth={2} />
          </button>
        </div>
      </div>
    </div>
  );
}
