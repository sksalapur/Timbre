import { useQuery } from '@tanstack/react-query';
import { lyricsGet, urlWith } from '@/lyrics/http';
import { appleWebToken } from './webToken';

/**
 * What Apple Music's artist page shows at the top, as the phone reads it
 * (app/…/data/canvas/AppleArtistArt.kt): the hero photograph, usually the
 * artist's title logo drawn over it as a separate transparent image, and
 * Apple's own colours for that photograph. Most artists have no logo.
 */
export interface AppleArtistArt {
  heroUrl: string;
  /** Width over height of the photograph. */
  heroAspect: number;
  /** A transparent PNG, or null for the many artists without one. */
  logoUrl: string | null;
  /** Width over height, so the logo can be sized before it decodes. */
  logoAspect: number;
  /** Apple's `bgColor` for the photograph: what the page is tinted with. */
  background: string;
  /** Apple's `textColor1`. */
  accent: string;
  /** The colour Apple fills the artist's Play button with; absent for some artists. */
  keyColor: string | null;
}

const HERO_WIDTH = 1200;
const LOGO_WIDTH = 1000;

type Json = Record<string, unknown>;
const obj = (v: unknown): Json | null => (v && typeof v === 'object' && !Array.isArray(v) ? (v as Json) : null);
const str = (v: unknown): string => (typeof v === 'string' ? v : '');
const num = (v: unknown): number => (typeof v === 'number' && Number.isFinite(v) ? v : 0);
function parse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** Case, accents and punctuation are not what makes two names different (AppleArtistArt.kt's normalized()). */
function normalized(name: string): string {
  return name.toLowerCase().normalize('NFD').replace(/[^\p{L}\p{N}]/gu, '');
}

/** Apple's colours are six hex digits with no prefix. */
function hex(value: unknown): string | null {
  const v = str(value);
  return /^[0-9a-f]{6}$/i.test(v) ? `#${v.toLowerCase()}` : null;
}

/** Fills Apple's image URL template ({w}x{h}{c}.{f}). */
function fill(template: unknown, w: number, h: number, format: string): string | null {
  const url = str(template);
  if (!url.includes('{w}')) return null;
  return url.replace('{w}', String(w)).replace('{h}', String(h)).replace('{c}', 'bb').replace('{f}', format);
}

/** The first object anywhere in [node] that has an `artistLogo` key: the artist page's header. */
function findHeader(node: unknown): Json | null {
  if (Array.isArray(node)) {
    for (const child of node) {
      const found = findHeader(child);
      if (found) return found;
    }
    return null;
  }
  const o = obj(node);
  if (!o) return null;
  if ('artistLogo' in o) return o;
  for (const child of Object.values(o)) {
    const found = findHeader(child);
    if (found) return found;
  }
  return null;
}

/** The header art in an artist page's hydration JSON; null when it has none. */
export function parseArtistPage(html: string): AppleArtistArt | null {
  const marker = html.indexOf('id="serialized-server-data"');
  if (marker < 0) return null;
  const start = html.indexOf('>', marker) + 1;
  const end = html.indexOf('</script>', start);
  if (start <= 0 || end < 0) return null;
  const header = findHeader(parse(html.slice(start, end)));
  if (!header) return null;

  // Absent for most artists: the key is there and null.
  const logo = obj(obj(header.artistLogo)?.dictionary);
  const hero = obj(obj(header.artwork)?.dictionary) ?? obj(obj(header.colorBackdropArtwork)?.dictionary);
  if (!hero) return null;
  const heroW = num(hero.width);
  const heroH = num(hero.height);
  if (!heroW || !heroH) return null;
  const heroUrl = fill(hero.url, HERO_WIDTH, Math.round((HERO_WIDTH * heroH) / heroW), 'jpg');
  if (!heroUrl) return null;

  const logoW = num(logo?.width);
  const logoH = num(logo?.height);
  const logoUrl = logo && logoW && logoH ? fill(logo.url, LOGO_WIDTH, Math.round((LOGO_WIDTH * logoH) / logoW), 'png') : null;

  const background = hex(hero.bgColor);
  if (!background) return null;
  return {
    heroUrl,
    heroAspect: heroW / heroH,
    logoUrl,
    logoAspect: logoUrl ? logoW / logoH : 1,
    background,
    accent: hex(hero.textColor1) ?? background,
    keyColor: hex(header.keyColor),
  };
}

/**
 * The artist's music.apple.com page, found by exact name. The phone asks the
 * iTunes search API; a page may not be allowed to read it, so Apple's own
 * catalogue search (the web player's token, through the extension) stands in.
 * A search happily returns somebody else, so only an answer with the very
 * name asked for is taken. Null when there is no such artist; throws when
 * Apple couldn't be asked, so a miss from a network hiccup isn't remembered.
 */
async function artistPageUrl(name: string): Promise<string | null> {
  const key = normalized(name);
  const itunes = await lyricsGet(urlWith('https://itunes.apple.com/search', { term: name, entity: 'musicArtist', limit: 5 }));
  if (itunes) {
    const results = (obj(parse(itunes))?.results as Json[] | undefined) ?? [];
    const hit = results.find((r) => normalized(str(r.artistName)) === key);
    if (!hit) return null;
    // The link carries the artist's slug; the bare id answers with a redirect to it.
    const link = str(hit.artistLinkUrl).split('?')[0];
    return link || (num(hit.artistId) ? `https://music.apple.com/us/artist/${num(hit.artistId)}` : null);
  }

  const token = await appleWebToken();
  if (!token) throw new Error('Apple Music is out of reach');
  const search = await lyricsGet(
    urlWith('https://amp-api.music.apple.com/v1/catalog/us/search', { term: name, types: 'artists', limit: 5, l: 'en-US' }),
    { route: 'extension', headers: { Authorization: `Bearer ${token}` } },
  );
  if (!search) throw new Error('Apple Music search failed');
  const artists = (obj(obj(obj(parse(search))?.results)?.artists)?.data as Json[] | undefined) ?? [];
  const hit = artists.find((a) => normalized(str(obj(a.attributes)?.name)) === key);
  return hit ? str(obj(hit.attributes)?.url).split('?')[0] || null : null;
}

export async function fetchAppleArtistArt(name: string): Promise<AppleArtistArt | null> {
  if (!normalized(name)) return null;
  const page = await artistPageUrl(name);
  if (!page) return null;
  const html = await lyricsGet(page, { route: 'extension' });
  if (!html) throw new Error('Apple Music artist page out of reach');
  return parseArtistPage(html);
}

/**
 * Apple's header art for [name]. Null until (and unless) it arrives: the
 * YouTube header stands in meanwhile, and stays when Apple has nothing.
 */
export function useAppleArtistArt(name: string | null | undefined): AppleArtistArt | null {
  const query = useQuery({
    queryKey: ['apple', 'artist-art', normalized(name ?? '')],
    queryFn: () => fetchAppleArtistArt(name!),
    enabled: !!name && !!normalized(name),
    staleTime: Infinity,
    gcTime: 60 * 60_000,
    retry: false,
  });
  return query.data ?? null;
}
