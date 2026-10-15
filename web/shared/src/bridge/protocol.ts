/**
 * The page ↔ extension contract (build prompt §4.2).
 *
 * Every call is a typed request/response pair inside a versioned envelope.
 * Credentials never cross this boundary: the extension sends results only,
 * never cookies or tokens.
 */

/** Bumped on any breaking change to an envelope or a method's shape. */
export const BRIDGE_PROTOCOL = 1;

/** Tags every window message so the relay ignores everything else on the page. */
export const BRIDGE_CHANNEL = 'bitchord-bridge';

/** What the installed extension can do; `ping` reports it. */
export type Capability = 'yt' | 'yt.auth' | 'addon' | 'lyrics' | 'spotify' | 'discord';

export type BrowserFamily = 'chromium' | 'firefox';

/** A plain HTTP exchange, serialisable across the message channel. */
export interface HttpRequest {
  url: string;
  method?: 'GET' | 'POST' | 'HEAD';
  headers?: Record<string, string>;
  /** Text bodies as-is; binary bodies base64-encoded with `bodyEncoding: 'base64'`. */
  body?: string;
  bodyEncoding?: 'text' | 'base64';
}

export interface HttpResponse {
  status: number;
  headers: Record<string, string>;
  /** UTF-8 text, or base64 when `bodyEncoding` is `'base64'`. */
  body: string;
  bodyEncoding: 'text' | 'base64';
  /** The final URL after redirects. */
  url: string;
}

export interface YtAuthState {
  signedIn: boolean;
  /** Account picked for `X-Goog-AuthUser`; null when signed out. */
  account: { name: string; handle: string | null; avatarUrl: string | null; authUser: string } | null;
}

/**
 * Method name → params and result. Only `ping` is served in phase 2; the rest
 * are declared so both sides type-check against one contract as they land.
 */
export interface BridgeMethods {
  ping: {
    params: Record<string, never>;
    result: {
      /** The extension's own version, from its manifest. */
      version: string;
      protocol: number;
      browser: BrowserFamily;
      capabilities: Capability[];
    };
  };
  'yt.fetch': { params: HttpRequest; result: HttpResponse };
  'yt.authState': { params: Record<string, never>; result: YtAuthState };
  'addon.fetch': { params: HttpRequest; result: HttpResponse };
  'lyrics.fetch': { params: HttpRequest; result: HttpResponse };
}

export type BridgeMethod = keyof BridgeMethods;
export type ParamsOf<M extends BridgeMethod> = BridgeMethods[M]['params'];
export type ResultOf<M extends BridgeMethod> = BridgeMethods[M]['result'];

export interface BridgeRequest<M extends BridgeMethod = BridgeMethod> {
  channel: typeof BRIDGE_CHANNEL;
  protocol: number;
  direction: 'to-extension';
  id: string;
  method: M;
  params: ParamsOf<M>;
}

export type BridgeErrorCode =
  | 'unsupported' // the method exists in the contract but this extension can't serve it
  | 'forbidden' // the caller's origin isn't allowed, or a host permission is missing
  | 'protocol' // envelope version mismatch
  | 'network'
  | 'internal';

export interface BridgeError {
  code: BridgeErrorCode;
  message: string;
}

export type BridgeResponse<M extends BridgeMethod = BridgeMethod> = {
  channel: typeof BRIDGE_CHANNEL;
  protocol: number;
  direction: 'to-page';
  id: string;
} & ({ ok: true; result: ResultOf<M> } | { ok: false; error: BridgeError });

export function isBridgeRequest(value: unknown): value is BridgeRequest {
  const v = value as Partial<BridgeRequest> | null;
  return (
    typeof v === 'object' &&
    v !== null &&
    v.channel === BRIDGE_CHANNEL &&
    v.direction === 'to-extension' &&
    typeof v.id === 'string' &&
    typeof v.method === 'string'
  );
}

export function isBridgeResponse(value: unknown): value is BridgeResponse {
  const v = value as Partial<BridgeResponse> | null;
  return (
    typeof v === 'object' &&
    v !== null &&
    v.channel === BRIDGE_CHANNEL &&
    v.direction === 'to-page' &&
    typeof v.id === 'string' &&
    typeof v.ok === 'boolean'
  );
}

export function okResponse<M extends BridgeMethod>(id: string, result: ResultOf<M>): BridgeResponse<M> {
  return { channel: BRIDGE_CHANNEL, protocol: BRIDGE_PROTOCOL, direction: 'to-page', id, ok: true, result };
}

export function errorResponse(id: string, code: BridgeErrorCode, message: string): BridgeResponse {
  return { channel: BRIDGE_CHANNEL, protocol: BRIDGE_PROTOCOL, direction: 'to-page', id, ok: false, error: { code, message } };
}
