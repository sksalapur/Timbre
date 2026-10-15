/**
 * The web app origins this extension serves. Every message is checked against
 * this list, and it becomes externally_connectable (Chromium) and the relay's
 * content-script matches (Firefox).
 *
 * Set BITCHORD_SITE_ORIGINS (comma-separated) for a production build. The
 * production domain hasn't been chosen yet, so the default is development only.
 */
export const SITE_ORIGINS: string[] = (process.env.BITCHORD_SITE_ORIGINS ?? 'http://localhost:5173')
  .split(',')
  .map((o) => o.trim().replace(/\/+$/, ''))
  .filter(Boolean);

/**
 * Match patterns can't carry a port, so `http://localhost:5173` becomes
 * `http://localhost/*`. The exact origin, port included, is still enforced at
 * runtime against SITE_ORIGINS.
 */
export function matchPatternsFor(origins: string[]): string[] {
  return [...new Set(origins.map((o) => {
    const url = new URL(o);
    return `${url.protocol}//${url.hostname}/*`;
  }))];
}
