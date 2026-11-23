# CLAUDE.md

Anonymous Simple Stats (ASS): an anonymous, batched screen-view tracker for Android. Sibling repos: `../AssBackend` (API) and `../AssFrontEnd` (dashboard).

## Layout
- `assLib/` is the library (package `fr.clewig.asslib`); this is where the real code lives.
  - `AnonymousSimpleStatsManager.kt`: the public API (`getInstance(context, AssConfig(endpoint, appId, verbose))` and `logScreen(pageUuid)`); validates the config and wires the pieces below to Android (cache dir, `BackgroundWatcher`, one daemon thread as serial executor).
  - `StatsEngine.kt`: batching, upload and retry logic, Android-free; every upload and retry-file access runs on one serial executor, with a backoff after failures, so views are never sent twice.
  - `Uploader.kt`: `Uploader` interface, `HttpUploader` the response-code rules (`isHandled`), Retry-After and gzip negotiation.
  - `BackgroundWatcher.kt`: `ActivityLifecycleCallbacks` counting started activities, flushes when the app goes to background.
  - `RetryStore.kt`: the retry file in the cache dir, capped at 1000 views / 7 days.
  - `AssLog.kt`: logging interface (`AndroidLog` in the library, `FakeLog` in tests).
  - `AssHolder.kt`: a generic double-checked-locking singleton holder, used as the manager's companion object.
  - `PageView.kt`: the (internal) data model and JSON (de)serialization extensions.
  - `TLSSocketFactory.kt`: turns on TLS 1.1/1.2 for API 16–19.
- `app/` is a sample integration only (`MainActivity`).
- `AI_INTEGRATION.md`: instructions for AI assistants integrating the library into an app (screen list → CSV → dashboard → generated ids and `logScreen` calls). Keep it in step with the public API and the dashboard's CSV format.

## Conventions and constraints
- Anonymity is the product: never add device IDs, advertising IDs, IPs or anything else that persists across sessions. `sessionId` is regenerated on every process start.
- Keep the library dependency-free at runtime (only the Kotlin stdlib): plain `HttpURLConnection`, `org.json`, `java.util.concurrent`, no coroutines, androidx, Retrofit or Gson. Only the `INTERNET` permission.
- Page IDs must be UUID strings.
- The wire format is `POST /views` with body `{"app", "pageViews":[{page, sessionId, timestamp}]}` (`app` = application UUID from AssConfig, omitted when null), where `timestamp` is *milliseconds elapsed since the view*, not epoch time. Any change here must match AssBackend.
- Response codes: 200 and 206 mean accepted, 400 means rejected (drop the batch), anything else (incl. 415/429/503) means retry, after `Retry-After` when given, else an exponential backoff (1 min to 30 min).
- Bodies are gzipped only after a response carried `Accept-Encoding: gzip` (AssBackend sends it on every `/views` response and caps the decompressed body at 1 MB).
- The backend URL comes from `AssConfig.endpoint`. The library must never throw into the host app.

## Build and test
- `./gradlew :assLib:assembleDebug`
- `./gradlew :assLib:testDebugUnitTest` (JVM tests per component; `StatsEngineTest` drives the engine with a fake uploader, clock and an executor that runs tasks inline)
- Toolchain: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21; needs JDK 17. minSdk 16, compileSdk 35, targetSdk 29, Java 8 bytecode.
