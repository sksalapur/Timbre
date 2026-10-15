import { detectExtension, type DetectedExtension } from '@bitchord/shared/bridge';
import { useQuery } from '@tanstack/react-query';
import { config } from '@/config';
import { queryClient } from '@/lib/queryClient';

export type ExtensionStatus =
  | { state: 'checking' }
  | { state: 'missing' }
  | { state: 'present'; extension: DetectedExtension };

export const extensionQueryKey = ['extension', 'detect'] as const;

/** Re-pings after a miss: an MV3 service worker waking from sleep can miss the first one. */
const RECHECKS = 4;
const RECHECK_MS = 1500;

/**
 * One ping. When the extension was already known, a miss gets a second, more
 * patient ping before it counts: a worker that fell asleep while the tab was
 * in the background shouldn't flip the whole site to "not installed".
 */
async function detect(): Promise<DetectedExtension | null> {
  const found = await detectExtension({ chromiumExtensionId: config.chromiumExtensionId });
  if (found || !queryClient.getQueryData(extensionQueryKey)) return found;
  return detectExtension({ chromiumExtensionId: config.chromiumExtensionId, timeoutMs: 4000 });
}

/**
 * Whether the companion extension is installed (prompt §4.2: a `ping` on
 * startup). A miss is reported at once, so a visitor without the extension
 * sees the install prompt straight away, but re-checked a few times: a
 * sleeping worker answering late flips the page to its content on its own.
 */
export function useExtension(): ExtensionStatus {
  const query = useQuery({
    queryKey: extensionQueryKey,
    queryFn: detect,
    staleTime: 30_000,
    gcTime: Infinity,
    retry: false,
    // Re-pinged when the tab comes back, so an extension updated or reloaded
    // meanwhile reports its new version (useExtensionUpdate).
    refetchOnWindowFocus: true,
    refetchInterval: (q) => (q.state.data === null && q.state.dataUpdateCount <= RECHECKS ? RECHECK_MS : false),
  });
  if (query.isPending) return { state: 'checking' };
  if (!query.data) return { state: 'missing' };
  return { state: 'present', extension: query.data };
}
