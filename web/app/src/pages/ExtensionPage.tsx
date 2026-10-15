import { PageHeader } from '@/components/PageHeader';
import { useExtension } from '@/extension/useExtension';
import styles from './ExtensionPage.module.css';

const FEATURES = [
  ['YouTube Music playback', 'Streams straight from YouTube to your browser, from your own connection.'],
  ['Your YouTube Music library', 'Uses the Google account you’re already signed into. BitChord never sees your password or cookies.'],
  ['Spotify', 'Import your liked songs and playlists, and show Canvas videos behind the artwork.'],
  ['Discord', 'Show what you’re listening to on your Discord profile.'],
] as const;

export default function ExtensionPage() {
  const status = useExtension();

  return (
    <>
      <PageHeader title="BitChord Extension" />
      <div className={styles.body}>
        <section className={styles.group} aria-live="polite">
          <div className={styles.row}>
            <span>Status</span>
            <span className={styles.value}>
              {status.state === 'checking' && 'Checking…'}
              {status.state === 'missing' && 'Not installed'}
              {status.state === 'present' && (
                <>
                  <span className={styles.dot} aria-hidden="true" />
                  Installed · v{status.extension.info.version}
                </>
              )}
            </span>
          </div>
          {status.state === 'present' && (
            <div className={styles.row}>
              <span>Connection</span>
              <span className={styles.value}>
                {status.extension.bridge.transport === 'external' ? 'Direct' : 'Page relay'} ·{' '}
                {status.extension.info.browser === 'firefox' ? 'Firefox' : 'Chromium'}
              </span>
            </div>
          )}
        </section>
        <p className={styles.footer}>
          The extension does the networking and sign-in work a web page isn’t allowed to do. Addons and most lyric
          providers work without it.
        </p>

        <h2 className={styles.sectionHeader}>What it adds</h2>
        <section className={styles.group}>
          {FEATURES.map(([title, detail]) => (
            <div key={title} className={styles.feature}>
              <span className={styles.featureTitle}>{title}</span>
              <span className={styles.featureDetail}>{detail}</span>
            </div>
          ))}
        </section>
      </div>
    </>
  );
}
