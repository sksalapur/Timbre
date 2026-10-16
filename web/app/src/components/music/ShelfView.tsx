import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { PrefetchLink } from '@/components/PrefetchLink';
import shelfStyles from '@/components/Shelf.module.css';
import type { MusicItem, Shelf } from '@/data/music/types';
import { browseHref } from './links';
import { CircleLockup, SquareLockup, TrackLockup, VideoLockup } from './Lockups';
import styles from './ShelfView.module.css';

const LAYOUT_CLASS = {
  tracks: shelfStyles.tracks,
  square: shelfStyles.square,
  video: shelfStyles.video,
  circle: shelfStyles.circle,
} as const;

function renderItem(shelf: Shelf, item: MusicItem): ReactNode {
  switch (shelf.layout) {
    case 'tracks':
      return <TrackLockup item={item} />;
    case 'video':
      return <VideoLockup item={item} />;
    case 'circle':
      return <CircleLockup item={item} />;
    default:
      return <SquareLockup item={item} />;
  }
}

/**
 * A shelf's scrolling row with Apple's hover arrows (report §2), around any
 * list items: the shelves below, and the artist page's Top Songs.
 */
export function ShelfScroller({ layoutClass, children }: { layoutClass: string | undefined; children: ReactNode }) {
  const list = useRef<HTMLUListElement>(null);
  const [edges, setEdges] = useState({ start: true, end: true });

  const measure = useCallback(() => {
    const el = list.current;
    if (!el) return;
    setEdges({ start: el.scrollLeft <= 2, end: el.scrollLeft + el.clientWidth >= el.scrollWidth - 2 });
  }, []);

  useEffect(() => {
    measure();
    const el = list.current;
    if (!el) return;
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [measure]);

  /** Pages by one viewport of columns, as Apple's arrows do. */
  const page = (direction: 1 | -1) => {
    const el = list.current;
    if (!el) return;
    const gutter = Number.parseFloat(getComputedStyle(el).paddingInlineStart) || 0;
    el.scrollBy({ left: direction * (el.clientWidth - gutter * 2), behavior: 'smooth' });
  };

  return (
    <div className={`${shelfStyles.body} ${styles.body}`}>
      <button
        className={`${styles.arrow} ${styles.arrowLeft}`}
        aria-label="Previous Page"
        disabled={edges.start}
        onClick={() => page(-1)}
      >
        <ChevronLeft size={20} strokeWidth={2.4} />
      </button>
      <ul ref={list} className={`${shelfStyles.list} ${layoutClass}`} onScroll={measure}>
        {children}
      </ul>
      <button
        className={`${styles.arrow} ${styles.arrowRight}`}
        aria-label="Next Page"
        disabled={edges.end}
        onClick={() => page(1)}
      >
        <ChevronRight size={20} strokeWidth={2.4} />
      </button>
    </div>
  );
}

/** A titled horizontal shelf (report §2): grid columns per breakpoint, scroll-snap, hover arrows. */
export function ShelfView({ shelf }: { shelf: Shelf }) {
  return (
    <section className={shelfStyles.section} aria-label={shelf.title}>
      <div className={shelfStyles.header}>
        <h2 className={styles.title}>
          {shelf.seeAll ? (
            <PrefetchLink to={browseHref(shelf.seeAll)} className={styles.titleLink}>
              {shelf.title}
              <ChevronRight size={18} strokeWidth={2.6} className={styles.chevron} />
            </PrefetchLink>
          ) : (
            shelf.title
          )}
        </h2>
      </div>
      <ShelfScroller key={shelf.items.length} layoutClass={LAYOUT_CLASS[shelf.layout]}>
        {shelf.items.map((item, i) => (
          <li key={`${item.id}-${i}`}>{renderItem(shelf, item)}</li>
        ))}
      </ShelfScroller>
    </section>
  );
}

/** Shelves in order, each its own section. */
export function Shelves({ shelves }: { shelves: Shelf[] }) {
  return (
    <>
      {shelves.map((shelf, i) => (
        <ShelfView key={`${shelf.title}-${i}`} shelf={shelf} />
      ))}
    </>
  );
}
