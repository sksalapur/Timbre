import { useUi } from '@/state/ui';
import styles from './SidePanel.module.css';

/** Up Next / Lyrics glass panel (report §7). Both are empty until playback lands. */
export function SidePanel() {
  const panel = useUi((s) => s.sidePanel);
  if (!panel) return null;

  return (
    <aside className={styles.panel} aria-label={panel === 'up-next' ? 'Up Next' : 'Lyrics'}>
      {panel === 'up-next' ? (
        <>
          <div className={styles.header}>
            <h3 className={styles.title}>Up Next</h3>
          </div>
          <div className={styles.empty}>
            <p className={styles.emptyTitle}>Nothing in Up Next</p>
            <p>Songs you play next appear here.</p>
          </div>
        </>
      ) : (
        <div className={styles.empty}>
          <p className={styles.emptyTitle}>No lyrics available</p>
        </div>
      )}
    </aside>
  );
}
