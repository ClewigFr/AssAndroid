# AssAndroid — Anonymous Simple Stats

[![CI](https://github.com/ClewigFr/AssAndroid/actions/workflows/ci.yml/badge.svg)](https://github.com/ClewigFr/AssAndroid/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![JitPack](https://jitpack.io/v/ClewigFr/AssAndroid.svg)](https://jitpack.io/#ClewigFr/AssAndroid)

A lightweight, anonymous Android library for tracking screen views.

Simple Stats collects no user identifiers, no device IDs and no personal data. Each screen view is sent as:

| Field       | Meaning                                                      |
|-------------|--------------------------------------------------------------|
| `app`       | Your application's UUID, created in the dashboard            |
| `page`      | The UUID of a page you added to that application in the dashboard |
| `sessionId` | A random UUID generated on every app launch, never persisted |
| `timestamp` | How many milliseconds ago the view happened, at send time    |

What is kept on the device and on the server, and what to declare in the Play Console's Data safety form: [confidentiality-policy.md](confidentiality-policy.md).

## Modules

- `assLib`: the library.
- `assCompose`: optional Jetpack Compose helpers (`TrackScreen`).
- `app`: a sample app that shows how to integrate it.

## Installation

The library is published through [JitPack](https://jitpack.io/#ClewigFr/AssAndroid). It needs minSdk 16 or higher, declares only the `INTERNET` permission and has no dependency besides the Kotlin standard library.

```groovy
// settings.gradle (or the root build.gradle)
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// app/build.gradle
dependencies {
    implementation 'com.github.ClewigFr.AssAndroid:assLib:0.6.1'
}
```

Since 0.5.0 the repository publishes two modules, each with its own coordinates. Don't use the short `com.github.ClewigFr:AssAndroid:<version>`: it brings both modules, Compose included. Versions up to 0.4.0 were only published as `com.github.ClewigFr:AssAndroid:<version>`.

### Jetpack Compose

An optional module logs a screen from Compose. It needs minSdk 21, and brings Compose runtime and lifecycle, which a Compose app already has; `assLib` stays dependency-free.

```groovy
implementation 'com.github.ClewigFr.AssAndroid:assCompose:0.6.1' // includes assLib
```

```kotlin
@Composable
fun CartScreen() {
    TrackScreen(stats, AssPages.PANIER) // logs on entry and each time the app comes back to the foreground
    …
}
```

## Integration

```kotlin
// Usually in Application.onCreate (or your first Activity)
val ass = AnonymousSimpleStatsManager.getInstance(
    applicationContext,
    AssConfig(
        endpoint = "https://ass-api.clewig.fr", // the hosted service, or your own AssBackend
        appId = "c4740a37-572d-488f-9fc4-40cf1575739b", // from the dashboard: "Mes applications"
        verbose = BuildConfig.DEBUG
    )
)

// Whenever a screen is shown, with the UUID of that page in the dashboard
ass.logScreen("ed1129dd-c159-4bf8-9851-3016b9d6ee3a")
```

Setup: in the dashboard, create an application (you get its UUID), then add one page per screen you want to track (each gets a UUID).

With the hosted service, the API is at `https://ass-api.clewig.fr` and the dashboard at https://ass.clewig.fr, where you create your applications and pages. To keep the data on your own server, deploy [AssBackend](https://github.com/ClewigFr/AssBackend) and [AssFrontEnd](https://github.com/ClewigFr/AssFrontEnd) and use your backend's URL as `endpoint`.

### Many screens at once, or with an AI assistant

The dashboard can import page names from a CSV (**Importer un CSV**) and export them with their UUIDs (**Exporter en CSV**: `name,id,app_id`).

[AI_INTEGRATION.md](AI_INTEGRATION.md) is written for AI coding assistants. It has two phases:
1. the assistant lists the app's screens in a CSV, which you import in the dashboard;
2. from the exported CSV, the assistant adds the library: the dependency, a Kotlin object with the ids, the initialization, and the `logScreen` calls for Activities, Fragments and Compose.

Ask: *"Read AI_INTEGRATION.md from the Anonymous Simple Stats library and follow phase 1"*, then, with the export, *"follow phase 2"*.

### Batch settings

Views are sent by batches to save battery. Two optional `AssConfig` settings change when:

```kotlin
AssConfig(
    endpoint = "https://ass-api.clewig.fr",
    appId = "c4740a37-572d-488f-9fc4-40cf1575739b",
    batchSize = 100,         // views per batch, 20 to 200 (default 50)
    batchMaxAgeMinutes = 30  // send older batches at the next view, 2 to 60 (default 10)
)
```

Larger and older batches mean fewer uploads, so less battery, but views reach the dashboard later. Whatever these settings, the batch is sent when the app goes to the background. A value out of range is replaced by the nearest bound, with a warning in the logs.

`logScreen` ignores (and logs) ids that aren't valid UUIDs instead of throwing. A malformed `appId` logs an error and disables the library, rather than sending views the server would reject.

The library declares only the `INTERNET` permission in its manifest, and has no dependency besides the Kotlin standard library.

## How it works

- Page views are kept in memory and sent in a batch when:
  - 50 views are queued (`batchSize`, 20 to 200), or
  - a view arrives and the oldest queued view is more than 10 minutes old (`batchMaxAgeMinutes`, 2 to 60), or
  - the app goes to the background (its last activity stops).
- Every batch is written to the cache directory before it is sent and removed once the server accepted it, so a process killed mid-upload loses nothing.
- Batches are sent by `POST` to `/views` as `{"app": "<uuid>", "pageViews": [...]}`, one at a time on a single background thread.
- If sending fails, the batch is written to the cache directory (at most 1000 views, 7 days) and sent again after the next successful upload or on the next launch.
- After a failure, nothing is sent for a while: the server's `Retry-After`, otherwise 1 minute doubling up to 30 minutes. Batches go straight to the retry file meanwhile.
- Bodies are gzipped once the server has answered with `Accept-Encoding: gzip`, so an older backend never receives a body it can't read.
- `TLSSocketFactory` turns on TLS 1.2/1.3 on old devices (API 16–19) and never turns on SSLv3 or TLS 1.0/1.1.

### Server responses

| Code  | Meaning                                       | What the client does |
|-------|-----------------------------------------------|----------------------|
| `200` | All views accepted                            | Clears the batch     |
| `206` | Accepted, but some unknown page IDs were dropped | Clears the batch  |
| `400` | Nothing accepted                              | Clears the batch     |
| `415` | Unsupported `Content-Encoding`               | Saves the batch, retries uncompressed |
| `429` / `503` | Rate limited / server busy           | Saves the batch, waits for `Retry-After` |
| other | Server error                                  | Saves the batch to retry later |

### Limits of the hosted service

Each account on the server has, by default:
- **5 applications** and **30 pages per application**. The dashboard refuses to create more, and a CSV import that would go over is refused as a whole.
- **100,000 page views per month** (calendar month, UTC, all the account's applications together). This is a soft limit: views keep being recorded, and the dashboard shows an alert. The library is never refused views because of it.

A self-hosted backend sets its own values (`MAX_APPS`, `MAX_PAGES_PER_APP`, `MAX_MONTHLY_VIEWS`, `0` for no limit): see the backend's README.

## Related repositories

- [**AssBackend**](https://github.com/ClewigFr/AssBackend): the API that receives the page views
- [**AssFrontEnd**](https://github.com/ClewigFr/AssFrontEnd): the dashboard for viewing the stats

## Build

```bash
./gradlew :assLib:assembleRelease
./gradlew :assLib:testDebugUnitTest :assCompose:testDebugUnitTest   # JVM tests, no emulator (Robolectric for Compose)
```

Requirements: JDK 17 to build (AGP 8.7, Gradle 8.9). minSdk 16, compileSdk 35.

## License

[MIT](LICENSE) © Clewig
