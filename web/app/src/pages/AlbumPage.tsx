import { useParams } from 'react-router';
import { DetailHeader } from '@/components/music/DetailHeader';
import { playTrack } from '@/components/music/play';
import { Shelves } from '@/components/music/ShelfView';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { useAlbum, useYouTubeReady } from '@/data/music/queries';
import { useArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { AlbumSkeleton } from '@/skeletons/PageSkeletons';
import styles from './DetailPage.module.css';

/** An album (report §3): header, numbered tracks, footer, "more" shelves. */
export default function AlbumPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const album = useAlbum(id);
  usePageTint(useArtworkColor(album.data?.thumbnail));

  if (missing) return <NeedsExtension />;
  if (album.isError) return <LoadError error={album.error} onRetry={() => void album.refetch()} />;
  if (!album.data) return <AlbumSkeleton />;

  const data = album.data;
  const artists = data.artists.map((a) => a.name).join(', ');
  // Album tracks carry no artist of their own; playing one credits the album's.
  const tracks = data.tracks;
  const credit = (t: (typeof tracks)[number]) => (t.artists.length ? t : { ...t, artists: data.artists });
  return (
    <>
      <DetailHeader
        info={data}
        canPlay={tracks.length > 0}
        onPlay={() => tracks[0] && playTrack(credit(tracks[0]), data.thumbnail)}
        onShuffle={() => {
          const pick = tracks[Math.floor(Math.random() * tracks.length)];
          if (pick) playTrack(credit(pick), data.thumbnail);
        }}
      />
      <TrackList tracks={tracks} variant="album" fallbackArtwork={data.thumbnail} fallbackArtists={data.artists} />
      <footer className={styles.footer}>
        <p>{data.subtitle}</p>
        <p>{data.meta}</p>
        {artists && <p>{artists}</p>}
      </footer>
      {data.more.length > 0 && (
        <div className={styles.alternate}>
          <Shelves shelves={data.more} />
        </div>
      )}
    </>
  );
}
