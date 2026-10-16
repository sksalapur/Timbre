import { useMemo, useState, type CSSProperties } from 'react';
import { useParams } from 'react-router';
import { PlayIcon } from '@/components/icons/Transport';
import { Artwork } from '@/components/music/Artwork';
import { browseHref } from '@/components/music/links';
import { playList } from '@/components/music/play';
import { Shelves, TrackShelf } from '@/components/music/ShelfView';
import { LoadError, NeedsExtension } from '@/components/music/States';
import { useAppleArtistArt, type AppleArtistArt } from '@/data/apple/artistArt';
import { useArtist, useYouTubeReady } from '@/data/music/queries';
import type { ArtworkColor } from '@/lib/artworkColor';
import { usePageTint } from '@/lib/usePageTint';
import { ArtistSkeleton } from '@/skeletons/PageSkeletons';
import styles from './ArtistPage.module.css';

function luminance(color: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16) / 255);
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!;
}

/**
 * The name, or Apple's title logo in its place (AppleArtistArt.kt). Until the
 * logo has drawn, the name stands in, so the header is never left empty; a
 * logo that fails to load leaves the name.
 */
function ArtistName({ name, apple }: { name: string; apple: AppleArtistArt | null }) {
  const logo = apple?.logoUrl ?? null;
  const [shown, setShown] = useState<string | null>(null);
  const logoShown = !!logo && shown === logo;
  return (
    <h1 className={styles.name} data-logo={logoShown || undefined}>
      {!logoShown && name}
      {logo && (
        <img
          key={logo}
          className={styles.logo}
          src={logo}
          alt={logoShown ? name : ''}
          hidden={!logoShown}
          // Its proportions are known before it decodes; the CSS caps its size.
          style={{ aspectRatio: String(apple!.logoAspect) }}
          decoding="async"
          onLoad={() => setShown(logo)}
        />
      )}
    </h1>
  );
}

/**
 * An artist (report §4): full-bleed hero with the name and Play, Top Songs,
 * then shelves. Where Apple Music has the artist, the page takes Apple's
 * header as the phone does: its photograph, its title logo over it, its
 * colour behind the whole page, and its colour on the Play button.
 */
export default function ArtistPage() {
  const id = decodeURIComponent(useParams().id ?? '');
  const { missing } = useYouTubeReady();
  const artist = useArtist(id);
  const apple = useAppleArtistArt(artist.data?.name);
  const tint = useMemo<ArtworkColor | null>(
    () => (apple ? { background: apple.background, dark: luminance(apple.background) < 0.6 } : null),
    [apple],
  );
  usePageTint(tint);

  if (missing) return <NeedsExtension />;
  if (artist.isError) return <LoadError error={artist.error} onRetry={() => void artist.refetch()} />;
  if (!artist.data) return <ArtistSkeleton />;

  const data = artist.data;
  const topSongs = { title: `${data.name}: Top Songs`, type: 'artist' as const, id: data.id };
  return (
    <>
      <header className={styles.hero}>
        <div className={styles.heroImage}>
          <Artwork src={data.heroImage} displayWidth={1200} aspect={1165 / 604} eager />
          {/* Apple's photograph fades in over YouTube's once it arrives. */}
          {apple && (
            <img
              key={apple.heroUrl}
              className={styles.appleHero}
              src={apple.heroUrl}
              alt=""
              decoding="async"
              onLoad={(e) => e.currentTarget.setAttribute('data-loaded', '')}
            />
          )}
        </div>
        <div className={styles.heroFade} aria-hidden="true" />
        <div className={styles.heroContent}>
          <ArtistName name={data.name} apple={apple} />
          {data.audience && <p className={styles.audience}>{data.audience}</p>}
          <button
            className={styles.play}
            // Apple's fill for the Play circle, with the triangle in the page's colour,
            // as the phone cuts it out of the circle; white without a key colour.
            style={
              apple
                ? ({ '--play-fill': apple.keyColor ?? '#ffffff', '--play-glyph': apple.background } as CSSProperties)
                : undefined
            }
            aria-label={`Play ${data.name}`}
            disabled={!data.topSongs.length}
            onClick={() => playList(data.topSongs, 0, { source: topSongs })}
          >
            <PlayIcon size={26} />
          </button>
        </div>
      </header>

      {data.topSongs.length > 0 && (
        <TrackShelf
          title="Top Songs"
          to={data.topSongsSeeAll ? browseHref(data.topSongsSeeAll) : null}
          tracks={data.topSongs}
          source={topSongs}
        />
      )}

      <Shelves shelves={data.shelves} />

      {data.description && (
        <section className={styles.about} aria-label={`About ${data.name}`}>
          <h2 className={styles.sectionTitle}>About {data.name}</h2>
          <p className={styles.aboutText}>{data.description}</p>
        </section>
      )}
    </>
  );
}
