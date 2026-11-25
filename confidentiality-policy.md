# Privacy: what Anonymous Simple Stats collects

Anonymous Simple Stats counts screen views. It is built so that nothing it collects identifies a person
or a device, but it does collect data: this page lists all of it.

## What the library sends

For each screen view, and nothing else:

| Field       | What it is | Who chooses it |
|-------------|------------|----------------|
| `app`       | The UUID of your application in the dashboard | You, in `AssConfig` |
| `page`      | The UUID of the screen in the dashboard | You, in `logScreen` / `TrackScreen` |
| `sessionId` | A random UUID drawn at each process start, kept in memory only | The library |
| `timestamp` | How many milliseconds ago the view happened | The library |

The library never reads or sends:
- a user, device or advertising identifier (no Advertising ID, no `ANDROID_ID`, no install ID);
- the user's location, language, accounts, contacts, or anything about the user's content;
- the device model, OS version or app version: requests carry a neutral `User-Agent: AnonymousSimpleStats`
  instead of the platform default, and don't use the HTTP cache;
- cookies: the library sets none (it would only send some if your app installs a global `CookieHandler`).

It declares only the `INTERNET` permission.

Two launches can't be linked: the session id is never written to disk.

## What stays on the device

Views not sent yet: in memory, and in the app's cache directory when an upload failed or the app went to the
background (`cacheDir/saved_file.json`). That file holds the same four fields, at most 1000 views and none older
than 7 days, and is deleted once they are delivered. The cache directory is excluded from Android backups, and
the system may clear it at any time.

## What the server keeps

Data sent to the hosted service (`ass-api.clewig.fr`), or to a self-hosted backend under its own settings:
- The views, as sent, for 90 days by default (`RETENTION_DAYS`). After that only the number of views per page
  and per day is kept: the session ids are deleted.
- The device's IP address reaches the server with each request, as with any network connection. It is only used
  in memory, to limit abuse, and is never stored or logged.

The hosted service's privacy policy, with its processors, is at https://ass.clewig.fr/#/privacy.

## Google Play Data safety

In the Data safety form of the Play Console, for this library:
- **Data collected:** App activity → **App interactions** (the screens viewed).
- **Purpose:** **Analytics**.
- **Shared:** no (it goes to the backend you configure, acting for you).
- **Processed ephemerally:** no; **required:** yes, unless your app lets users turn it off.
- **Encrypted in transit:** yes, as long as `AssConfig.endpoint` is an `https://` URL.
- No device or other IDs, no personal info, no location.

As the developer of the app, you remain responsible for your own answers and for informing its users, for
example in your own privacy policy.
