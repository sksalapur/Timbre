# YouTube Music sign-in (phase 5)

Prompt §5.3. There's no login screen: BitChord uses the YouTube session the browser already has, through the extension. The cookies and the signature never reach the page; it only gets names, pictures and InnerTube responses.

## How a signed-in request travels

1. YouTube.js runs in the page as a second instance (`getSignedInnertube`, `app/src/playback/youtube/innertube.ts`). It is marked `logged_in` so its library calls aren't refused, but it holds no cookie and adds no credentials.
2. Its InnerTube calls go through the bridge as `yt.fetch` with `auth: true`. Everything else (the session bootstrap, the player script) loads like the anonymous instance's.
3. The extension (`extension/lib/ytFetch.ts`, `ytAuth.ts`):
   - accepts only `https://(music|www).youtube.com/youtubei/v1/*`;
   - drops any identity headers the page sent;
   - adds `Authorization: SAPISIDHASH … SAPISID1PHASH … SAPISID3PHASH …` (one `<ts>_<sha1(ts secret origin)>` per signing cookie: SAPISID, `__Secure-1PAPISID`, `__Secure-3PAPISID`), `X-Goog-AuthUser`, `X-Goog-PageId` for a brand channel and `X-Origin`. **All three hashes are required.** With `SAPISIDHASH` alone, as `DesktopYouTubeAuth.kt` sends it, the user's real Chrome got signed-out answers from `account_menu` (verified 2026-10-15; 0.2.1 fixed it). Playwright's Chromium sent every cookie and couldn't reproduce it;
   - fetches with `credentials: 'include'`, so the browser attaches the cookies itself. A session rule for the extension's own requests (tabId −1) sets the YouTube Origin and Referer the signature was computed for.
4. `onBehalfOfUser` isn't sent. The desktop app found a stale `dataSyncId` answered 400 on every request, and `X-Goog-AuthUser` plus `X-Goog-PageId` already pick the identity.

## Accounts and channels

- **Logins:** `account/account_menu` is asked for each `X-Goog-AuthUser` from 0. The walk stops at the first refusal, at a missing header, or when a login repeats (past the last one Google answers with the first again). At most five.
- **Channels:** `account/accounts_list` for each login gives its own channel and its brand channels (`pageId` from the item's tokens). If that call fails, the login's own channel stands in.
- **Default:** the channel YouTube itself has selected (`isSelected`) on the first login. The user's choice is stored in `storage.local` (`yt.prefs`).
- **Caching:** the list is cached for 10 minutes. A change to a session cookie (SAPISID, SID and their `__Secure-` forms; not SIDCC, which rotates constantly) clears it, re-reads, and pushes the new state.
- **Disconnect** (sidebar account menu, or the popup) stops BitChord using the session. The user stays signed in to Google.

## Pushes

`Bridge.subscribe`:
- On Chromium the page opens an `externally_connectable` port (`bitchord-events`).
- On Firefox the relay content script holds the port and forwards each event with `postMessage`.

An MV3 worker drops its ports when it sleeps, so ports reconnect only while the page is visible. The page also re-reads the state when the tab regains focus.

When the identity changes, the page resets every `['music', …]` query and the resolver's verdicts (`useYtAuth.adopt`).

## What's signed in

| Feature | How |
|---|---|
| Home, search, album, artist, playlist | The signed instance whenever signed in (personal home feed, private playlists) |
| Library | `/library/:section`: `FEmusic_history`, `…_library_corpus_track_artists`, `…_liked_albums`, `…_liked_videos`, `…_mixed_for_you`, `…_liked_playlists`, with continuations |
| Favorite | `/like/like` and `/like/removelike` on the videoId. State from `/next`'s `likeButtonRenderer` |
| Add to Library | The header's ToggleButton target: `/like/like` on the album's or playlist's list id |
| Add to Playlist | `/playlist/get_add_to_playlist`, then `/browse/edit_playlist` `ACTION_ADD_VIDEO` |
| Age-restricted playback | An age-gate refusal anonymously makes the engine ask again signed in: YTMUSIC, then WEB, then VISIONOS (the phone uses WEB_REMIX with cookies) |

## Verified, and not yet

- **Verified signed out** (Playwright Chromium and stock Firefox): the auth state, the refusal codes (`unauthenticated` signed out, `forbidden` off-host), pushes over both transports, the update notice.
- **Verified in the user's Chrome:** the account, name and picture read through `account_menu`, after the three-hash fix.
- **When the account can't be read**, the extension says why: an HTTP status, or YouTube's own `logged_in=0` from `responseContext.serviceTrackingParams`. The popup shows it, the Extension page too, and the sidebar shows a Retry pill.
- **Still to check signed in:** library pages, Favorite, Add to Library, Add to Playlist, channel switching, and age-restricted playback (and whether the signed web clients' streams outlast ~60 s, given the WebPO problem in `youtube-playback.md`).
