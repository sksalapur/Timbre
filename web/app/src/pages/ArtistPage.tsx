import { useParams } from 'react-router';
import { PrefetchLink } from '@/components/PrefetchLink';
import { PlayIcon } from '@/components/icons/Transport';
import shelfStyles from '@/components/Shelf.module.css';
import { Artwork } from '@/components/music/Artwork';
import { browseHref } from '@/components/music/links';
import { TrackLockup } from '@/components/music/Lockups';
import { playTrack } from '@/components/music/play';
import { Shelves } from '@/components/music/ShelfView';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { useArtist, useYouTubeReady } from '@/data/music/queries';
import type { MusicItem, Track } from '@/data/music/types';
import { ArtistSkeleton } from '@/skeletons/PageSkeletons';
import styles from './ArtistPage.module.css';

const asItem = (track: Track): MusicItem => ({
  kind: 'song',
  id: track.videoId,
  title: track.title,
  subtitle: track.album?.name ?? '',
  artists: track.artists,
  album: track.album,
  thumbnail: track.thumbnail,
  wide: false,
  durationSec: track.durationSec,
  explicit: track.explicit,
});

/** An artist (report §4): full-bleed hero with the name and Play, Top Songs, then shelves. */
export default function ArtistPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const artist = useArtist(id);

  if (missing) return <NeedsExtension />;
  if (artist.isError) return <LoadError error={artist.error} onRetry={() => void artist.refetch()} />;
  if (!artist.data) return <ArtistSkeleton />;

  const data = artist.data;
  return (
    <>
      <header className={styles.hero}>
        <div className={styles.heroImage}>
          <Artwork src={data.heroImage} displayWidth={1200} aspect={1165 / 604} eager />
        </div>
        <div className={styles.heroFade} aria-hidden="true" />
        <div className={styles.heroContent}>
          <h1 className={styles.name}>{data.name}</h1>
          {data.audience && <p className={styles.audience}>{data.audience}</p>}
          <button
            className={styles.play}
            aria-label={`Play ${data.name}`}
            disabled={!data.topSongs.length}
            onClick={() => data.topSongs[0] && playTrack(data.topSongs[0])}
          >
            <PlayIcon size={26} />
          </button>
        </div>
      </header>

      {data.topSongs.length > 0 && (
        <section className={shelfStyles.section} aria-label="Top Songs">
          <div className={shelfStyles.header}>
            <h2 className={styles.sectionTitle}>
              {data.topSongsSeeAll ? <PrefetchLink to={browseHref(data.topSongsSeeAll)}>Top Songs ›</PrefetchLink> : 'Top Songs'}
            </h2>
          </div>
          <div className={shelfStyles.body}>
            <ul className={`${shelfStyles.list} ${shelfStyles.tracks}`}>
              {data.topSongs.map((track) => (
                <li key={track.videoId}>
                  <TrackLockup item={asItem(track)} />
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}

      <Shelves shelves={data.shelves} />

      {data.description && (
        <section className={styles.about} aria-label={`About ${data.name}`}>
          <h2 className={styles.sectionTitle}>About {data.name}</h2>
          <p className={styles.aboutText}>{data.description}</p>
        </section>
      )}
    </>
  );
}
