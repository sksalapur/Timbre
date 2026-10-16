import { useMemo, useState } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { useTrackLog } from '@/playback/trackLog';
import { usePlayer } from '@/state/player';
import styles from './DebugPage.module.css';

/**
 * The phase 3 acceptance set (build prompt §11: old, new, regional, explicit
 * and age-restricted tracks). Ids are YouTube Music's first song result for
 * each query, taken 2026-10-15; the last two are the age-gated videos
 * yt-dlp's own tests use.
 */
export const TEST_TRACKS: { id: string; label: string }[] = [
  { id: 'ZEcqHA7dbwM', label: 'Frank Sinatra · Fly Me To The Moon (1964)' },
  { id: 'xUNqsfFUwhY', label: 'The Beatles · Here Comes The Sun (1969)' },
  { id: 'h4R4aSIAG0A', label: 'Chris Isaak · Can’t Help Falling In Love' },
  { id: 'BSTsnWoslP4', label: 'Queen · Bohemian Rhapsody (1975)' },
  { id: '7CTJcHjkq0E', label: 'Michael Jackson · Billie Jean (1982)' },
  { id: 'ZVgHPSyEIqk', label: 'Radiohead · Let Down (1997)' },
  { id: 'J7p4bzqLvCw', label: 'The Weeknd · Blinding Lights (2019)' },
  { id: '3YgtjHZyCIQ', label: 'Taylor Swift · Anti-Hero (2022)' },
  { id: 'kIft-LUHHVA', label: 'Sabrina Carpenter · Espresso (2024, explicit)' },
  { id: 'NJAv_7lHUIU', label: 'Arijit Singh · Kesariya (Hindi)' },
  { id: 'FM2ykrYbzqg', label: 'AP Dhillon · Brown Munde (Punjabi)' },
  { id: 'HaEYUJ2aRHs', label: 'BTS · Dynamite (K-pop)' },
  { id: 'juRFjpB5Ppg', label: 'Bad Bunny · Tití Me Preguntó (Spanish, explicit)' },
  { id: 'RHR0tKRxiyY', label: 'Fela Kuti · Water No Get Enemy (Afrobeat)' },
  { id: 'gI7hyn18Xn8', label: 'Lata Mangeshkar · Lag Ja Gale (1964, Hindi)' },
  { id: '4wOLVrGHiIU', label: 'Eminem · Lose Yourself (explicit)' },
  { id: 'H4RELGc9su8', label: 'Kendrick Lamar · HUMBLE. (explicit)' },
  { id: 'Y3SKjM_2M5k', label: 'Cardi B · WAP' },
  { id: 'Tq92D6wQ1mg', label: 'Age-restricted: [MMD] Adios · EVERGLOW' },
  { id: 'HtVdAasjOgU', label: 'Age-restricted: The Witcher 3 trailer' },
];

/** A videoId out of whatever was pasted: an id, a watch URL, a youtu.be or music.youtube.com link. */
export function parseVideoId(input: string): string | null {
  const text = input.trim();
  if (/^[\w-]{11}$/.test(text)) return text;
  try {
    const url = new URL(text);
    const id = url.searchParams.get('v') ?? (url.hostname === 'youtu.be' ? url.pathname.slice(1) : null);
    return id && /^[\w-]{11}$/.test(id) ? id : null;
  } catch {
    return null;
  }
}

/** The web equivalent of the app's track log viewer, plus a way to play anything. */
export default function DebugPage() {
  const [input, setInput] = useState('');
  const [filter, setFilter] = useState<'all' | 'current'>('current');
  const lines = useTrackLog((s) => s.lines);
  const clear = useTrackLog((s) => s.clear);
  const playVideo = usePlayer((s) => s.playVideo);
  const nowPlaying = usePlayer((s) => s.nowPlaying);

  const shown = useMemo(
    () =>
      filter === 'current' && nowPlaying
        ? lines.filter((l) => l.about === nowPlaying.videoId || l.about === null)
        : lines,
    [lines, filter, nowPlaying],
  );
  const id = parseVideoId(input);

  return (
    <>
      <PageHeader title="Debug" />
      <div className={styles.body}>
        <form
          className={styles.form}
          onSubmit={(e) => {
            e.preventDefault();
            if (id) void playVideo(id);
          }}
        >
          <input
            className={styles.field}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Video id or YouTube link"
            aria-label="Video id or YouTube link"
          />
          <button className={styles.button} type="submit" disabled={!id}>
            Play
          </button>
        </form>
        {nowPlaying?.streamInfo && <p className={styles.stream}>{nowPlaying.streamInfo}</p>}

        <h2 className={styles.sectionHeader}>Test tracks</h2>
        <ul className={styles.tracks}>
          {TEST_TRACKS.map((t) => (
            <li key={t.id}>
              <button
                className={styles.track}
                data-current={nowPlaying?.videoId === t.id}
                onClick={() => void playVideo(t.id)}
              >
                <span>{t.label}</span>
                <code>{t.id}</code>
              </button>
            </li>
          ))}
        </ul>

        <div className={styles.logHeader}>
          <h2 className={styles.sectionHeader}>Track log</h2>
          <div className={styles.logActions}>
            <button onClick={() => setFilter(filter === 'all' ? 'current' : 'all')}>
              {filter === 'all' ? 'Show current track' : 'Show everything'}
            </button>
            <button onClick={clear}>Clear</button>
          </div>
        </div>
        <ol className={styles.log} data-testid="track-log">
          {shown.map((line) => (
            <li key={line.id} data-level={line.level}>
              <span className={styles.time}>{(line.t / 1000).toFixed(2)}</span>
              {line.about && <span className={styles.about}>{line.about}</span>}
              <span>{line.message}</span>
            </li>
          ))}
        </ol>
      </div>
    </>
  );
}
