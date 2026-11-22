# CLAUDE.md

Anonymous Simple Stats (ASS): an anonymous, batched screen-view tracker for Android. Sibling repos: `../AssBackend` (API) and `../AssFrontEnd` (dashboard).

## Layout
- `assLib/` is the library (package `fr.clewig.asslib`); this is where the real code lives.
  - `AnonymousSimpleStatsManager.kt`: the public API (`getInstance(context, verbose)` and `logScreen(pageUuid)`), batching, HTTP POST, and saving/restoring a batch in the cache dir.
  - `AssHolder.kt`: a generic double-checked-locking singleton holder, used as the manager's companion object.
  - `PageView.kt`: the data model and JSON (de)serialization extensions.
  - `TLSSocketFactory.kt`: turns on TLS 1.1/1.2 for API 16–19.
- `app/` is a sample integration only (`MainActivity`).

## Conventions and constraints
- Anonymity is the product: never add device IDs, advertising IDs, IPs or anything else that persists across sessions. `sessionId` is regenerated on every process start.
- Keep the library dependency-light: plain `HttpURLConnection` and `org.json`, no Retrofit or Gson.
- Page IDs must be UUID strings.
- The wire format is `POST /views` with body `{"pageViews":[{page, sessionId, timestamp}]}`, where `timestamp` is *milliseconds elapsed since the view*, not epoch time. Any change here must match AssBackend.
- Response codes: 200 and 206 mean accepted, 400 means rejected (drop the batch), anything else means retry.
- The backend URL is hardcoded in `AnonymousSimpleStatsManager`.

## Build and test
- `./gradlew :assLib:assembleDebug`
- `./gradlew :assLib:testDebugUnitTest` (only template tests exist so far)
- Toolchain: Groovy Gradle, minSdk 16, compileSdk 29, Java 8 target.
