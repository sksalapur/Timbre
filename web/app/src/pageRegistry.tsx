import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import { matchPath } from 'react-router';
import {
  AlbumSkeleton,
  ArtistSkeleton,
  HomeSkeleton,
  NewSkeleton,
  PlainSkeleton,
  PlaylistSkeleton,
  RadioSkeleton,
  SearchSkeleton,
} from '@/skeletons/PageSkeletons';

type PageModule = { default: ComponentType };

interface PageRoute {
  path: string;
  load: () => Promise<PageModule>;
  skeleton: ReactNode;
}

/**
 * Every page, lazy-loaded behind its own skeleton (prompt §3). `load` is kept
 * so links can warm a route's chunk on hover or focus, before the click.
 */
export const pages: PageRoute[] = [
  { path: '/home', load: () => import('@/pages/HomePage'), skeleton: <HomeSkeleton /> },
  { path: '/new', load: () => import('@/pages/NewPage'), skeleton: <NewSkeleton /> },
  { path: '/radio', load: () => import('@/pages/RadioPage'), skeleton: <RadioSkeleton /> },
  { path: '/search', load: () => import('@/pages/SearchPage'), skeleton: <SearchSkeleton /> },
  { path: '/album/:id', load: () => import('@/pages/AlbumPage'), skeleton: <AlbumSkeleton /> },
  { path: '/artist/:id', load: () => import('@/pages/ArtistPage'), skeleton: <ArtistSkeleton /> },
  { path: '/playlist/:id', load: () => import('@/pages/PlaylistPage'), skeleton: <PlaylistSkeleton /> },
  { path: '/extension', load: () => import('@/pages/ExtensionPage'), skeleton: <PlainSkeleton /> },
];

export const notFound: PageRoute = { path: '*', load: () => import('@/pages/NotFoundPage'), skeleton: <PlainSkeleton /> };

export function pageElement({ load, skeleton }: PageRoute) {
  const Page = lazy(load);
  return (
    <Suspense fallback={skeleton}>
      <Page />
    </Suspense>
  );
}

/** Warms the chunk for whatever page `href` leads to. Safe to call repeatedly. */
export function preloadRoute(href: string): void {
  const pathname = href.split(/[?#]/)[0] ?? href;
  const page = pages.find((p) => matchPath(p.path, pathname));
  void page?.load().catch(() => {
    // A failed prefetch is retried by the real navigation.
  });
}
