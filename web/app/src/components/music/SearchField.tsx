import { Search, X } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { useSuggestions } from '@/data/music/queries';
import type { MusicItem } from '@/data/music/types';
import { Artwork } from './Artwork';
import { hrefFor } from './links';
import { playTrack } from './play';
import styles from './SearchField.module.css';

type Option = { kind: 'query'; text: string; typed: string; rest: string } | { kind: 'item'; item: MusicItem };

/**
 * The centred search field and its suggestions (report §6): a pill field, and
 * a glass dropdown of completions, then artist, album and song suggestions.
 * Arrow keys move through it; Enter picks; Escape closes.
 */
export function SearchField({ initial, onSearch }: { initial: string; onSearch: (query: string) => void }) {
  const [value, setValue] = useState(initial);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const input = useRef<HTMLInputElement>(null);
  const listId = useId();
  const navigate = useNavigate();
  const suggestions = useSuggestions(open ? value : '');

  useEffect(() => setValue(initial), [initial]);

  const options: Option[] = [
    ...(suggestions.data?.queries ?? []).map((q) => ({ kind: 'query' as const, ...q })),
    ...(suggestions.data?.items ?? []).map((item) => ({ kind: 'item' as const, item })),
  ];
  const showList = open && value.trim().length > 0 && options.length > 0;

  const pick = (option: Option | undefined) => {
    setOpen(false);
    setActive(-1);
    if (!option) return onSearch(value.trim());
    if (option.kind === 'query') {
      setValue(option.text);
      return onSearch(option.text);
    }
    const href = hrefFor(option.item);
    if (href) navigate(href);
    else playTrack(option.item);
  };

  return (
    <div className={styles.wrapper}>
      <form
        className={styles.form}
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          pick(active >= 0 ? options[active] : undefined);
        }}
      >
        <Search size={15} strokeWidth={2.4} className={styles.icon} aria-hidden="true" />
        <input
          ref={input}
          className={styles.field}
          type="search"
          value={value}
          placeholder="Artists, Songs, Lyrics, and More"
          aria-label="Search"
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
          autoComplete="off"
          spellCheck={false}
          onChange={(e) => {
            setValue(e.target.value);
            setOpen(true);
            setActive(-1);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 120)}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') {
              e.preventDefault();
              setActive((i) => Math.min(i + 1, options.length - 1));
            } else if (e.key === 'ArrowUp') {
              e.preventDefault();
              setActive((i) => Math.max(i - 1, -1));
            } else if (e.key === 'Escape') {
              setOpen(false);
            }
          }}
        />
        {value && (
          <button
            type="button"
            className={styles.clear}
            aria-label="Clear"
            onClick={() => {
              setValue('');
              input.current?.focus();
            }}
          >
            <X size={12} strokeWidth={3} />
          </button>
        )}
      </form>
      {showList && (
        <ul id={listId} className={styles.list} role="listbox">
          {options.map((option, i) => (
            <li
              key={option.kind === 'query' ? `q-${option.text}` : `i-${option.item.id}`}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              className={option.kind === 'query' ? styles.hint : styles.lockup}
              data-active={i === active || undefined}
              onMouseDown={(e) => e.preventDefault()}
              onMouseEnter={() => setActive(i)}
              onClick={() => pick(option)}
            >
              {option.kind === 'query' ? (
                <>
                  <Search size={13} strokeWidth={2.4} aria-hidden="true" />
                  <span className={styles.suggestion}>
                    <span className={styles.typed}>{option.typed}</span>
                    <span>{option.rest}</span>
                  </span>
                </>
              ) : (
                <>
                  <div className={styles.thumb}>
                    <Artwork
                      src={option.item.thumbnail}
                      displayWidth={46}
                      radius={option.item.kind === 'artist' ? '50%' : 'var(--radius-artwork-small)'}
                    />
                  </div>
                  <div className={styles.lockupText}>
                    <span className={styles.lockupTitle}>{option.item.title}</span>
                    <span className={styles.lockupSubtitle}>{option.item.subtitle || kindLabel(option.item.kind)}</span>
                  </div>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function kindLabel(kind: MusicItem['kind']): string {
  return { song: 'Song', video: 'Video', album: 'Album', playlist: 'Playlist', 'spotify-playlist': 'Spotify Playlist', artist: 'Artist', episode: 'Episode', podcast: 'Podcast', unknown: '' }[kind];
}
