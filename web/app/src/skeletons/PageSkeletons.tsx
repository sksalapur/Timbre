import { PageHeader } from '@/components/PageHeader';
import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';
import { DetailSkeleton } from './DetailSkeleton';
import styles from './PageSkeletons.module.css';
import { ShelfSkeleton } from './ShelfSkeleton';

// Route fallbacks. They live in the main bundle, so a route's skeleton shows
// before its own chunk has loaded. The title is real text: it's known up front.

export function HomeSkeleton() {
  return (
    <div aria-busy="true">
      <PageHeader title="Home" />
      <ShelfSkeleton kind="editorial" />
      <ShelfSkeleton kind="tracks" />
      <ShelfSkeleton kind="square" />
      <ShelfSkeleton kind="square" />
    </div>
  );
}

export function NewSkeleton() {
  return (
    <div aria-busy="true">
      <PageHeader title="New" />
      <ShelfSkeleton kind="editorial" title={false} />
      <ShelfSkeleton kind="tracks" />
      <ShelfSkeleton kind="square" />
      <ShelfSkeleton kind="square" />
    </div>
  );
}

export function SearchSkeleton() {
  return (
    <div aria-busy="true">
      <div className={styles.searchField} aria-hidden="true">
        <SkeletonBlock width="min(492px, 100%)" height={38} radius="var(--radius-pill)" />
      </div>
      <div className={styles.sectionTitle} aria-hidden="true">
        <SkeletonLine lineHeight={22} fontSize={17} width={160} />
      </div>
      <div className={styles.categories} aria-hidden="true">
        {Array.from({ length: 16 }, (_, i) => (
          <SkeletonBlock key={i} aspectRatio="16 / 9" radius="var(--radius-artwork-large)" />
        ))}
      </div>
    </div>
  );
}

export function AlbumSkeleton() {
  return <DetailSkeleton />;
}

export function PlaylistSkeleton() {
  return <DetailSkeleton rowArtwork rows={10} />;
}

export function ArtistSkeleton() {
  return (
    <div aria-busy="true">
      <SkeletonBlock className={styles.hero} />
      <ShelfSkeleton kind="tracks" />
      <ShelfSkeleton kind="square" />
      <ShelfSkeleton kind="square" />
      <ShelfSkeleton kind="circle" />
    </div>
  );
}

export function PlainSkeleton() {
  return (
    <div aria-busy="true" className={styles.sectionTitle}>
      <SkeletonLine lineHeight={40} fontSize={34} width={220} style={{ marginTop: 32 }} />
    </div>
  );
}
