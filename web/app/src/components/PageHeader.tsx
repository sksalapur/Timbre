import styles from './PageHeader.module.css';

/** The page title row (report §1, `.header-nav`). */
export function PageHeader({ title }: { title: string }) {
  return (
    <div className={styles.header}>
      <h1 className={styles.title}>{title}</h1>
    </div>
  );
}
