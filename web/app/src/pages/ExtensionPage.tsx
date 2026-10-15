import { ChevronRight } from 'lucide-react';
import { useState, type ComponentType } from 'react';
import { FaChrome, FaFirefoxBrowser } from 'react-icons/fa';
import { SiDiscord, SiSpotify, SiYoutubemusic } from 'react-icons/si';
import type { Capability } from '@bitchord/shared/bridge';
import { PageHeader } from '@/components/PageHeader';
import { config } from '@/config';
import { useExtension, type ExtensionStatus } from '@/extension/useExtension';
import styles from './ExtensionPage.module.css';

type Browser = 'chrome' | 'firefox';

interface Download {
  browser: Browser;
  title: string;
  detail: string;
  href: string;
  Icon: ComponentType<{ size?: number; className?: string }>;
  steps: string[];
}

const DOWNLOADS: Download[] = [
  {
    browser: 'chrome',
    title: 'Download for Chrome',
    detail: 'Chrome, Edge, Brave and other Chromium browsers',
    href: config.extensionChromeUrl,
    Icon: FaChrome,
    steps: [
      'Unzip the download into a folder you’ll keep.',
      'Open chrome://extensions and turn on Developer mode.',
      'Choose Load unpacked and select that folder.',
      'Come back to this page: the status above turns to Installed.',
    ],
  },
  {
    browser: 'firefox',
    title: 'Download for Firefox',
    detail: 'Firefox 128 or newer',
    href: config.extensionFirefoxUrl,
    Icon: FaFirefoxBrowser,
    steps: [
      'Open about:debugging#/runtime/this-firefox.',
      'Choose Load Temporary Add-on and select the downloaded zip.',
      'Firefox removes temporary add-ons when it quits; a signed version is coming.',
    ],
  },
];

/** The browser this page is open in, to put its download first. */
const currentBrowser: Browser = /firefox/i.test(navigator.userAgent) ? 'firefox' : 'chrome';

interface Integration {
  name: string;
  Icon: ComponentType<{ size?: number; className?: string }>;
  color: string;
  capability: Capability;
  /** What the row says when the extension serves this integration. */
  ready: string;
}

const INTEGRATIONS: Integration[] = [
  { name: 'YouTube Music', Icon: SiYoutubemusic, color: '#ff0033', capability: 'yt', ready: 'Playback ready · not signed in' },
  { name: 'Spotify', Icon: SiSpotify, color: '#1ed760', capability: 'spotify', ready: 'Not connected' },
  { name: 'Discord', Icon: SiDiscord, color: '#5865f2', capability: 'discord', ready: 'Off' },
];

/** One integration's state, honest about what this extension version can do. */
function integrationStatus(status: ExtensionStatus, integration: Integration): string {
  if (status.state === 'checking') return 'Checking…';
  if (status.state === 'missing') return 'Needs the extension';
  return status.extension.info.capabilities.includes(integration.capability)
    ? integration.ready
    : 'Not available in this version yet';
}

export default function ExtensionPage() {
  const status = useExtension();
  const [chosen, setChosen] = useState<Browser | null>(null);
  const downloads = [...DOWNLOADS].sort((a, b) => Number(b.browser === currentBrowser) - Number(a.browser === currentBrowser));
  const steps = DOWNLOADS.find((d) => d.browser === chosen)?.steps;

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

        <h2 className={styles.sectionHeader}>Get the extension</h2>
        <section className={styles.group}>
          {downloads.map(({ browser, title, detail, href, Icon }) => (
            <a
              key={browser}
              className={styles.download}
              href={href}
              download
              onClick={() => setChosen(browser)}
            >
              <Icon size={28} className={styles.browserIcon} />
              <span className={styles.downloadText}>
                <span className={styles.downloadTitle}>{title}</span>
                <span className={styles.downloadDetail}>
                  {detail}
                  {browser === currentBrowser && ' · This browser'}
                </span>
              </span>
              <ChevronRight size={16} strokeWidth={2.4} className={styles.chevron} />
            </a>
          ))}
        </section>
        {steps ? (
          <ol className={styles.steps}>
            {steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
        ) : (
          <p className={styles.footer}>Pick your browser to download the extension and see how to install it.</p>
        )}

        <h2 className={styles.sectionHeader}>Integrations</h2>
        <section className={styles.group}>
          {INTEGRATIONS.map((integration) => (
            <div key={integration.name} className={styles.integration}>
              <span className={styles.integrationIcon} style={{ backgroundColor: integration.color }}>
                <integration.Icon size={15} />
              </span>
              <span className={styles.integrationName}>{integration.name}</span>
              <span className={styles.value}>{integrationStatus(status, integration)}</span>
            </div>
          ))}
        </section>
        <p className={styles.footer}>
          Sign-ins stay inside the extension: this page only ever sees results, never your cookies or tokens.
        </p>
      </div>
    </>
  );
}
