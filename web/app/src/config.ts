/** Build-time configuration, from Vite's `VITE_*` environment variables. */
export const config = {
  /** Pinned in development by the manifest `key` (web/extension/wxt.config.ts). */
  chromiumExtensionId: import.meta.env.VITE_EXTENSION_ID_CHROMIUM || DEV_EXTENSION_ID,
  androidAppUrl: import.meta.env.VITE_ANDROID_APP_URL || 'https://github.com/kushagrasinghx/BitChord/releases/latest',
} as const;
