package fr.clewig.asslib.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.clewig.asslib.AnonymousSimpleStatsManager

/**
 * Logs a view of [pageId] each time this screen becomes visible: when it enters the composition
 * while the app is in the foreground, and each time the app comes back to the foreground with
 * this screen shown. The Compose equivalent of calling `logScreen` in `onResume`.
 *
 * Put it once at the top of a screen:
 * ```
 * @Composable
 * fun CartScreen() {
 *     TrackScreen(stats, AssPages.PANIER)
 *     …
 * }
 * ```
 *
 * @param stats the instance from `AnonymousSimpleStatsManager.getInstance`
 * @param pageId the page UUID from the dashboard
 */
@Composable
public fun TrackScreen(stats: AnonymousSimpleStatsManager, pageId: String) {
    TrackScreen(pageId, stats, stats::logScreen)
}

/** [TrackScreen] with the logging function apart, for the tests. [key] identifies where views go. */
@Composable
internal fun TrackScreen(pageId: String, key: Any, logScreen: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Keyed on pageId too: the same composable showing another page logs it at once
    DisposableEffect(lifecycleOwner, key, pageId) {
        // A new observer receives the events up to the current state, so ON_RESUME comes at once when already resumed
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) logScreen(pageId)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
