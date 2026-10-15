import { useEffect, useRef, useState } from 'react';
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

function clock(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00';
  const s = Math.floor(seconds);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/**
 * Elapsed time as a 2px line along the LCD's bottom (report §1). Drawn from
 * the media clock every frame while playing, by writing a CSS variable: no
 * React state, so nothing re-renders. Hovering shows the times; pressing or
 * dragging on the line seeks.
 */
function Progress() {
  const bar = useRef<HTMLDivElement>(null);
  const elapsed = useRef<HTMLSpanElement>(null);
  const remaining = useRef<HTMLSpanElement>(null);
  const status = usePlayer((s) => s.status);
  const deck = usePlayer((s) => s.deck);
  const seek = usePlayer((s) => s.seek);
  const [dragging, setDragging] = useState<number | null>(null);

  useEffect(() => {
    const audio = getAudio();
    const element = bar.current;
    if (!audio || !element) return;
    let frame = 0;
    const draw = () => {
      const duration = audio.duration;
      const time = dragging !== null && Number.isFinite(duration) ? dragging * duration : audio.currentTime;
      const fraction = duration > 0 ? time / duration : 0;
      element.style.setProperty('--progress', String(Math.min(1, Math.max(0, fraction))));
      if (elapsed.current) elapsed.current.textContent = clock(time);
      if (remaining.current) remaining.current.textContent = `-${clock((duration || 0) - time)}`;
      if (!audio.paused || dragging !== null) frame = requestAnimationFrame(draw);
    };
    draw();
    const onSeeked = () => draw();
    audio.addEventListener('seeked', onSeeked);
    return () => {
      cancelAnimationFrame(frame);
      audio.removeEventListener('seeked', onSeeked);
    };
  }, [status, deck, dragging]);

  const fractionAt = (event: React.PointerEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    return Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
  };

  return (
    <div
      className={styles.scrubber}
      data-dragging={dragging !== null || undefined}
      onPointerDown={(e) => {
        if (!getAudio()) return;
        e.currentTarget.setPointerCapture(e.pointerId);
        setDragging(fractionAt(e));
      }}
      onPointerMove={(e) => {
        if (dragging !== null) setDragging(fractionAt(e));
      }}
      onPointerUp={(e) => {
        if (dragging === null) return;
        const audio = getAudio();
        if (audio && Number.isFinite(audio.duration)) seek(fractionAt(e) * audio.duration);
        setDragging(null);
      }}
      onPointerCancel={() => setDragging(null)}
    >
      <span ref={elapsed} className={`${styles.time} ${styles.elapsed}`} />
      <span ref={remaining} className={`${styles.time} ${styles.remaining}`} />
      <div className={styles.progress} aria-hidden="true">
        <div ref={bar} className={styles.track} />
      </div>
    </div>
  );
}
