import { useParams } from 'react-router';
import { PlayIcon } from '@/components/icons/Transport';
import { Artwork } from '@/components/music/Artwork';
import { browseHref } from '@/components/music/links';
import { playList } from '@/components/music/play';
import { Shelves, TrackShelf } from '@/components/music/ShelfView';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { useArtist, useYouTubeReady } from '@/data/music/queries';
import { ArtistSkeleton } from '@/skeletons/PageSkeletons';
import styles from './ArtistPage.module.css';

/** An artist (report §4): full-bleed hero with the name and Play, Top Songs, then shelves. */
export default function ArtistPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const artist = useArtist(id);

  if (missing) return <NeedsExtension />;
  if (artist.isError) return <LoadError error={artist.error} onRetry={() => void artist.refetch()} />;
  if (!artist.data) return <ArtistSkeleton />;

  const data = artist.data;
  const topSongs = { title: `${data.name}: Top Songs`, type: 'artist' as const, id: data.id };
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
            onClick={() => playList(data.topSongs, 0, { source: topSongs })}
          >
            <PlayIcon size={26} />
          </button>
        </div>
      </header>

      {data.topSongs.length > 0 && (
        <TrackShelf
          title="Top Songs"
          to={data.topSongsSeeAll ? browseHref(data.topSongsSeeAll) : null}
          tracks={data.topSongs}
          source={topSongs}
        />
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
