import { ChevronLeft, ChevronRight, ListPlus, MoreHorizontal, Star } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Menu, MenuHeader, MenuItem, MenuSeparator } from '@/components/Menu';
import {
  useAddToPlaylist,
  useLikeStatus,
  usePlaylistOptions,
  useSetLiked,
  useSignedIn,
} from '@/data/music/queries';
import type { Track } from '@/data/music/types';

function PlaylistChoices({ videoId, onDone, onBack }: { videoId: string; onDone: () => void; onBack: () => void }) {
  const options = usePlaylistOptions(videoId, true);
  const add = useAddToPlaylist();
  return (
    <>
      <MenuItem icon={<ChevronLeft size={14} />} onSelect={onBack}>
        Back
      </MenuItem>
      <MenuSeparator />
      <MenuHeader>Add to Playlist</MenuHeader>
      {options.isPending && (
        <MenuItem disabled onSelect={() => {}}>
          Loading…
        </MenuItem>
      )}
      {options.isError && (
        <MenuItem disabled onSelect={() => {}}>
          Couldn’t load your playlists
        </MenuItem>
      )}
      {options.data?.length === 0 && (
        <MenuItem disabled onSelect={() => {}}>
          No playlists yet
        </MenuItem>
      )}
      {options.data?.map((option) => (
        <MenuItem
          key={option.playlistId}
          disabled={option.contains}
          trailing={option.contains ? 'Added' : undefined}
          onSelect={() => {
            add.mutate({ playlistId: option.playlistId, videoId });
            onDone();
          }}
        >
          {option.title}
        </MenuItem>
      ))}
    </>
  );
}

function TrackMenuItems({ track, onClose }: { track: Track; onClose: () => void }) {
  const [view, setView] = useState<'main' | 'playlists'>('main');
  const status = useLikeStatus(track.videoId);
  const setLiked = useSetLiked();
  if (view === 'playlists') {
    return <PlaylistChoices videoId={track.videoId} onDone={onClose} onBack={() => setView('main')} />;
  }
  const liked = status.data === 'LIKE';
  return (
    <>
      <MenuItem
        icon={<Star size={15} fill={liked ? 'currentColor' : 'none'} />}
        disabled={status.isPending}
        onSelect={() => {
          setLiked.mutate({ videoId: track.videoId, liked: !liked });
          onClose();
        }}
      >
        {liked ? 'Unfavorite' : 'Favorite'}
      </MenuItem>
      <MenuItem icon={<ListPlus size={15} />} trailing={<ChevronRight size={14} />} onSelect={() => setView('playlists')}>
        Add to Playlist
      </MenuItem>
    </>
  );
}

/**
 * The ⋯ button at the end of a track row (report §14), with the actions a
 * signed-in user has on a song: Favorite and Add to Playlist. Hidden signed
 * out, where there would be nothing in it yet (the queue actions arrive in
 * phase 6).
 */
export function TrackMenu({ track, className }: { track: Track; className?: string }) {
  const signedIn = useSignedIn();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const close = useCallback(() => setAnchor(null), []);
  if (!signedIn) return null;
  return (
    <>
      <button
        className={className}
        aria-label={`More options for ${track.title}`}
        aria-haspopup="menu"
        aria-expanded={anchor !== null}
        data-open={anchor !== null || undefined}
        onClick={(event) => {
          event.stopPropagation();
          setAnchor(anchor ? null : event.currentTarget);
        }}
        onDoubleClick={(event) => event.stopPropagation()}
      >
        <MoreHorizontal size={18} strokeWidth={2} />
      </button>
      {anchor && (
        <Menu anchor={anchor} onClose={close} label={track.title}>
          <TrackMenuItems track={track} onClose={close} />
        </Menu>
      )}
    </>
  );
}
