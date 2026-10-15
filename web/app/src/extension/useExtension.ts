import { detectExtension, type DetectedExtension } from '@bitchord/shared/bridge';
import { useQuery } from '@tanstack/react-query';
import { config } from '@/config';

export type ExtensionStatus =
  | { state: 'checking' }
  | { state: 'missing' }
  | { state: 'present'; extension: DetectedExtension };

export const extensionQueryKey = ['extension', 'detect'] as const;

/** Re-pings after a miss: an MV3 service worker waking from sleep can miss the first one. */
const RECHECKS = 4;
const RECHECK_MS = 1500;

/**
 * Whether the companion extension is installed (prompt §4.2: a `ping` on
 * startup). A miss is reported at once, so a visitor without the extension
 * sees the install prompt straight away, but re-checked a few times: a
 * sleeping worker answering late flips the page to its content on its own.
 */
export function useExtension(): ExtensionStatus {
  const query = useQuery({
    queryKey: extensionQueryKey,
    queryFn: () => detectExtension({ chromiumExtensionId: config.chromiumExtensionId }),
    staleTime: Infinity,
    gcTime: Infinity,
    retry: false,
    refetchOnWindowFocus: false,
    refetchInterval: (q) => (q.state.data === null && q.state.dataUpdateCount <= RECHECKS ? RECHECK_MS : false),
  });
  if (query.isPending) return { state: 'checking' };
  if (!query.data) return { state: 'missing' };
  return { state: 'present', extension: query.data };
}
