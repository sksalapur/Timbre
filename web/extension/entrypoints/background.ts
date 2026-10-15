import {
  BRIDGE_PROTOCOL,
  errorResponse,
  isBridgeRequest,
  okResponse,
  type BridgeRequest,
  type BridgeResponse,
  type Capability,
} from '@bitchord/shared/bridge';
import { installRules } from '../lib/rules';
import { ytFetch } from '../lib/ytFetch';

declare const __SITE_ORIGINS__: string[];

/** Exact origins (port included) allowed to talk to the extension. */
const ALLOWED_ORIGINS = new Set(__SITE_ORIGINS__);

/** Grows as each phase lands its methods. */
const CAPABILITIES: Capability[] = ['yt'];

function originOf(sender: Browser.runtime.MessageSender): string | null {
  if (sender.origin) return sender.origin;
  try {
    return sender.url ? new URL(sender.url).origin : null;
  } catch {
    return null;
  }
}

async function handle(request: BridgeRequest): Promise<BridgeResponse> {
  if (request.protocol !== BRIDGE_PROTOCOL) {
    return errorResponse(
      request.id,
      'protocol',
      `The page speaks bridge protocol ${request.protocol}; this extension speaks ${BRIDGE_PROTOCOL}. Update whichever is older.`,
    );
  }
  switch (request.method) {
    case 'ping':
      return okResponse<'ping'>(request.id, {
        version: browser.runtime.getManifest().version,
        protocol: BRIDGE_PROTOCOL,
        browser: import.meta.env.FIREFOX ? 'firefox' : 'chromium',
        capabilities: CAPABILITIES,
      });
    case 'yt.fetch':
      try {
        return okResponse<'yt.fetch'>(request.id, await ytFetch((request as BridgeRequest<'yt.fetch'>).params));
      } catch (error) {
        const code = (error as { code?: string }).code === 'forbidden' ? 'forbidden' : 'network';
        return errorResponse(request.id, code, error instanceof Error ? error.message : String(error));
      }
    default:
      return errorResponse(request.id, 'unsupported', `${request.method} isn't available in this version of the extension.`);
  }
}

/**
 * One entry point for both transports: validates the envelope and the
 * sender's origin, then answers through `sendResponse`.
 */
function listener(
  message: unknown,
  sender: Browser.runtime.MessageSender,
  sendResponse: (response: BridgeResponse) => void,
): boolean {
  if (!isBridgeRequest(message)) return false;
  const origin = originOf(sender);
  if (!origin || !ALLOWED_ORIGINS.has(origin)) {
    sendResponse(errorResponse(message.id, 'forbidden', `Origin ${origin ?? 'unknown'} isn't allowed.`));
    return false;
  }
  handle(message)
    .catch((error: unknown) => errorResponse(message.id, 'internal', String(error)))
    .then(sendResponse);
  return true; // answering asynchronously
}

export default defineBackground(() => {
  // Dynamic rules persist, but are rewritten on every install and startup so
  // an update that changes them never leaves stale ones behind.
  const refreshRules = () =>
    installRules(__SITE_ORIGINS__).catch((error: unknown) => console.error('BitChord: installing rules failed', error));
  browser.runtime.onInstalled.addListener(refreshRules);
  browser.runtime.onStartup.addListener(refreshRules);

  // Chromium: the page itself, through externally_connectable.
  browser.runtime.onMessageExternal?.addListener(listener);

  // Firefox: the relay content script on the site's tab. Only our own
  // content scripts can reach onMessage; the tab URL is the page's origin.
  browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (sender.id !== browser.runtime.id) return false;
    return listener(message, sender, sendResponse);
  });
});
