/**
 * Asks YouTube's image CDN for the size actually displayed (prompt §3).
 * Thumbnail URLs on lh3.googleusercontent.com and yt3.ggpht.com end in a
 * `=wNNN-hNNN…` option string; i.ytimg.com URLs are fixed sizes and pass
 * through unchanged.
 */
export function sizedArtwork(url: string, cssPx: number, aspect = 1): string {
  if (!/googleusercontent\.com|ggpht\.com/.test(url)) return url;
  const dpr = Math.min(typeof window === 'undefined' ? 2 : window.devicePixelRatio || 1, 3);
  const w = Math.round(cssPx * dpr);
  const h = Math.round((cssPx / aspect) * dpr);
  const base = url.replace(/=[^/=]*$/, '');
  return `${base}=w${w}-h${h}-l90-rj`;
}
