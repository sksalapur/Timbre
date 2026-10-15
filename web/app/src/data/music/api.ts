import type { YTMusic } from 'youtubei.js/web';
import { getInnertube } from '@/playback/youtube/innertube';
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
  const yt = await getInnertube();
  return homePage(await yt.music.getHomeFeed());
}

/** The New page: YouTube Music's Explore, minus its podcast and mood carousels (mappers drop those). */
export async function fetchExplore(): Promise<Shelf[]> {
  const yt = await getInnertube();
  return mapShelves((await yt.music.getExplore()).sections);
}

/** Moods & genres, read raw because YouTube.js drops the tiles' colours. */
export async function fetchCategories(): Promise<CategoryGroup[]> {
  const yt = await getInnertube();
  const response = await yt.actions.execute('/browse', { browseId: 'FEmusic_moods_and_genres', client: 'YTMUSIC' });
  return mapCategories(response.data);
}

export async function fetchSearch(query: string): Promise<SearchResults> {
  const yt = await getInnertube();
  return mapSearch(await yt.music.search(query));
}

export async function fetchSuggestions(input: string): Promise<Suggestions> {
  const yt = await getInnertube();
  return mapSuggestions(await yt.music.getSearchSuggestions(input));
}

export async function fetchAlbum(id: string): Promise<AlbumPage> {
  const yt = await getInnertube();
  return mapAlbum(id, await yt.music.getAlbum(id));
}

export async function fetchArtist(id: string): Promise<ArtistPage> {
  const yt = await getInnertube();
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
  const yt = await getInnertube();
  const playlist = await yt.music.getPlaylist(id);
  return { page: mapPlaylist(id, playlist), more: playlistTracks(playlist).next };
}

/** See All, moods and genres: whatever the browse id renders as. */
export async function fetchBrowse({ browseId, params }: BrowseLink): Promise<BrowsePage> {
  const yt = await getInnertube();
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
