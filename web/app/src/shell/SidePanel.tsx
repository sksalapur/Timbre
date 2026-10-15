import { useUi } from '@/state/ui';
import styles from './SidePanel.module.css';
import { UpNext } from './UpNext';

/** Up Next / Lyrics glass panel (report §7). Lyrics arrive in phase 8. */
export function SidePanel() {
  const panel = useUi((s) => s.sidePanel);
  if (!panel) return null;

  return (
    <aside className={styles.panel} aria-label={panel === 'up-next' ? 'Up Next' : 'Lyrics'}>
      {panel === 'up-next' ? (
        <UpNext />
      ) : (
        <div className={styles.empty}>
          <p className={styles.emptyTitle}>No lyrics available</p>
        </div>
      )}
    </aside>
  );
}
