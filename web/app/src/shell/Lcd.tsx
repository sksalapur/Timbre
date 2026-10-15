import { useEffect, useRef } from 'react';
import { LogoMark } from '@/components/icons/LogoMark';
import { sizedArtwork } from '@/lib/artwork';
import { getAudio, usePlayer } from '@/state/player';
import styles from './Lcd.module.css';

/** The now-playing tile inside the pill (report §1, "LCD"). */
export function Lcd() {
  const nowPlaying = usePlayer((s) => s.nowPlaying);
  const status = usePlayer((s) => s.status);
  const error = usePlayer((s) => s.error);

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
      <div className={styles.artwork}>
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
      </div>
      <div className={styles.meta}>
        <span className={styles.title}>{nowPlaying.title || (status === 'loading' ? 'Loading…' : '')}</span>
        <span className={status === 'error' ? `${styles.subtitle} ${styles.error}` : styles.subtitle}>{subtitle}</span>
      </div>
      <Progress />
    </div>
  );
}

/**
 * Elapsed time as a 2px line. Drawn from the media clock every frame while
 * playing, by writing a CSS variable: no React state, so nothing re-renders.
 */
function Progress() {
  const bar = useRef<HTMLDivElement>(null);
  const status = usePlayer((s) => s.status);

  useEffect(() => {
    const audio = getAudio();
    const element = bar.current;
    if (!audio || !element) return;
    let frame = 0;
    const draw = () => {
      const fraction = audio.duration > 0 ? audio.currentTime / audio.duration : 0;
      element.style.setProperty('--progress', String(Math.min(1, Math.max(0, fraction))));
      if (!audio.paused) frame = requestAnimationFrame(draw);
    };
    draw();
    return () => cancelAnimationFrame(frame);
  }, [status]);

  return (
    <div className={styles.progress} aria-hidden="true">
      <div ref={bar} className={styles.track} />
    </div>
  );
}
