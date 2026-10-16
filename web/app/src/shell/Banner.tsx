import { X } from 'lucide-react';
import type { ReactNode } from 'react';
import styles from './Banner.module.css';

interface BannerProps {
  title: string;
  detail: string;
  action: ReactNode;
  onDismiss?: () => void;
}

/**
 * The full-width key-colour band Apple pins under the player for signed-out
 * visitors (report §1). An invitation, not an error.
 */
export function Banner({ title, detail, action, onDismiss }: BannerProps) {
  return (
    <div className={styles.banner} role="region" aria-label={title}>
      <div className={styles.text}>
        <p className={styles.title}>{title}</p>
        <p className={styles.detail}>{detail}</p>
      </div>
      <div className={styles.action}>{action}</div>
      {onDismiss && (
        <button className={styles.close} onClick={onDismiss} aria-label="Dismiss">
          <X size={16} strokeWidth={2.4} />
        </button>
      )}
    </div>
  );
}

export const bannerButtonClass = styles.button;
