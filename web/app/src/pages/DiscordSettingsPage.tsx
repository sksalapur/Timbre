import { PageHeader } from '@/components/PageHeader';
import { useDiscord } from '@/extension/useDiscord';
import { DiscordIntegration, DiscordSettings } from './IntegrationSettings';
import styles from './ExtensionPage.module.css';

/** Discord rich presence (DiscordScreen on the phone): the account, then how the card reads. */
export default function DiscordSettingsPage() {
  const status = useDiscord();
  return (
    <>
      <PageHeader title="Discord" />
      <div className={styles.body}>
        <section className={styles.group}>
          <div className={styles.integration}>
            <span className={styles.integrationName}>Account</span>
            <DiscordIntegration />
          </div>
        </section>
        {status.state === 'ready' && !status.discord.connected && (
          <p className={styles.footer}>
            Connect opens a page from the extension that reads your Discord session from a discord.com tab. The web player never
            sees it. This uses your account’s own token, which Discord’s terms don’t allow.
          </p>
        )}
        {status.state === 'ready' && status.discord.connected && (
          <>
            <h2 className={styles.sectionHeader}>Presence</h2>
            <DiscordSettings />
          </>
        )}
      </div>
    </>
  );
}
