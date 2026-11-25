# Changelog

## Unreleased

### Changed
- Fewer uploads, to save battery (each one wakes the radio): a batch is sent at 50 views (was 20) or
  when its oldest view is 10 minutes old (was 2), and still whenever the app goes to the background.

## 0.5.1

### Privacy
- Requests send a neutral `User-Agent: AnonymousSimpleStats` instead of the platform default, which
  named the device model and Android version, and don't use the HTTP cache (as on iOS).
- New [confidentiality-policy.md](confidentiality-policy.md): what is sent, kept on the device and on
  the server, and what to declare in Google Play's Data safety form.

### Tests
- `TrackScreen` is covered by JVM tests (Robolectric): logged once when shown, again at each return
  to the foreground, never on recomposition, not before the screen is resumed, again when the page
  changes, and no more once the screen is gone. No change to the library.

## 0.5.0

### Added
- `assCompose`, an optional module for Jetpack Compose: `TrackScreen(stats, pageId)` logs a view
  when the screen is shown and each time the app comes back to the foreground with it, like
  `logScreen` in `onResume`. minSdk 21; it depends on Compose runtime and lifecycle, `assLib`
  still has no dependency.

### Changed
- The repository publishes two modules, so each has its own coordinates:
  `com.github.ClewigFr.AssAndroid:assLib:<version>` and `com.github.ClewigFr.AssAndroid:assCompose:<version>`.
  The short `com.github.ClewigFr:AssAndroid:<version>` would bring both modules, Compose included.

## 0.4.0

Breaking changes: initialize with `getInstance(context, AssConfig(endpoint, appId, verbose))`,
`appId` being the application UUID from the dashboard. Apps already doing so only need the new
version number.

### Removed
- `getInstance(context, verbose)`, deprecated since 0.3.0. It sent views without an application
  UUID, which the backend now refuses (unless it runs with `LEGACY_VIEWS=true`): views were lost.
  Use `getInstance(context, AssConfig(endpoint, appId, verbose))`.
- `AssHolder`, deprecated since 0.3.1: a singleton helper that apps weren't meant to use.

### Changed
- `AssConfig.appId` is required (`String`, no longer `String?`): views without an application
  UUID are refused by the backend. A malformed one still disables the library with an error log.

## 0.3.1

### Fixed
- Page views saved during an outage are sent again as soon as a background flush gets
  through, not only at the next app start or after a full batch of 20.
- The retry file is written aside then renamed: a process killed while writing it no longer
  loses the saved page views.

### Changed
- A cleartext `http://` endpoint logs a warning (Android 9+ blocks it by default).
- `AssHolder` is deprecated and will be removed in 0.4.0; `getInstance` is unchanged.

## 0.3.0

First public release.

### Added
- `getInstance(context, AssConfig(endpoint, appId, verbose))`: the backend URL and the
  application UUID from the dashboard are configured by the app. `logScreen` only accepts
  page UUIDs.
- [AI_INTEGRATION.md](AI_INTEGRATION.md), a guide for AI coding assistants: list the
  screens in a CSV, import it in the dashboard, then add the library from the export.
- Publication through JitPack.

### Changed
- `getInstance(context, verbose)` is deprecated: it sends to `https://ass-api.clewig.fr`
  without an application UUID.
- Only TLS 1.2 and 1.3 are used, also turned on for API 16–19.
- Built with AGP 8.7.3, Gradle 8.9 and Kotlin 2.0.21; still minSdk 16.
- No runtime dependency besides the Kotlin standard library: coroutines and
  `androidx.lifecycle` are gone. The app going to background is detected with
  `ActivityLifecycleCallbacks`.
- Only the `INTERNET` permission is declared (`ACCESS_NETWORK_STATE` removed).
- A batch is sent at 20 queued views (was 21), and when the app goes to background it is
  written to disk before being sent, so a killed process no longer loses it.
- Failed batches are resent as soon as an upload succeeds, not only on the next launch. The
  retry file keeps at most 1000 views, none older than 7 days.
- After a failed upload, nothing is sent before the server's `Retry-After`, otherwise a wait
  of 1 minute doubling up to 30 minutes.
- Bodies are gzipped once the server answers with `Accept-Encoding: gzip`.
- Timeouts are 15 s (were 45 s).
- `PageView`, `PageViews` and `TLSSocketFactory` are no longer public.

### Fixed
- The library could crash the host app on an unexpected error in its upload thread, or on
  a malformed endpoint. Both now only log and, for the endpoint, disable the library.
- Views could be sent twice when a background upload and a retry overlapped.
- A corrupt retry file made every later save fail.
- A clock moved backwards could send a negative elapsed time.

## 0.2.4-alpha and earlier

See the git tags.
