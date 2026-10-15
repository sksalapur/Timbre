import { ArrowUpRight, House, LayoutGrid, Puzzle, Radio, Search } from 'lucide-react';
import type { ComponentType } from 'react';
import { PrefetchLink, PrefetchNavLink } from '@/components/PrefetchLink';
import { LogoMark } from '@/components/icons/LogoMark';
import { config } from '@/config';
import { useUi } from '@/state/ui';
import styles from './Sidebar.module.css';

interface NavItem {
  to: string;
  label: string;
  Icon: ComponentType<{ size?: number; strokeWidth?: number; className?: string }>;
}

const PRIMARY: NavItem[] = [
  { to: '/search', label: 'Search', Icon: Search },
  { to: '/home', label: 'Home', Icon: House },
  { to: '/new', label: 'New', Icon: LayoutGrid },
  { to: '/radio', label: 'Radio', Icon: Radio },
];

/** The left glass sidebar (report §1). Library sections arrive with sign-in in phase 5. */
export function Sidebar() {
  const drawerOpen = useUi((s) => s.drawerOpen);
  const setDrawerOpen = useUi((s) => s.setDrawerOpen);
  const close = () => setDrawerOpen(false);

  return (
    <>
      <nav className={styles.nav} data-open={drawerOpen} aria-label="Main">
        <div className={styles.header}>
          <PrefetchLink to="/home" className={styles.logo} aria-label="BitChord, Home" onClick={close}>
            <LogoMark size={22} className={styles.mark} />
            <span className={styles.wordmark}>BitChord</span>
          </PrefetchLink>
        </div>
        <div className={styles.scroll}>
          <ul className={styles.list}>
            {PRIMARY.map(({ to, label, Icon }) => (
              <li key={to}>
                <PrefetchNavLink to={to} className={styles.item} onClick={close}>
                  <Icon size={22} strokeWidth={1.9} className={styles.icon} />
                  <span>{label}</span>
                </PrefetchNavLink>
              </li>
            ))}
          </ul>
        </div>
        <div className={styles.footer}>
          <PrefetchNavLink to="/extension" className={styles.item} onClick={close}>
            <Puzzle size={22} strokeWidth={1.9} className={styles.icon} />
            <span>BitChord Extension</span>
          </PrefetchNavLink>
          <a className={styles.footerLink} href={config.androidAppUrl} target="_blank" rel="noreferrer">
            <span>Get the Android app</span>
            <ArrowUpRight size={14} strokeWidth={2} />
          </a>
        </div>
      </nav>
      {drawerOpen && <div className={styles.scrim} onClick={close} aria-hidden="true" />}
    </>
  );
}
