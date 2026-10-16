import { PageHeader } from '@/components/PageHeader';
import { PrefetchLink } from '@/components/PrefetchLink';
import styles from './NotFoundPage.module.css';

export default function NotFoundPage() {
  return (
    <>
      <PageHeader title="Page not found" />
      <p className={styles.body}>
        This page doesn’t exist. <PrefetchLink to="/home" className={styles.link}>Go to Home</PrefetchLink>
      </p>
    </>
  );
}
