import { readFileSync } from 'node:fs';
import { defineConfig } from 'wxt';
import { LYRIC_ORIGINS, patternOf } from './lib/lyricHosts';
import { matchPatternsFor, SITE_ORIGINS } from './site.config';

/** BITCHORD_STORE_BUILD=1 for a Chrome Web Store upload. */
const STORE_BUILD = process.env.BITCHORD_STORE_BUILD === '1';

const devPublicKey = readFileSync(new URL('./dev-key/public-key.txt', import.meta.url), 'utf8').trim();

export default defineConfig({
  manifestVersion: 3,
  vite: () => ({
    define: {
      // Checked against every sender at runtime (entrypoints/background.ts).
      __SITE_ORIGINS__: JSON.stringify(SITE_ORIGINS),
    },
  }),
  manifest: ({ browser }) => ({
    name: 'BitChord',
    description: 'Connects the BitChord web player to YouTube Music, Spotify and Discord.',
    homepage_url: SITE_ORIGINS[0],
    // `cookies` reads the YouTube session for signed-in requests (phase 5);
    // the cookies themselves never leave the extension (lib/ytAuth.ts).
    permissions: ['storage', 'declarativeNetRequest', 'cookies'],
    // YouTube now (phases 3 and 5); Spotify and Discord arrive with the
    // phases that use them, so the install prompt only asks for what the
    // build does. The site's own origins are listed because header rules need
    // host access to the initiator as well as the target.
    host_permissions: [
      'https://music.youtube.com/*',
      'https://www.youtube.com/*',
      'https://*.youtube.com/*',
      'https://*.googlevideo.com/*',
      ...matchPatternsFor(SITE_ORIGINS),
    ],
    // Lyric providers the page can't reach itself, each granted once by the
    // user on the extension's own page (lib/lyricsFetch.ts, grant.html).
    optional_host_permissions: LYRIC_ORIGINS.map(patternOf),
    ...(browser === 'firefox'
      ? {
          browser_specific_settings: {
            gecko: {
              id: 'extension@bitchord.kushagrasingh.in',
              strict_min_version: '128.0',
              data_collection_permissions: { required: ['none'] },
            },
          },
        }
      : {
          // Chromium pages message the extension directly.
          externally_connectable: { matches: matchPatternsFor(SITE_ORIGINS) },
          // Pins the unpacked extension's id so the page can address it
          // (scripts/gen-dev-key.mjs). The Web Store assigns its own id and
          // refuses a `key`, so store builds leave it out.
          ...(!STORE_BUILD && { key: devPublicKey }),
        }),
  }),
  hooks: {
    // Firefox only: the relay content script runs on the site's origins.
    'build:manifestGenerated': (wxt, manifest) => {
      for (const script of manifest.content_scripts ?? []) {
        if (script.js?.some((file) => file.includes('relay'))) {
          script.matches = matchPatternsFor(SITE_ORIGINS);
        }
      }
    },
  },
});
