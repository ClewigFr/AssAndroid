package fr.clewig.asslib

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

/**
 * Configuration of the stats manager.
 *
 * @param endpoint base URL of the AssBackend server, e.g. "https://stats.example.com"
 * @param appId UUID of your application, shown in the dashboard when you create it.
 *   Page views are only accepted for pages of this application.
 * @param verbose log every event (debug builds only)
 */
data class AssConfig(
    val endpoint: String,
    val appId: String?,
    val verbose: Boolean = false
)

/**
 * Entry point of the library: get it with [getInstance], then call [logScreen] on every screen.
 * Never throws into the host app; a bad configuration only disables it (see the logs).
 */
class AnonymousSimpleStatsManager(context: Context, private val config: AssConfig) {

    private val log: AssLog = AndroidLog(config.verbose)

    /** Null when the configuration is invalid: the library is then disabled. */
    private val engine: StatsEngine? = createEngine(context.applicationContext ?: context)

    companion object AnonymousSimpleStats :
        AssHolder<AnonymousSimpleStatsManager, Context, AssConfig>(::AnonymousSimpleStatsManager) {

        /** Default server for the deprecated getInstance(context, verbose), kept so existing integrations keep compiling. */
        private const val LEGACY_ENDPOINT = "https://ass-api.clewig.fr"
        private const val RETRY_FILE_NAME = "saved_file.json"

        @Deprecated(
            "Pass the backend URL explicitly",
            ReplaceWith("getInstance(context, AssConfig(endpoint = \"https://…\", appId = \"…\", verbose = verbose))")
        )
        fun getInstance(context: Context, verbose: Boolean): AnonymousSimpleStatsManager =
            getInstance(context, AssConfig(LEGACY_ENDPOINT, appId = null, verbose = verbose))

        override fun onExistingInstance(instance: AnonymousSimpleStatsManager, arg: Context, arg2: AssConfig) {
            if (arg2 != instance.config) {
                instance.log.w("getInstance called again with a different AssConfig: it is ignored, the first one is kept")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createEngine(context: Context): StatsEngine? {
        val appId = parseAppId(config.appId)
        if (config.appId != null && appId == null) {
            log.e("Invalid appId \"${config.appId}\": it must be the application UUID from the dashboard. Stats are disabled.")
            return null
        }
        val url = viewsUrl(config.endpoint)
        if (url == null) {
            log.e("Invalid endpoint \"${config.endpoint}\": it must be an http(s) URL. Stats are disabled.")
            return null
        }
        // One task at a time; nothing thrown in the library's coroutines may reach the host app
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO.limitedParallelism(1) +
                CoroutineExceptionHandler { _, e -> log.e("Unexpected error, ignored", e) }
        )
        val clock = { System.currentTimeMillis() }
        return StatsEngine(
            appId = appId,
            uploader = HttpUploader(url, log),
            store = RetryStore(File(context.cacheDir, RETRY_FILE_NAME), clock, log),
            serialScope = scope,
            clock = clock,
            log = log
        ).also { engine ->
            engine.start()
            onMainThread {
                // Lifecycle observers must be added on the main thread
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStop(owner: LifecycleOwner) {
                        log.v("onBackground upload")
                        engine.flush()
                    }
                })
            }
            log.v("setup")
        }
    }

    private fun onMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else Handler(Looper.getMainLooper()).post(block)
    }

    /**
     * Log a view of a screen.
     *
     * @param pageId the page UUID from the dashboard; anything else is ignored with a warning
     */
    fun logScreen(pageId: String) {
        val engine = engine ?: return
        val page = try {
            UUID.fromString(pageId)
        } catch (e: IllegalArgumentException) {
            log.w("Ignored page view: \"$pageId\" is not a UUID")
            return
        }
        engine.log(page)
    }
}
