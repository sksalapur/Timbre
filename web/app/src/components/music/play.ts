import type { MusicItem, Track } from '@/data/music/types';
import { usePlayer } from '@/state/player';

/** Plays one song (the queue arrives in phase 6), with the metadata the page already has. */
export function playTrack(track: Track | MusicItem, fallbackArtwork?: string | null) {
  const isTrack = 'videoId' in track;
  const videoId = isTrack ? track.videoId : track.id;
  void usePlayer.getState().playVideo(videoId, {
    title: track.title,
    artist: track.artists.map((a) => a.name).join(', '),
    artworkUrl: track.thumbnail ?? fallbackArtwork ?? null,
    durationSec: track.durationSec ?? 0,
  });
}
