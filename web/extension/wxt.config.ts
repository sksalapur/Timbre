import { readFileSync } from 'node:fs';
import { defineConfig } from 'wxt';
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
    permissions: ['storage'],
    // Host permissions for YouTube, Spotify and Discord arrive with the phases
    // that use them (prompt §4.2), so the install prompt only ever asks for
    // what the build actually does.
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
