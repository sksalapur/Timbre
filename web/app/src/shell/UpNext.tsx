import { party, useParty } from '@/party/listenTogether';
import { Infinity as InfinityIcon, MoreHorizontal, X } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import { Artwork } from '@/components/music/Artwork';
import { formatDuration } from '@/components/music/links';
import { autoplaySectionStart, upcomingUserQueueIndices, type QueueEntry } from '@/playback/queue/timeline';
import { CROSSFADE_CHOICES, usePlayer } from '@/state/player';
import styles from './UpNext.module.css';

function Row({
  entry,
  at,
  onDragStart,
  onDropAt,
  dragging,
}: {
  entry: QueueEntry;
  at: number;
  onDragStart: (at: number) => void;
  onDropAt: (at: number) => void;
  dragging: boolean;
}) {
  const { jumpTo, removeAt, playNext } = usePlayer.getState();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [over, setOver] = useState(false);
  const close = useCallback(() => setAnchor(null), []);
  const { track } = entry;

  return (
    <li
      className={styles.row}
      data-over={over || undefined}
      data-dragging={dragging || undefined}
      draggable
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = 'move';
        onDragStart(at);
      }}
      onDragOver={(e) => {
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setOver(false);
        onDropAt(at);
      }}
      onDoubleClick={() => jumpTo(at)}
    >
      <div className={styles.art}>
        <Artwork src={track.thumbnail} displayWidth={40} radius="var(--radius-artwork-small)" />
        <button className={styles.remove} aria-label={`Remove ${track.title}`} onClick={() => removeAt(at)}>
          <X size={12} strokeWidth={3} />
        </button>
      </div>
      <button className={styles.text} onClick={() => jumpTo(at)} aria-label={`Play ${track.title}`}>
        <span className={styles.title}>{track.title}</span>
        <span className={styles.subtitle}>{track.artists.map((a) => a.name).join(', ')}</span>
      </button>
      <div className={styles.end}>
        <span className={styles.duration}>{formatDuration(track.durationSec)}</span>
        <button
          className={styles.more}
          aria-label={`More options for ${track.title}`}
          aria-haspopup="menu"
          data-open={anchor !== null || undefined}
          onClick={(e) => setAnchor(anchor ? null : e.currentTarget)}
        >
          <MoreHorizontal size={16} />
        </button>
      </div>
      {anchor && (
        <Menu anchor={anchor} onClose={close} label={track.title}>
          <MenuItem onSelect={() => (close(), jumpTo(at))}>Play Now</MenuItem>
          <MenuItem
            onSelect={() => {
              close();
              removeAt(at);
              playNext([track], entry.source);
            }}
          >
            Play Next
          </MenuItem>
          <MenuSeparator />
          <MenuItem destructive onSelect={() => (close(), removeAt(at))}>
            Remove from Up Next
          </MenuItem>
        </Menu>
      )}
    </li>
  );
}

/** Apple's Up Next (report §7), over the three-tier queue: what's next, then AutoPlay's section. */
export function UpNext() {
  const queue = usePlayer((s) => s.queue);
  const index = usePlayer((s) => s.index);
  const localAutoplay = usePlayer((s) => s.autoplay);
  // In a party AutoPlay is the party's setting (Autoplay.autoplayEnabledFor).
  const inParty = useParty((s) => !!s.code);
  const partyAutoplay = useParty((s) => s.playback.autoplayEnabled);
  const autoplay = inParty ? partyAutoplay : localAutoplay;
  const repeat = usePlayer((s) => s.repeat);
  const crossfadeSec = usePlayer((s) => s.crossfadeSec);
  const { clearUpNext, setAutoplay, setCrossfade, move } = usePlayer.getState();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [dragFrom, setDragFrom] = useState<number | null>(null);
  const close = useCallback(() => setAnchor(null), []);

  const tiers = queue.map((e) => e.tier);
  const autoplayAt = autoplaySectionStart(tiers, index);
  const canClear = upcomingUserQueueIndices(tiers, index).length > 0;
  const upcoming = queue.map((entry, at) => ({ entry, at })).filter(({ at }) => at > index);
  const beforeAutoplay = upcoming.filter(({ at }) => at < autoplayAt);
  const afterAutoplay = upcoming.filter(({ at }) => at >= autoplayAt);
  const playingFrom = queue[index]?.source?.title;

  const rows = (items: typeof upcoming) =>
    items.map(({ entry, at }) => (
      <Row
        key={entry.entryId}
        entry={entry}
        at={at}
        dragging={dragFrom === at}
        onDragStart={setDragFrom}
        onDropAt={(to) => {
          if (dragFrom !== null) move(dragFrom, to);
          setDragFrom(null);
        }}
      />
    ));

  return (
    <>
      <div className={styles.header}>
        <h3 className={styles.heading}>Up Next</h3>
        {canClear && (
          <button className={styles.clear} onClick={clearUpNext}>
            Clear
          </button>
        )}
        <button
          className={styles.autoplay}
          aria-label="AutoPlay"
          title="AutoPlay: keep playing similar music when the queue ends"
          aria-pressed={autoplay}
          onClick={() => (inParty ? party.setAutoplay(!autoplay) : setAutoplay(!autoplay))}
        >
          <InfinityIcon size={16} strokeWidth={2.4} />
        </button>
        <button className={styles.options} aria-label="Playback options" aria-haspopup="menu" onClick={(e) => setAnchor(e.currentTarget)}>
          <MoreHorizontal size={16} />
        </button>
        {anchor && (
          <Menu anchor={anchor} onClose={close} label="Playback options">
            <MenuHeader>Crossfade</MenuHeader>
            {CROSSFADE_CHOICES.map((seconds) => (
              <MenuItem key={seconds} checked={crossfadeSec === seconds} onSelect={() => (close(), setCrossfade(seconds))}>
                {seconds === 0 ? 'Off' : `${seconds} seconds`}
              </MenuItem>
            ))}
          </Menu>
        )}
      </div>
      {playingFrom && <p className={styles.from}>Playing from {playingFrom}</p>}

      {upcoming.length === 0 ? (
        <div className={styles.empty}>
          <p className={styles.emptyTitle}>Nothing in Up Next</p>
          <p>
            {autoplay && repeat === 'off' && index >= 0
              ? 'AutoPlay is finding similar music.'
              : 'Songs you play next appear here.'}
          </p>
        </div>
      ) : (
        <div className={styles.scroll}>
          <ol className={styles.list}>{rows(beforeAutoplay)}</ol>
          {afterAutoplay.length > 0 && (
            <>
              <h4 className={styles.section}>
                <InfinityIcon size={14} strokeWidth={2.4} /> AutoPlay
              </h4>
              <p className={styles.sectionDetail}>Similar music, from YouTube Music’s radio.</p>
              <ol className={styles.list}>{rows(afterAutoplay)}</ol>
            </>
          )}
        </div>
      )}
    </>
  );
}
