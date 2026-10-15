import { errorResponse, isBridgeRequest, type BridgeResponse } from '@bitchord/shared/bridge';

/**
 * Firefox has no externally_connectable for web pages, so this relays the
 * page's window messages to the background and posts the answers back
 * (prompt §4.2). Chromium builds leave it out. `matches` is replaced with the
 * site origins by the manifest hook in wxt.config.ts.
 */
export default defineContentScript({
  matches: ['http://localhost/*'],
  include: ['firefox'],
  runAt: 'document_start',
  main() {
    window.addEventListener('message', (event: MessageEvent) => {
      if (event.source !== window || event.origin !== location.origin) return;
      if (!isBridgeRequest(event.data)) return;
      const request = event.data;
      browser.runtime
        .sendMessage(request)
        .then((response: BridgeResponse) => window.postMessage(response, location.origin))
        .catch((error: unknown) =>
          window.postMessage(errorResponse(request.id, 'internal', String(error)), location.origin),
        );
    });
  },
});
