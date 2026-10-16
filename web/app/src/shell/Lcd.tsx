import { Maximize2 } from 'lucide-react';
import { LogoMark } from '@/components/icons/LogoMark';
import { sizedArtwork } from '@/lib/artwork';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import styles from './Lcd.module.css';
import { useScrubber } from './useScrubber';

/** The now-playing tile inside the pill (report §1, "LCD"). */
export function Lcd() {
  const nowPlaying = usePlayer((s) => s.nowPlaying);
  const status = usePlayer((s) => s.status);
  const error = usePlayer((s) => s.error);
  const openFullScreen = useUi((s) => s.setFullScreen);

  if (!nowPlaying) {
    return (
      <div className={styles.empty}>
        <LogoMark size={26} />
      </div>
    );
  }

  const subtitle = status === 'error' ? error : nowPlaying.artist || (status === 'loading' ? 'Loading…' : '');
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
        <span className={styles.title}>{nowPlaying.title || (status === 'loading' ? 'Loading…' : '')}</span>
        <span className={status === 'error' ? `${styles.subtitle} ${styles.error}` : styles.subtitle}>{subtitle}</span>
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
