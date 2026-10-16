import { useState } from 'react';
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
  VolumeIcon,
} from '@/components/icons/Transport';
import { FavoriteButton } from '@/components/music/FavoriteButton';
import { useLyrics } from '@/lyrics/useLyrics';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { Lcd } from './Lcd';
import styles from './PlayerBar.module.css';

/** Apple's volume control: the speaker mutes; hovering it shows the slider. */
function VolumeControl() {
  const volume = usePlayer((s) => s.volume);
  const muted = usePlayer((s) => s.muted);
  const setVolume = usePlayer((s) => s.setVolume);
  const toggleMute = usePlayer((s) => s.toggleMute);
  const [open, setOpen] = useState(false);
  const level = muted ? 0 : volume;
  return (
    <div className={styles.volume} onPointerEnter={() => setOpen(true)} onPointerLeave={() => setOpen(false)}>
      <button className={styles.action} aria-label={muted ? 'Unmute' : 'Mute'} aria-pressed={muted} onClick={toggleMute}>
        <VolumeIcon level={level} size={20} />
      </button>
      {open && (
        <div className={styles.volumePopover}>
          <input
            className={styles.slider}
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={level}
            aria-label="Volume"
            style={{ '--fill': `${level * 100}%` } as React.CSSProperties}
            onChange={(e) => setVolume(Number(e.currentTarget.value))}
          />
        </div>
      )}
    </div>
  );
}

/** The floating glass player pill (report §1). */
export function PlayerBar() {
  const sidePanel = useUi((s) => s.sidePanel);
  const toggleSidePanel = useUi((s) => s.toggleSidePanel);
  const lyrics = useLyrics();
  const hasLyrics = !!lyrics.data || sidePanel === 'lyrics';
  const hasTrack = usePlayer((s) => s.nowPlaying !== null);
  const videoId = usePlayer((s) => s.nowPlaying?.videoId ?? null);
  const playing = usePlayer((s) => s.status === 'playing' || s.status === 'buffering');
  const hasNext = usePlayer((s) => s.index + 1 < s.queue.length || s.repeat !== 'off' || (s.autoplay && s.index >= 0));
  const shuffle = usePlayer((s) => s.shuffle);
  const repeat = usePlayer((s) => s.repeat);
  const { togglePlay, next, previous, toggleShuffle, cycleRepeat } = usePlayer.getState();

  return (
    <div className={styles.bar} data-panel-open={sidePanel !== null}>
      <div className={styles.pill} role="region" aria-label="Player">
        <div className={styles.controls}>
          <button className={styles.small} aria-label="Shuffle" aria-pressed={shuffle} onClick={toggleShuffle}>
            <ShuffleIcon size={28} />
          </button>
          <div className={styles.main}>
            <button className={styles.skip} disabled={!hasTrack} aria-label="Previous" onClick={previous}>
              <BackwardIcon size={26} />
            </button>
            <button className={styles.play} disabled={!hasTrack} aria-label={playing ? 'Pause' : 'Play'} onClick={togglePlay}>
              {playing ? <PauseIcon size={28} /> : <PlayIcon size={28} />}
            </button>
            <button className={styles.skip} disabled={!hasTrack || !hasNext} aria-label="Next" onClick={next}>
              <ForwardIcon size={26} />
            </button>
          </div>
          <button
            className={styles.small}
            aria-label={repeat === 'one' ? 'Repeat one' : repeat === 'all' ? 'Repeat all' : 'Repeat'}
            aria-pressed={repeat !== 'off'}
            onClick={cycleRepeat}
          >
            {repeat === 'one' ? <RepeatOneIcon size={28} /> : <RepeatIcon size={28} />}
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
              {sidePanel === 'lyrics' ? <LyricsActiveIcon size={18} /> : <LyricsIcon size={18} />}
            </button>
          )}
          <button
            className={styles.action}
            aria-label="Up Next"
            aria-pressed={sidePanel === 'up-next'}
            onClick={() => toggleSidePanel('up-next')}
          >
            <UpNextIcon size={18} />
          </button>
          <VolumeControl />
        </div>
      </div>
    </div>
  );
}
