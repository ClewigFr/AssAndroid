# Changelog

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
