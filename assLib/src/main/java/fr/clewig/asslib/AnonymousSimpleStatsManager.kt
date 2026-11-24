package fr.clewig.asslib

import android.app.Application
import android.content.Context
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Configuration of the stats manager.
 *
 * @param endpoint base URL of the AssBackend server, e.g. "https://stats.example.com"
 * @param appId UUID of your application, shown in the dashboard when you create it. Required:
 *   page views are only accepted for pages of this application. A malformed one disables the library.
 * @param verbose log every event (debug builds only)
 */
public data class AssConfig(
    public val endpoint: String,
    public val appId: String,
    public val verbose: Boolean = false
)

/**
 * Entry point of the library: get it with [getInstance], then call [logScreen] on every screen.
 * Never throws into the host app; a bad configuration only disables it (see the logs).
 */
public class AnonymousSimpleStatsManager(context: Context, private val config: AssConfig) {

    private val log: AssLog = AndroidLog(config.verbose)

    /** Null when the configuration is invalid: the library is then disabled. */
    private val engine: StatsEngine? = createEngine(context.applicationContext ?: context)

    public companion object AnonymousSimpleStats {

        private const val RETRY_FILE_NAME = "saved_file.json"

        @Volatile
        private var instance: AnonymousSimpleStatsManager? = null

        /**
         * The single instance, created on the first call; later calls return it and ignore
         * their arguments (with a warning when [config] differs).
         */
        public fun getInstance(context: Context, config: AssConfig): AnonymousSimpleStatsManager {
            val existing = instance ?: synchronized(this) {
                instance ?: return AnonymousSimpleStatsManager(context, config).also { instance = it }
            }
            if (config != existing.config) {
                existing.log.w("getInstance called again with a different AssConfig: it is ignored, the first one is kept")
            }
            return existing
        }
    }

    private fun createEngine(context: Context): StatsEngine? {
        val appId = parseAppId(config.appId)
        if (appId == null) {
            log.e("Invalid appId \"${config.appId}\": it must be the application UUID from the dashboard. Stats are disabled.")
            return null
        }
        val url = viewsUrl(config.endpoint)
        if (url == null) {
            log.e("Invalid endpoint \"${config.endpoint}\": it must be an http(s) URL. Stats are disabled.")
            return null
        }
        if (url.protocol == "http") {
            log.w("Endpoint \"${config.endpoint}\" is cleartext http: Android 9+ blocks it unless the app's network security config allows it")
        }
        val clock = { System.currentTimeMillis() }
        return StatsEngine(
            appId = appId,
            uploader = HttpUploader(url, log),
            store = RetryStore(File(context.cacheDir, RETRY_FILE_NAME), clock, log),
            serial = serialExecutor(),
            clock = clock,
            log = log
        ).also { engine ->
            engine.start()
            val app = context as? Application
            if (app != null) app.registerActivityLifecycleCallbacks(BackgroundWatcher { log.v("onBackground upload"); engine.flush() })
            else log.w("No Application context: views are only sent by batches, not when the app goes to background")
            log.v("setup")
        }
    }

    /** One daemon thread, one task at a time; nothing thrown on it may reach the host app. */
    private fun serialExecutor(): Executor {
        val thread = Executors.newSingleThreadExecutor { task ->
            Thread(task, "AnonymousSimpleStats").apply { isDaemon = true }
        }
        return Executor { task ->
            thread.execute {
                try {
                    task.run()
                } catch (e: Throwable) {
                    log.e("Unexpected error, ignored", e)
                }
            }
        }
    }

    /**
     * Log a view of a screen.
     *
     * @param pageId the page UUID from the dashboard; anything else is ignored with a warning
     */
    public fun logScreen(pageId: String) {
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
