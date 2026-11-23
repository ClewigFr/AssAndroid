package fr.clewig.asslib

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Calls [onBackground] when the app's last started activity stops, i.e. the app leaves the
 * screen. Replaces androidx ProcessLifecycleOwner so the library needs no dependency.
 * Callbacks arrive on the main thread.
 */
internal class BackgroundWatcher(private val onBackground: () -> Unit) : Application.ActivityLifecycleCallbacks {

    /** Activities between onStart and onStop. Starts at 0 even if getInstance came after an onStart. */
    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        started++
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        // A rotation stops then restarts the activity: not a trip to the background
        if (started == 0 && !activity.isChangingConfigurations) onBackground()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
