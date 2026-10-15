import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';
import styles from './DetailSkeleton.module.css';

/** Album and playlist pages: artwork header, then the track list. */
export function DetailSkeleton({ rows = 12, rowArtwork = false }: { rows?: number; rowArtwork?: boolean }) {
  return (
    <div aria-busy="true" aria-label="Loading">
      <div className={styles.header} aria-hidden="true">
        <SkeletonBlock className={styles.artwork} aspectRatio="1" radius="var(--radius-artwork-large)" />
        <div className={styles.headings}>
          <SkeletonLine lineHeight={32} fontSize={26} width={260} />
          <SkeletonLine lineHeight={32} fontSize={26} width={180} />
          <SkeletonLine lineHeight={15} fontSize={12} width={90} style={{ marginTop: 4 }} />
          <SkeletonLine lineHeight={18} fontSize={13} width="min(440px, 100%)" style={{ marginTop: 38 }} />
          <SkeletonLine lineHeight={18} fontSize={13} width="min(380px, 90%)" />
          <div className={styles.actions}>
            <SkeletonBlock width={100} height={36} radius={24} />
            <SkeletonBlock width={100} height={36} radius={24} />
          </div>
        </div>
      </div>
      <div className={styles.rows} aria-hidden="true">
        {Array.from({ length: rows }, (_, i) => (
          <div key={i} className={rowArtwork ? `${styles.row} ${styles.rowArtwork}` : styles.row}>
            {rowArtwork ? (
              <SkeletonBlock width={40} height={40} radius="var(--radius-artwork-small)" />
            ) : (
              <SkeletonLine lineHeight={16} fontSize={13} width={12} />
            )}
            <SkeletonLine lineHeight={16} fontSize={13} width={`${30 + ((i * 37) % 35)}%`} />
            <SkeletonLine lineHeight={16} fontSize={13} width={30} style={{ marginRight: 35 }} />
          </div>
        ))}
      </div>
    </div>
  );
}
