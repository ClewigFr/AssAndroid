# AssAndroid — Anonymous Simple Stats

A lightweight, anonymous Android library for tracking screen views.

Simple Stats collects no user identifiers, no device IDs and no personal data. Each screen view is sent as:

| Field       | Meaning                                                      |
|-------------|--------------------------------------------------------------|
| `page`      | A UUID you assign to each screen                             |
| `sessionId` | A random UUID generated on every app launch, never persisted |
| `timestamp` | How many milliseconds ago the view happened, at send time    |

## Modules

- `assLib`: the library.
- `app`: a sample app that shows how to integrate it.

## Integration

```kotlin
// Usually in Application.onCreate (or your first Activity)
val ass = AnonymousSimpleStatsManager.getInstance(applicationContext, /* verbose = */ BuildConfig.DEBUG)

// Whenever a screen is shown
ass.logScreen("4b344f25-b0cb-49cf-a865-c6626582332a")
```

The library declares the `INTERNET` and `ACCESS_NETWORK_STATE` permissions in its manifest.

## How it works

- Page views are kept in memory and sent in a batch when:
  - there are more than 20 views queued, or
  - the oldest queued view is more than 2 minutes old, or
  - the app goes to the background (through `ProcessLifecycleOwner`).
- Batches are sent by `POST` to `/views` as `{"pageViews": [...]}`.
- If sending fails, the batch is written to the cache directory and loaded again on the next launch.
- `TLSSocketFactory` turns on TLS 1.1 and 1.2 on old devices (API 16–19).

### Server responses

| Code  | Meaning                                       | What the client does |
|-------|-----------------------------------------------|----------------------|
| `200` | All views accepted                            | Clears the batch     |
| `206` | Accepted, but some unknown page IDs were dropped | Clears the batch  |
| `400` | Nothing accepted                              | Clears the batch     |
| other | Server error                                  | Saves the batch to retry later |

## Related repositories

- **AssBackend**: the API that receives the page views
- **AssFrontEnd**: the dashboard for viewing the stats

## Build

```bash
./gradlew :assLib:assembleRelease
```

Requirements: minSdk 16, compileSdk 29, Java 8.
