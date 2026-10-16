import { useSearchParams } from 'react-router';
import { PrefetchLink } from '@/components/PrefetchLink';
import { Artwork } from '@/components/music/Artwork';
import { CategoryTiles } from '@/components/music/CategoryTiles';
import { hrefFor } from '@/components/music/links';
import { playTrack } from '@/components/music/play';
import { SearchField } from '@/components/music/SearchField';
import { Shelves } from '@/components/music/ShelfView';
import { EmptyState, LoadError, NeedsExtension } from '@/components/music/States';
import { useCategories, useSearch, useYouTubeReady } from '@/data/music/queries';
import type { MusicItem, SearchResults, Shelf } from '@/data/music/types';
import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';
import { ShelfSkeleton } from '@/skeletons/ShelfSkeleton';
import styles from './SearchPage.module.css';

const KIND_LABEL: Record<MusicItem['kind'], string> = {
  song: 'Song',
  video: 'Video',
  album: 'Album',
  playlist: 'Playlist',
  'spotify-playlist': 'Spotify Playlist',
  artist: 'Artist',
  episode: 'Episode',
  podcast: 'Podcast',
  unknown: '',
};

/** Apple's result shelves, in its order, from results grouped by kind. */
function shelvesFor(results: SearchResults): Shelf[] {
  const shelf = (title: string, items: MusicItem[], layout: Shelf['layout']): Shelf | null =>
    items.length ? { title, strapline: null, layout, items, seeAll: null } : null;
  return [
    shelf('Artists', results.artists, 'circle'),
    shelf('Albums', results.albums, 'square'),
    shelf('Songs', results.songs, 'tracks'),
    shelf('Playlists', results.playlists, 'square'),
    shelf('Videos', results.videos, 'video'),
  ].filter((s): s is Shelf => !!s);
}

function TopResult({ item }: { item: MusicItem }) {
  const href = hrefFor(item);
  const body = (
    <>
      <div className={styles.topArtwork}>
        <Artwork src={item.thumbnail} displayWidth={90} radius={item.kind === 'artist' ? '50%' : 'var(--radius-artwork-small)'} />
      </div>
      <div className={styles.topText}>
        <span className={styles.topTitle}>{item.title}</span>
        <span className={styles.topKind}>
          {[KIND_LABEL[item.kind], item.kind === 'artist' ? null : item.artists.map((a) => a.name).join(', ')].filter(Boolean).join(' · ')}
        </span>
      </div>
    </>
  );
  return href ? (
    <PrefetchLink to={href} className={styles.top}>
      {body}
    </PrefetchLink>
  ) : (
    <button className={styles.top} onClick={() => playTrack(item)}>
      {body}
    </button>
  );
}

function ResultsSkeleton() {
  return (
    <div aria-busy="true">
      <div className={styles.sectionTitle}>
        <SkeletonLine lineHeight={22} fontSize={17} width={130} />
      </div>
      <div className={styles.topGrid} aria-hidden="true">
        {Array.from({ length: 6 }, (_, i) => (
          <div key={i} className={styles.top}>
            <SkeletonBlock width={90} height={90} radius="var(--radius-artwork-small)" />
            <div className={styles.topText}>
              <SkeletonLine lineHeight={20} fontSize={15} width="70%" />
              <SkeletonLine lineHeight={15} fontSize={12} width="40%" />
            </div>
          </div>
        ))}
      </div>
      <ShelfSkeleton kind="circle" />
      <ShelfSkeleton kind="square" />
    </div>
  );
}

function Results({ query }: { query: string }) {
  const search = useSearch(query);
  if (search.isError) return <LoadError error={search.error} onRetry={() => void search.refetch()} />;
  if (!search.data) return <ResultsSkeleton />;
  const results = search.data;
  const shelves = shelvesFor(results);
  if (!results.top.length && !shelves.length) {
    return <EmptyState title="No Results" detail={`Try a new search for something other than “${query}”.`} />;
  }
  return (
    <>
      {results.top.length > 0 && (
        <section aria-label="Top Results">
          <h2 className={styles.sectionTitle}>Top Results</h2>
          <div className={styles.topGrid}>
            {results.top.map((item) => (
              <TopResult key={item.id} item={item} />
            ))}
          </div>
        </section>
      )}
      <Shelves shelves={shelves} />
    </>
  );
}

function Landing() {
  const categories = useCategories();
  if (categories.isError) return <LoadError error={categories.error} onRetry={() => void categories.refetch()} />;
  if (!categories.data) {
    return (
      <div className={styles.landingSkeleton} aria-busy="true">
        {Array.from({ length: 12 }, (_, i) => (
          <SkeletonBlock key={i} aspectRatio="16 / 9" radius="var(--radius-artwork-large)" />
        ))}
      </div>
    );
  }
  return <CategoryTiles groups={categories.data.map((g) => ({ ...g, title: g.title || 'Browse Categories' }))} />;
}

/** Search (report §6): a sticky bar with the field, then categories or grouped results. */
export default function SearchPage() {
  const { missing } = useYouTubeReady();
  const [params, setParams] = useSearchParams();
  const query = params.get('q') ?? '';

  return (
    <>
      <div className={styles.scopeBar}>
        <SearchField initial={query} onSearch={(q) => setParams(q ? { q } : {})} />
      </div>
      {missing ? <NeedsExtension what="and search YouTube Music" /> : query ? <Results query={query} /> : <Landing />}
    </>
  );
}
