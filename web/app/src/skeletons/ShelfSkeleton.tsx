import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';
import shelf from '@/components/Shelf.module.css';
import styles from './ShelfSkeleton.module.css';

export type ShelfKind = 'editorial' | 'square' | 'tracks' | 'horizontal' | 'video' | 'circle';

/** How many placeholders fill a shelf at the widest breakpoint, per row. */
const ITEMS: Record<ShelfKind, number> = {
  editorial: 3,
  square: 6,
  tracks: 16,
  horizontal: 8,
  video: 5,
  circle: 10,
};

function Item({ kind }: { kind: ShelfKind }) {
  switch (kind) {
    case 'square':
      return (
        <div className={styles.square}>
          <SkeletonBlock aspectRatio="1" radius="var(--radius-artwork-medium)" />
          <div className={styles.caption}>
            <SkeletonLine lineHeight={15} fontSize={12} width="80%" />
            <SkeletonLine lineHeight={15} fontSize={12} width="55%" />
          </div>
        </div>
      );
    case 'video':
      return (
        <div className={styles.video}>
          <SkeletonBlock aspectRatio="16 / 9" radius="var(--radius-artwork-medium)" />
          <div className={styles.caption}>
            <SkeletonLine lineHeight={15} fontSize={12} width="75%" />
            <SkeletonLine lineHeight={15} fontSize={12} width="45%" />
          </div>
        </div>
      );
    case 'circle':
      return (
        <div className={styles.circle}>
          <SkeletonBlock aspectRatio="1" radius="50%" />
          <div className={styles.caption}>
            <SkeletonLine lineHeight={16} fontSize={13} width="65%" />
          </div>
        </div>
      );
    case 'tracks':
      return (
        <div className={styles.track}>
          <SkeletonBlock width={40} height={40} radius="var(--radius-artwork-small)" />
          <div className={styles.trackText}>
            <SkeletonLine lineHeight={16} fontSize={13} width="70%" />
            <SkeletonLine lineHeight={15} fontSize={12} width="40%" />
          </div>
        </div>
      );
    case 'horizontal':
      return (
        <div className={styles.horizontal}>
          <SkeletonBlock width={97} height={97} radius="var(--radius-artwork-small)" />
          <div className={styles.trackText}>
            <SkeletonLine lineHeight={20} fontSize={15} width="75%" />
            <SkeletonLine lineHeight={15} fontSize={12} width="45%" />
          </div>
        </div>
      );
    case 'editorial':
      return (
        <div className={styles.editorial}>
          <div className={styles.editorialText}>
            <SkeletonLine lineHeight={14} fontSize={11} width="30%" style={{ marginBottom: 2 }} />
            <SkeletonLine lineHeight={20} fontSize={15} width="45%" />
            <SkeletonLine lineHeight={20} fontSize={15} width="35%" />
          </div>
          <SkeletonBlock aspectRatio="16 / 9" radius="var(--radius-artwork-large)" />
        </div>
      );
  }
}

/** A shelf in waiting: the real shelf's grid, filled with placeholders. */
export function ShelfSkeleton({ kind, title = true }: { kind: ShelfKind; title?: boolean }) {
  return (
    <section className={shelf.section} aria-hidden="true">
      {title && (
        <div className={shelf.header}>
          <SkeletonLine lineHeight={22} fontSize={17} width={180} />
        </div>
      )}
      <div className={shelf.body}>
        <div className={`${shelf.list} ${shelf[kind]}`}>
          {Array.from({ length: ITEMS[kind] }, (_, i) => (
            <Item key={i} kind={kind} />
          ))}
        </div>
      </div>
    </section>
  );
}
