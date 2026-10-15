// Copies the zipped builds into the web app's public folder under stable
// names, for the Extension page's download buttons:
//   web/app/public/downloads/bitchord-extension-chrome.zip
//   web/app/public/downloads/bitchord-extension-firefox.zip
//
//   corepack pnpm --dir web/extension package   (zips, then runs this)

import { copyFileSync, mkdirSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const output = fileURLToPath(new URL('../.output/', import.meta.url));
const target = fileURLToPath(new URL('../../app/public/downloads/', import.meta.url));
mkdirSync(target, { recursive: true });

for (const browser of ['chrome', 'firefox']) {
  const newest = readdirSync(output)
    .filter((name) => name.endsWith(`-${browser}.zip`))
    .map((name) => ({ name, time: statSync(join(output, name)).mtimeMs }))
    .sort((a, b) => b.time - a.time)[0];
  if (!newest) throw new Error(`No ${browser} zip in ${output}; run wxt zip first`);
  copyFileSync(join(output, newest.name), join(target, `bitchord-extension-${browser}.zip`));
  console.log(`${newest.name} -> app/public/downloads/bitchord-extension-${browser}.zip`);
}
