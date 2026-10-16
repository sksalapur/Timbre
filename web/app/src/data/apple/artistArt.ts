import { useQuery } from '@tanstack/react-query';
import { lyricsGet, urlWith } from '@/lyrics/http';
import { normalized, num, obj, parse, parseArtistPage, str, type AppleArtistArt, type Json } from './artistPage';
import { appleWebToken } from './webToken';

export type { AppleArtistArt };

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
