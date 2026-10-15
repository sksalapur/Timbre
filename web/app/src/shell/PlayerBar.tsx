import { ListMusic, MessageSquareQuote, Repeat, Repeat1, Shuffle, Volume, Volume1, Volume2, VolumeX } from 'lucide-react';
import { useState } from 'react';
import { BackwardIcon, ForwardIcon, PauseIcon, PlayIcon } from '@/components/icons/Transport';
import { FavoriteButton } from '@/components/music/FavoriteButton';
import { usePlayer } from '@/state/player';
import { useUi } from '@/state/ui';
import { Lcd } from './Lcd';
import styles from './PlayerBar.module.css';

/** Speaker glyph for the level, as the system volume indicator draws it. */
function VolumeGlyph({ volume, muted }: { volume: number; muted: boolean }) {
  if (muted || volume === 0) return <VolumeX size={18} strokeWidth={2} />;
  if (volume < 0.34) return <Volume size={18} strokeWidth={2} />;
  if (volume < 0.67) return <Volume1 size={18} strokeWidth={2} />;
  return <Volume2 size={18} strokeWidth={2} />;
}

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
        <VolumeGlyph volume={volume} muted={muted} />
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
  const hasLyrics = false;
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
            <Shuffle size={16} strokeWidth={2.2} />
          </button>
          <div className={styles.main}>
            <button className={styles.skip} disabled={!hasTrack} aria-label="Previous" onClick={previous}>
              <BackwardIcon size={22} />
            </button>
            <button className={styles.play} disabled={!hasTrack} aria-label={playing ? 'Pause' : 'Play'} onClick={togglePlay}>
              {playing ? <PauseIcon size={24} /> : <PlayIcon size={24} />}
            </button>
            <button className={styles.skip} disabled={!hasTrack || !hasNext} aria-label="Next" onClick={next}>
              <ForwardIcon size={22} />
            </button>
          </div>
          <button
            className={styles.small}
            aria-label={repeat === 'one' ? 'Repeat one' : repeat === 'all' ? 'Repeat all' : 'Repeat'}
            aria-pressed={repeat !== 'off'}
            onClick={cycleRepeat}
          >
            {repeat === 'one' ? <Repeat1 size={16} strokeWidth={2.2} /> : <Repeat size={16} strokeWidth={2.2} />}
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
          <VolumeControl />
        </div>
      </div>
    </div>
  );
}
