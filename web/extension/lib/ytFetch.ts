import type { HttpRequest, HttpResponse } from '@bitchord/shared/bridge';
import { installExtensionFetchRule } from './rules';

/** Where `yt.fetch` may go. Phase 5 widens this for signed-in InnerTube calls. */
const ALLOWED = [/^https:\/\/www\.youtube\.com\/api\/jnn\//];

let ruleReady: Promise<void> | null = null;

function toBytes(base64: string): Uint8Array {
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function toBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

/**
 * An anonymous request to YouTube from the extension, with YouTube's Origin
 * and Referer (rules.ts, EXTENSION_FETCH_RULE_ID). No cookies: the page never
 * gets anything credentialed through this.
 */
export async function ytFetch(request: HttpRequest): Promise<HttpResponse> {
  if (!ALLOWED.some((pattern) => pattern.test(request.url))) {
    throw Object.assign(new Error(`yt.fetch doesn't serve ${new URL(request.url).origin}${new URL(request.url).pathname}`), {
      code: 'forbidden',
    });
  }
  ruleReady ??= installExtensionFetchRule().catch((error: unknown) => {
    ruleReady = null;
    throw error;
  });
  await ruleReady;

  const body =
    request.body === undefined ? undefined : request.bodyEncoding === 'base64' ? toBytes(request.body) : request.body;
  const response = await fetch(request.url, {
    method: request.method ?? 'GET',
    headers: request.headers ?? {},
    body: body as BodyInit | undefined,
    credentials: 'omit',
  });
  const headers: Record<string, string> = {};
  response.headers.forEach((value, key) => (headers[key] = value));
  const type = response.headers.get('content-type') ?? '';
  const textual = /json|text|javascript|xml/.test(type);
  return {
    status: response.status,
    headers,
    url: response.url,
    bodyEncoding: textual ? 'text' : 'base64',
    body: textual ? await response.text() : toBase64(new Uint8Array(await response.arrayBuffer())),
  };
}
