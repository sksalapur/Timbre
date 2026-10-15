import { useCallback } from 'react';
import { useParams } from 'react-router';
import { DetailHeader } from '@/components/music/DetailHeader';
import { playTrack } from '@/components/music/play';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { TrackList } from '@/components/music/TrackList';
import { usePlaylist, useYouTubeReady } from '@/data/music/queries';
import { useArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { PlaylistSkeleton } from '@/skeletons/PageSkeletons';
import styles from './DetailPage.module.css';

/** A playlist (report §14): header, then the artwork track list, loading on as you scroll. */
export default function PlaylistPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const playlist = usePlaylist(id);
  const header = playlist.data?.pages[0]?.header ?? null;
  usePageTint(useArtworkColor(header?.thumbnail));

  const { hasNextPage, isFetchingNextPage, fetchNextPage } = playlist;
  const onNearEnd = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) void fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (missing) return <NeedsExtension />;
  if (playlist.isError) return <LoadError error={playlist.error} onRetry={() => void playlist.refetch()} />;
  if (!playlist.data || !header) return <PlaylistSkeleton />;

  const tracks = playlist.data.pages.flatMap((page) => page.tracks);
  return (
    <>
      <DetailHeader
        info={header}
        canPlay={tracks.length > 0}
        onPlay={() => tracks[0] && playTrack(tracks[0])}
        onShuffle={() => {
          const pick = tracks[Math.floor(Math.random() * tracks.length)];
          if (pick) playTrack(pick);
        }}
      />
      <TrackList tracks={tracks} variant="playlist" onNearEnd={onNearEnd} />
      {isFetchingNextPage && <div className={styles.loadingMore}>Loading more songs…</div>}
      <footer className={styles.footer}>
        <p>{header.meta}</p>
      </footer>
    </>
  );
}
