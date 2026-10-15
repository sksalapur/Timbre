import type { YTMusic } from 'youtubei.js/web';
import { getInnertube, getMusicClient, getSignedInnertube } from '@/playback/youtube/innertube';
import { LIBRARY_SECTIONS, LIKED_MUSIC_PLAYLIST, type LibrarySection } from './library';
import {
  mapAlbum,
  mapArtist,
  mapBrowse,
  mapCategories,
  mapHome,
  mapPlaylist,
  mapPlaylistTracks,
  mapSearch,
  mapShelves,
  mapSuggestions,
} from './mappers';
import type {
  AlbumPage,
  MusicItem,
  ArtistPage,
  BrowseLink,
  BrowsePage,
  CategoryGroup,
  PlaylistPage,
  SearchResults,
  Shelf,
  Suggestions,
  Track,
} from './types';

// The browse pages' requests, through YouTube.js's music namespace (prompt
// §5.1). Each returns a view model; continuations are passed back as opaque
// handles, so pages never see a YouTube.js object.

export interface Paged<T> {
  data: T;
  /** Fetches the next page; null when there isn't one. */
  next: (() => Promise<Paged<T>>) | null;
}

function homePage(feed: YTMusic.HomeFeed): Paged<Shelf[]> {
  return {
    data: mapHome(feed),
    next: feed.has_continuation ? async () => homePage(await feed.getContinuation()) : null,
  };
}

export async function fetchHome(): Promise<Paged<Shelf[]>> {
  const yt = await getMusicClient();
  return homePage(await yt.music.getHomeFeed());
}

/** The New page: YouTube Music's Explore, minus its podcast and mood carousels (mappers drop those). */
export async function fetchExplore(): Promise<Shelf[]> {
  const yt = await getMusicClient();
  return mapShelves((await yt.music.getExplore()).sections);
}

/** Moods & genres, read raw because YouTube.js drops the tiles' colours. */
export async function fetchCategories(): Promise<CategoryGroup[]> {
  const yt = await getInnertube();
  const response = await yt.actions.execute('/browse', { browseId: 'FEmusic_moods_and_genres', client: 'YTMUSIC' });
  return mapCategories(response.data);
}

export async function fetchSearch(query: string): Promise<SearchResults> {
  const yt = await getMusicClient();
  return mapSearch(await yt.music.search(query));
}

export async function fetchSuggestions(input: string): Promise<Suggestions> {
  const yt = await getInnertube();
  return mapSuggestions(await yt.music.getSearchSuggestions(input));
}

export async function fetchAlbum(id: string): Promise<AlbumPage> {
  const yt = await getMusicClient();
  return mapAlbum(id, await yt.music.getAlbum(id));
}

export async function fetchArtist(id: string): Promise<ArtistPage> {
  const yt = await getMusicClient();
  return mapArtist(id, await yt.music.getArtist(id));
}

function playlistTracks(page: YTMusic.Playlist): Paged<Track[]> {
  return {
    data: mapPlaylistTracks(page.items),
    next: page.has_continuation ? async () => playlistTracks(await page.getContinuation()) : null,
  };
}

/** The header and first page of tracks; `more` fetches the rest page by page. */
export async function fetchPlaylist(id: string): Promise<{ page: PlaylistPage; more: Paged<Track[]>['next'] }> {
  const yt = await getMusicClient();
  const playlist = await yt.music.getPlaylist(id);
  return { page: mapPlaylist(id, playlist), more: playlistTracks(playlist).next };
}

/** See All, moods and genres: whatever the browse id renders as. */
export async function fetchBrowse({ browseId, params }: BrowseLink): Promise<BrowsePage> {
  const yt = await getMusicClient();
  const { YTNodes } = await import('youtubei.js/web');
  const response = await yt.actions.execute('/browse', {
    browseId,
    ...(params && { params }),
    client: 'YTMUSIC',
    parse: true,
  });
  const memo = response.contents_memo;
  const sections = [
    ...(memo?.getType(YTNodes.Grid) ?? []),
    ...(memo?.getType(YTNodes.MusicCarouselShelf) ?? []),
    ...(memo?.getType(YTNodes.MusicShelf) ?? []),
    ...(memo?.getType(YTNodes.MusicPlaylistShelf) ?? []),
  ];
  const header = memo?.getType(YTNodes.MusicHeader)?.[0] ?? (response.header as unknown);
  return mapBrowse({ header, sections }, '');
}

// ---- Signed in (prompt §5.3) ------------------------------------------------------

type Raw = Record<string, unknown>;

/** Every object stored under [key], anywhere inside a raw response. */
function collect(root: unknown, key: string, out: Raw[] = []): Raw[] {
  if (Array.isArray(root)) for (const item of root) collect(item, key, out);
  else if (root && typeof root === 'object') {
    for (const [k, v] of Object.entries(root)) {
      if (k === key && v && typeof v === 'object' && !Array.isArray(v)) out.push(v as Raw);
      else collect(v, key, out);
    }
  }
  return out;
}

function runsText(node: unknown): string {
  const n = node as { runs?: { text?: string }[]; simpleText?: string } | undefined;
  return n?.runs?.map((r) => r.text ?? '').join('') ?? n?.simpleText ?? '';
}

/** One library page, a page of it at a time: grids continue as grids, song lists as song lists. */
export async function fetchLibrary(section: LibrarySection): Promise<Paged<BrowsePage>> {
  const yt = await getSignedInnertube();
  const { YTNodes } = await import('youtubei.js/web');
  const { title, browseId } = LIBRARY_SECTIONS[section];
  const response = await yt.actions.execute('/browse', { browseId, client: 'YTMUSIC', parse: true });
  const memo = response.contents_memo;
  const grids = memo?.getType(YTNodes.Grid) ?? [];
  const lists = [...(memo?.getType(YTNodes.MusicShelf) ?? []), ...(memo?.getType(YTNodes.MusicPlaylistShelf) ?? [])];
  const carousels = memo?.getType(YTNodes.MusicCarouselShelf) ?? [];
  const page = mapBrowse({ sections: [...grids, ...lists, ...carousels] }, title);
  // History is several lists ("Today", "Yesterday"); the rest is one grid or one list.
  const token = (grids[0]?.continuation ?? (lists.length === 1 ? lists[0]?.continuation : null)) || null;
  return { data: { ...page, title }, next: token ? () => libraryContinuation(token, title) : null };
}

async function libraryContinuation(token: string, title: string): Promise<Paged<BrowsePage>> {
  const yt = await getSignedInnertube();
  const response = await yt.actions.execute('/browse', { continuation: token, client: 'YTMUSIC', parse: true });
  const contents = response.continuation_contents as unknown as
    | { type: string; items?: unknown[]; contents?: unknown[]; continuation?: string | null }
    | undefined;
  const section =
    contents?.type === 'GridContinuation'
      ? { type: 'Grid', items: contents.items ?? [] }
      : { type: 'MusicShelf', contents: contents?.contents ?? [] };
  const next = contents?.continuation || null;
  return { data: mapBrowse({ sections: [section] }, title), next: next ? () => libraryContinuation(next, title) : null };
}

/** The user's playlists for the sidebar, in YouTube's order (most recent activity first). */
export async function fetchLibraryPlaylists(): Promise<MusicItem[]> {
  const { data } = await fetchLibrary('playlists');
  return data.grid.filter((item) => item.kind === 'playlist');
}

export type LikeStatus = 'LIKE' | 'DISLIKE' | 'INDIFFERENT';

/** The thumbs state YouTube Music shows on its player for this track. */
export async function fetchLikeStatus(videoId: string): Promise<LikeStatus> {
  const yt = await getSignedInnertube();
  const response = await yt.actions.execute('/next', { videoId, client: 'YTMUSIC' });
  const button = collect((response.data as Raw).playerOverlays, 'likeButtonRenderer')[0];
  const status = button?.likeStatus;
  return status === 'LIKE' || status === 'DISLIKE' ? status : 'INDIFFERENT';
}

/** Likes a track (it joins Liked Music) or takes the like back. */
export async function setTrackLiked(videoId: string, liked: boolean): Promise<void> {
  const yt = await getSignedInnertube();
  await yt.actions.execute(liked ? '/like/like' : '/like/removelike', { target: { videoId }, client: 'YTMUSIC' });
}

/** Adds an album or playlist to the library, or removes it: YouTube saves both by liking the list. */
export async function setListSaved(playlistId: string, saved: boolean): Promise<void> {
  const yt = await getSignedInnertube();
  await yt.actions.execute(saved ? '/like/like' : '/like/removelike', { target: { playlistId }, client: 'YTMUSIC' });
}

export interface PlaylistOption {
  playlistId: string;
  title: string;
}

/**
 * The playlists a track can be added to, as YouTube Music's "Save to
 * playlist" lists them. YouTube doesn't say which already hold the track
 * (adding is deduplicated instead), and its "Liked Music" entry is really a
 * like, which Favorite already does, so it's left out.
 */
export async function fetchPlaylistOptions(videoId: string): Promise<PlaylistOption[]> {
  const yt = await getSignedInnertube();
  const response = await yt.actions.execute('/playlist/get_add_to_playlist', { videoIds: [videoId], client: 'YTMUSIC' });
  return collect(response.data, 'playlistAddToOptionRenderer')
    .map((option) => ({
      playlistId: typeof option.playlistId === 'string' ? option.playlistId : '',
      title: runsText(option.title),
    }))
    .filter((option) => option.playlistId && option.playlistId !== LIKED_MUSIC_PLAYLIST && option.title);
}

export async function addToPlaylist(playlistId: string, videoId: string): Promise<void> {
  const yt = await getSignedInnertube();
  await yt.actions.execute('/browse/edit_playlist', {
    playlistId: playlistId.replace(/^VL/, ''),
    actions: [{ action: 'ACTION_ADD_VIDEO', addedVideoId: videoId, dedupeOption: 'DEDUPE_OPTION_SKIP' }],
    client: 'YTMUSIC',
  });
}
