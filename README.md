# AssAndroid — Anonymous Simple Stats

A lightweight, anonymous Android library for tracking screen views.

Simple Stats collects no user identifiers, no device IDs and no personal data. Each screen view is sent as:

| Field       | Meaning                                                      |
|-------------|--------------------------------------------------------------|
| `app`       | Your application's UUID, created in the dashboard            |
| `page`      | The UUID of a page you added to that application in the dashboard |
| `sessionId` | A random UUID generated on every app launch, never persisted |
| `timestamp` | How many milliseconds ago the view happened, at send time    |

## Modules

- `assLib`: the library.
- `app`: a sample app that shows how to integrate it.

## Integration

```kotlin
// Usually in Application.onCreate (or your first Activity)
val ass = AnonymousSimpleStatsManager.getInstance(
    applicationContext,
    AssConfig(
        endpoint = "https://your-ass-backend.example.com",
        appId = "c4740a37-572d-488f-9fc4-40cf1575739b", // from the dashboard: "Mes applications"
        verbose = BuildConfig.DEBUG
    )
)

// Whenever a screen is shown, with the UUID of that page in the dashboard
ass.logScreen("ed1129dd-c159-4bf8-9851-3016b9d6ee3a")
```

Setup: in the dashboard, create an application (you get its UUID), then add one page per screen you want to track (each gets a UUID).

### Many screens at once, or with an AI assistant

The dashboard can import page names from a CSV (**Importer un CSV**) and export them with their UUIDs (**Exporter en CSV**: `name,id,app_id`).

[AI_INTEGRATION.md](AI_INTEGRATION.md) is written for AI coding assistants. It has two phases:
1. the assistant lists the app's screens in a CSV, which you import in the dashboard;
2. from the exported CSV, the assistant adds the library: the dependency, a Kotlin object with the ids, the initialization, and the `logScreen` calls for Activities, Fragments and Compose.

Ask: *"Read AI_INTEGRATION.md from the Anonymous Simple Stats library and follow phase 1"*, then, with the export, *"follow phase 2"*.

`logScreen` ignores (and logs) ids that aren't valid UUIDs instead of throwing. A malformed `appId` logs an error and disables the library, rather than sending views the server would reject. The old `getInstance(context, verbose)` still compiles but is deprecated: it sends to `https://ass-api.clewig.fr` without an application UUID, so views are only accepted for pages that already exist there.

The library declares the `INTERNET` and `ACCESS_NETWORK_STATE` permissions in its manifest.

## How it works

- Page views are kept in memory and sent in a batch when:
  - there are more than 20 views queued, or
  - the oldest queued view is more than 2 minutes old, or
  - the app goes to the background (through `ProcessLifecycleOwner`).
- Batches are sent by `POST` to `/views` as `{"pageViews": [...]}`.
- If sending fails, the batch is written to the cache directory and loaded again on the next launch.
- `TLSSocketFactory` turns on TLS 1.2/1.3 on old devices (API 16–19) and never turns on SSLv3 or TLS 1.0/1.1.

### Server responses

| Code  | Meaning                                       | What the client does |
|-------|-----------------------------------------------|----------------------|
| `200` | All views accepted                            | Clears the batch     |
| `206` | Accepted, but some unknown page IDs were dropped | Clears the batch  |
| `400` | Nothing accepted                              | Clears the batch     |
| `429` / `503` | Rate limited / server busy           | Saves the batch to retry later |
| other | Server error                                  | Saves the batch to retry later |

## Related repositories

- **AssBackend**: the API that receives the page views
- **AssFrontEnd**: the dashboard for viewing the stats

## Build

```bash
./gradlew :assLib:assembleRelease
```

Requirements: JDK 17 to build (AGP 8.7, Gradle 8.9). minSdk 16, compileSdk 35.
