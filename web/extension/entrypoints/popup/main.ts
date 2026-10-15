import './style.css';

declare const __SITE_ORIGINS__: string[];

interface Integration {
  name: string;
  detail: string;
}

// Each integration gains its connected state, account and sign-out button in
// the phase that builds it (YouTube Music in 5, Spotify and Discord in 9).
const INTEGRATIONS: Integration[] = [
  { name: 'YouTube Music', detail: 'Not connected' },
  { name: 'Spotify', detail: 'Not connected' },
  { name: 'Discord', detail: 'Not connected' },
];

document.getElementById('version')!.textContent = `v${browser.runtime.getManifest().version}`;

const site = document.getElementById('site') as HTMLAnchorElement;
site.href = __SITE_ORIGINS__[0] ?? '#';

const list = document.getElementById('integrations')!;
for (const { name, detail } of INTEGRATIONS) {
  const row = document.createElement('li');
  row.className = 'row';
  const label = document.createElement('span');
  label.className = 'label';
  label.textContent = name;
  const value = document.createElement('span');
  value.className = 'value';
  value.textContent = detail;
  row.append(label, value);
  list.append(row);
}
