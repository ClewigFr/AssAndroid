# Integrating Anonymous Simple Stats with an AI assistant

Instructions for an AI coding assistant (Claude Code, Copilot, Cursor…) adding screen tracking to an Android app with this library. The work has two phases, with a manual step in the dashboard in between:

1. **Phase 1:** the assistant lists the app's screens and writes `ass-pages.csv`.
2. **Manual step:** a person imports that file in the dashboard (**Importer un CSV**), then downloads the result (**Exporter en CSV**). The dashboard creates the UUIDs.
3. **Phase 2:** the assistant adds the library to the app, using the exported CSV.

What to ask the assistant:

> Phase 1: "Read AI_INTEGRATION.md from the Anonymous Simple Stats library, then list this app's screens in ass-pages.csv."
>
> Phase 2: "Here is the CSV exported from the dashboard: `<path>`. Follow phase 2 of AI_INTEGRATION.md to add the tracking. Backend endpoint: `https://ass-api.clewig.fr`."

---

## Rules for the assistant

These apply to both phases.

- **Anonymity is the product.** Never send or add anything that identifies a user or a device: no user ID, email, advertising ID, Android ID, IP address or install ID. A page name describes a screen ("Panier"), never a person or their data ("Panier de Jean", "Commande 1234").
- **Don't invent UUIDs.** Page and application UUIDs are created by the dashboard and come only from the exported CSV. If a screen has no line in the CSV, don't track it: list it in your summary and tell the person to add it in the dashboard.
- **One page per screen the user sees,** not per technical component. A dialog, bottom sheet or tab counts only if it's a real destination for the user. Don't log the same screen twice: for example in both an Activity and the Fragment it hosts.
- **The library never throws into the host app.** Don't wrap its calls in `try/catch`, and don't add retry logic: it batches, retries and keeps unsent views on its own.

---

## Phase 1: list the screens and write `ass-pages.csv`

### Find the screens

Look for every screen a user can reach:

| UI toolkit | Where the screens are |
|---|---|
| Activities | `<activity>` entries in `AndroidManifest.xml`, and classes extending `Activity`, `AppCompatActivity` or `ComponentActivity` |
| Fragments | classes extending `Fragment` or `DialogFragment`; `<fragment>` destinations in navigation graphs (`res/navigation/*.xml`) |
| Jetpack Compose | `composable("route") { … }` destinations in a `NavHost`, type-safe routes (`composable<Route>`), and top-level `@Composable fun …Screen(…)` functions |
| Tabs, pagers, bottom navigation | each tab that shows a separate screen |

Skip:
- screens that aren't shown to users: splash screens shown for less than a second, routing or trampoline Activities, debug and developer menus;
- system screens and other libraries' screens: permission dialogs, Google sign-in, payment sheets.

### Name the pages

- Use a short name, as the app's team would say it, in the app's language, for example `Accueil`, `Panier`, `Paiement – Adresse`. These names are what people will read in the stats.
- Keep names unique within the app, and at most 255 characters each.
- The dashboard allows **30 pages per application** by default, and an import that would go over is refused. If the app has more screens, group the minor ones (settings sub-screens, steps of a flow) or keep the ones the team cares about, and say which ones you left out.
- An account has **5 applications** at most, and **100,000 views per month** across them. Going over the views only shows an alert in the dashboard, but that's another reason for one page per screen, never per item or per event.
- **One screen used in several modes** (for example, editing and creating a product): use one page, unless the team needs the modes counted separately.
- **One screen showing many different items** (for example, a product page): always one page. Never one page per product or per ID.

### Write the file

Create `ass-pages.csv` at the project root, UTF-8. It has a `name` header, then one page name per line. An optional second column says where the screen is in the code. The dashboard ignores that column, but it helps the person review the list.

```csv
name,source
Accueil,ui/home/HomeScreen.kt
Recherche,ui/search/SearchFragment.kt
Fiche produit,ui/product/ProductActivity.kt
Panier,ui/cart/CartScreen.kt
Paiement – Adresse,ui/checkout/AddressScreen.kt
Profil,ui/profile/ProfileScreen.kt
```

Quote a name that contains a comma, for example `"Paiement, étape 1"`.

### Finish phase 1

Don't change any app code in this phase. Tell the person:
1. how many screens you found, and the ones you deliberately skipped, and why;
2. what to do next: in the dashboard, open the application (create it if needed), click **Importer un CSV** and choose `ass-pages.csv`, then click **Exporter en CSV** and give you the downloaded file.

Importing the same file again later only creates the new names, so the list can be updated at any time.

---

## Phase 2: add the library with the exported CSV

### The input

The exported CSV has three columns, one line per page:

```csv
name,id,app_id
Accueil,30269ec7-8441-4493-ad65-8b3a7976c8c4,7f97b570-2cc1-4459-8dad-c86ddc4e809f
Panier,cf9dad24-712e-4c68-9825-06c61fe49345,7f97b570-2cc1-4459-8dad-c86ddc4e809f
```

- `name`: the page name;
- `id`: the page UUID, to pass to `logScreen`;
- `app_id`: the application UUID (the same on every line), to pass to `AssConfig(appId = …)`.

Match each line to its screen by name. If phase 1's `ass-pages.csv` is still there, use its `source` column. Then delete `ass-pages.csv`: the exported file replaces it.

### 1. Add the dependency

Use one of these, in order of preference:

- **Option A: it's already in the project** as a module or an AAR. Then use that.
- **Option J: from JitPack.** Add `maven { url 'https://jitpack.io' }` to the repositories (in `settings.gradle`'s `dependencyResolutionManagement`, or the root `build.gradle`), then `implementation 'com.github.ClewigFr.AssAndroid:assLib:0.5.1'`, or `com.github.ClewigFr.AssAndroid:assCompose:0.5.1` for a Jetpack Compose app (it includes `assLib`, see step 4). Use the latest tag from https://github.com/ClewigFr/AssAndroid/tags. Never the short `com.github.ClewigFr:AssAndroid`: since 0.5.0 it brings both modules.
- **Option B: as a module.** Copy the `assLib` folder into the project, then:
  ```groovy
  // settings.gradle
  include ':assLib'
  ```
  ```groovy
  // app/build.gradle
  dependencies {
      implementation project(':assLib')
  }
  ```
- **Option C: as an AAR.** Build it in this repository with `./gradlew :assLib:assembleRelease`, which produces `assLib/build/outputs/aar/assLib-release.aar`. Then copy the AAR into `app/libs/`. The library has no dependency besides the Kotlin standard library:
  ```groovy
  dependencies {
      implementation files('libs/assLib-release.aar')
  }
  ```

The library declares the `INTERNET` permission itself, and supports minSdk 16 and up.

### 2. Put the ids in one Kotlin file

Generate a single object from the CSV, for example `analytics/AssPages.kt` in the app's package. Name each constant after the page name, in `UPPER_SNAKE_CASE`, ASCII only. Keep the page name in a comment.

```kotlin
package com.example.app.analytics

/** Anonymous Simple Stats ids, generated from the dashboard's CSV export. Don't edit by hand: re-export and regenerate. */
object AssPages {
    const val APP_ID = "7f97b570-2cc1-4459-8dad-c86ddc4e809f"

    /** Accueil */
    const val ACCUEIL = "30269ec7-8441-4493-ad65-8b3a7976c8c4"
    /** Panier */
    const val PANIER = "cf9dad24-712e-4c68-9825-06c61fe49345"
}
```

Copy each UUID exactly as it appears in the CSV.

### 3. Initialize once, in the Application class

If the app has no `Application` subclass, create one and register it in the manifest with `android:name`.

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AnonymousSimpleStatsManager.getInstance(
            this,
            AssConfig(
                endpoint = "https://ass-api.clewig.fr",
                appId = AssPages.APP_ID,
                verbose = BuildConfig.DEBUG
            )
        )
    }
}
```

`getInstance` returns the same instance afterwards. Later calls can pass the same arguments: the config is only read the first time.

```kotlin
fun Context.ass() = AnonymousSimpleStatsManager.getInstance(
    applicationContext, AssConfig("https://ass-api.clewig.fr", AssPages.APP_ID, BuildConfig.DEBUG)
)
```

Put this helper next to `AssPages` so screens can call `ass().logScreen(…)`.

If the app uses build types or flavors with different backends, put the endpoint in `buildConfigField` instead of a literal.

### 4. Log each screen when it's shown

Call `logScreen` each time the screen becomes visible, including when the user comes back to it. Use the hook that matches the toolkit:

**Activity**
```kotlin
override fun onResume() {
    super.onResume()
    ass().logScreen(AssPages.PANIER)
}
```

**Fragment**
```kotlin
override fun onResume() {
    super.onResume()
    requireContext().ass().logScreen(AssPages.PANIER)
}
```

Use `onResume`, not `onCreate` or `onViewCreated`: those don't run again when the user comes back. With a `ViewPager`/`ViewPager2` of fragments, use `onResume` too. With the default `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT` / `FragmentStateAdapter`, it only runs for the visible page.

**Jetpack Compose, inside the screen**

With the `assCompose` module (minSdk 21), `TrackScreen` logs the screen when it's shown and each time the app comes back to the foreground with it, like `onResume`:
```kotlin
@Composable
fun CartScreen(…) {
    val context = LocalContext.current
    TrackScreen(context.ass(), AssPages.PANIER)
    …
}
```

Without the module (minSdk below 21, or no extra dependency wanted), `LaunchedEffect` logs it on entry only, not when the app comes back to the foreground:
```kotlin
@Composable
fun CartScreen(…) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { context.ass().logScreen(AssPages.PANIER) }
    …
}
```

**Jetpack Compose, for every destination of a `NavHost`**

The alternative is one listener on the nav controller, instead of one call per screen:
```kotlin
val routeToPage = mapOf(
    "home" to AssPages.ACCUEIL,
    "cart" to AssPages.PANIER,
)
DisposableEffect(navController) {
    val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
        routeToPage[destination.route]?.let { context.ass().logScreen(it) }
    }
    navController.addOnDestinationChangedListener(listener)
    onDispose { navController.removeOnDestinationChangedListener(listener) }
}
```

Choose one approach per app, either per screen or with the listener, never both, so nothing is counted twice. Routes with arguments (`"product/{id}"`) map to one page: `destination.route` is the pattern, not the actual id.

Don't call `logScreen` in loops, on recomposition, on scroll, or for events that aren't screens (button clicks, errors). The library counts screen views only.

### 5. Check it

1. Build and run a debug build: `verbose` is on.
2. Open a few screens, then filter Logcat on the tag `AnonymousSimpleStats`:
   - `log screen <uuid>` should appear once for each screen opened;
   - after 50 views, or when the app goes to the background, the batch is sent and `OK` appears.
3. Warnings mean something is wrong:
   - `Ignored page view: "…" is not a UUID`: a constant was mistyped.
   - `Invalid appId …`: `APP_ID` is wrong, and the library disables itself.
   - `Page views rejected by the server (400)` or `Some page views were rejected`: the ids don't belong to this application in the dashboard. Re-export the CSV and regenerate `AssPages`.
4. The views appear in the dashboard, in the application's stats, within a few seconds of being sent.

### Finish phase 2

Tell the person:
- which screens are tracked, and where `logScreen` was added;
- the screens found in the code that have no line in the CSV, which are therefore not tracked;
- the CSV lines that match no screen. These are perhaps screens removed from the app: delete them in the dashboard if they're no longer used.

---

## Updating later

When screens are added:
1. **Phase 1 again.** Add the new names to a CSV, or re-list the screens. Import the file: existing names are skipped, new ones are created.
2. **Export again**, regenerate `AssPages.kt` from the new export, and add `logScreen` calls for the new screens only.

A page's UUID never changes, so the existing calls keep working.
