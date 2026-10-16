import { PageHeader } from '@/components/PageHeader';
import { CategoryTiles } from '@/components/music/CategoryTiles';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { useCategories, useYouTubeReady } from '@/data/music/queries';
import { SearchSkeleton } from '@/skeletons/PageSkeletons';

/**
 * Radio. YouTube Music has no stations directory; its moods and genres are
 * the closest thing (each opens playlists and mixes for that mood).
 */
export default function RadioPage() {
  const { missing } = useYouTubeReady();
  const categories = useCategories();

  if (missing) {
    return (
      <>
        <PageHeader title="Radio" />
        <NeedsExtension />
      </>
    );
  }
  if (categories.isError) {
    return (
      <>
        <PageHeader title="Radio" />
        <LoadError error={categories.error} onRetry={() => void categories.refetch()} />
      </>
    );
  }
  if (!categories.data) return <SearchSkeleton />;
  return (
    <>
      <PageHeader title="Radio" />
      <CategoryTiles groups={categories.data} />
    </>
  );
}
