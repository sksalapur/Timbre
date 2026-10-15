import { detectExtension, type DetectedExtension } from '@bitchord/shared/bridge';
import { useQuery } from '@tanstack/react-query';
import { config } from '@/config';

export type ExtensionStatus =
  | { state: 'checking' }
  | { state: 'missing' }
  | { state: 'present'; extension: DetectedExtension };

export const extensionQueryKey = ['extension', 'detect'] as const;

/**
 * Whether the companion extension is installed (prompt §4.2: a `ping` on
 * startup). Checked once per page load; the result never goes stale on its own.
 */
export function useExtension(): ExtensionStatus {
  const query = useQuery({
    queryKey: extensionQueryKey,
    queryFn: () => detectExtension({ chromiumExtensionId: config.chromiumExtensionId }),
    staleTime: Infinity,
    gcTime: Infinity,
    retry: false,
    refetchOnWindowFocus: false,
  });
  if (query.isPending) return { state: 'checking' };
  if (!query.data) return { state: 'missing' };
  return { state: 'present', extension: query.data };
}
