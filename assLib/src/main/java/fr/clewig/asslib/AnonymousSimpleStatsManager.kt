package fr.clewig.asslib

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.net.ssl.HttpsURLConnection

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
 * AnonymousSimpleStatsManager
 */
class AnonymousSimpleStatsManager(context: Context, private val config: AssConfig) {

    private val context: Context = context.applicationContext ?: context
    private val verbose = config.verbose
    private val sessionId: UUID = UUID.randomUUID()
    private val lock = Any()
    private val fileLock = Any()
    private val batchPageViews: MutableList<PageView> = mutableListOf() // guarded by lock
    private val url: URL = URL(config.endpoint.trimEnd('/') + "/views")
    private val appId: UUID? = parseAppId(config.appId)
    /** A malformed appId disables the library instead of sending views the server would reject. */
    private val disabled = config.appId != null && appId == null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object AnonymousSimpleStats :
        AssHolder<AnonymousSimpleStatsManager, Context, AssConfig>(::AnonymousSimpleStatsManager) {

        /** Default server for the deprecated getInstance(context, verbose), kept so existing integrations keep compiling. */
        private const val LEGACY_ENDPOINT = "https://ass-api.clewig.fr"

        @Deprecated(
            "Pass the backend URL explicitly",
            ReplaceWith("getInstance(context, AssConfig(endpoint = \"https://…\", appId = \"…\", verbose = verbose))")
        )
        fun getInstance(context: Context, verbose: Boolean): AnonymousSimpleStatsManager =
            getInstance(context, AssConfig(LEGACY_ENDPOINT, appId = null, verbose = verbose))

        private const val TIMEOUT_CONNECTION = 45000
        private const val TIMEOUT_SOCKET = 45000
        private val CHARSET_UTF8 = Charset.forName("UTF-8")
        private const val BATCH_THRESHOLD = 20
        private const val DELAY_BEFORE_BATCH_UPLOAD = 120000
        private const val BATCH_SAVE_NAME = "saved_file.json"
        private const val TAG = "AnonymousSimpleStats"
        private const val CONFIG_HINT =
            "check that appId and the page UUIDs passed to logScreen come from the same application in the dashboard"
    }

    init {
        if (disabled) Log.e(TAG, "Invalid appId \"${config.appId}\": it must be the application UUID from the dashboard. Stats are disabled.")
    }

    init {
        // Disk read off the main thread
        scope.launch { restoreBatchForRetry() }
        // Lifecycle observers must be added on the main thread
        runOnMain {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    if (verbose) Log.v(TAG, "onBackground upload")
                    createAndSendJson(synchronized(lock) { drainBatch() })
                }
            })
        }
        if (verbose) Log.v(TAG, "setup")
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else Handler(Looper.getMainLooper()).post(block)
    }

    /**
     * Send a page view hit
     *
     *  @param pageId the page identifier
     */
    fun logScreen(pageId: String) {
        if (disabled) return
        // Never crash the host app over a bad id
        val page = try {
            UUID.fromString(pageId)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Ignored page view: \"$pageId\" is not a UUID")
            return
        }
        val toSend: List<PageView>? = synchronized(lock) {
            batchPageViews.add(PageView(page, sessionId))
            val isPageViewOldEnough =
                batchPageViews.first().timestamp + DELAY_BEFORE_BATCH_UPLOAD < System.currentTimeMillis()
            if (batchPageViews.size > BATCH_THRESHOLD || isPageViewOldEnough) drainBatch() else null
        }
        toSend?.let { createAndSendJson(it) }

        if (verbose) Log.v(TAG, "log screen $page")
    }

    /**
     * Take every queued page view out of the pending list, so each view is sent only once.
     * Must be called while holding [lock].
     */
    private fun drainBatch(): List<PageView> {
        val batch = batchPageViews.toList()
        batchPageViews.clear()
        return batch
    }

    private fun createAndSendJson(batchList: List<PageView>) {
        if (batchList.isEmpty()) return
        scope.launch {
            val jsonArray = PageViews(batchList).toJson()
            val delivered = postCall(url, viewsPayload(appId, jsonArray))
            if (!delivered) saveBatchForRetry(batchList)

            if (verbose) Log.v(TAG, jsonArray.toString())
        }
    }

    /**
     * Post request
     *
     * @param url the url
     * @param jsonObj the jsonObject to send
     * @return true if the batch was handled by the server and must not be retried
     */
    private fun postCall(url: URL, jsonObj: JSONObject): Boolean {
        var urlConnection: HttpURLConnection? = null
        return try {
            urlConnection = url.openConnection() as HttpURLConnection
            if (urlConnection is HttpsURLConnection) {
                try {
                    urlConnection.sslSocketFactory = TLSSocketFactory()
                } catch (e: KeyManagementException) {
                    Log.e(TAG, "Could not enable TLS 1.2+", e)
                } catch (e: NoSuchAlgorithmException) {
                    Log.e(TAG, "Could not enable TLS 1.2+", e)
                }
            }
            urlConnection.connectTimeout = TIMEOUT_CONNECTION
            urlConnection.readTimeout = TIMEOUT_SOCKET
            urlConnection.requestMethod = "POST"
            urlConnection.doOutput = true
            urlConnection.doInput = true
            urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            OutputStreamWriter(urlConnection.outputStream, CHARSET_UTF8).use {
                it.write(jsonObj.toString())
            }

            // Read the code first: inputStream throws for 4xx/5xx responses.
            val handled = handleResponseCode(urlConnection.responseCode)
            val body = if (urlConnection.responseCode < 400) urlConnection.inputStream
            else urlConnection.errorStream
            body?.let { if (verbose) Log.v(TAG, convertStreamToString(BufferedInputStream(it)) ?: "") }
            handled
        } catch (e: IOException) {
            // Offline, timeout, DNS…: the batch is saved and retried later
            Log.w(TAG, "Upload failed (${e.javaClass.simpleName}: ${e.message}), page views kept for retry")
            false
        } finally {
            urlConnection?.disconnect()
        }
    }

    /**
     * Handle the request response code
     *
     * @param responseCode the response code
     * @return true if the batch must not be retried
     */
    private fun handleResponseCode(responseCode: Int): Boolean {
        return when (responseCode) {
            200 -> {
                Log.v(TAG, "OK")
                true
            }
            206 -> {
                Log.w(TAG, "Some page views were rejected: $CONFIG_HINT")
                true
            }
            400 -> {
                // Retrying wouldn't help: the views are dropped
                Log.w(TAG, "Page views rejected by the server (400): $CONFIG_HINT")
                true
            }
            else -> {
                Log.w(TAG, "Server answered $responseCode, page views kept for retry")
                false
            }
        }
    }

    /**
     * Append a failed batch to the retry file, which is reloaded on the next launch.
     */
    private fun saveBatchForRetry(failedBatch: List<PageView>) {
        synchronized(fileLock) {
            try {
                val file = File(context.cacheDir, BATCH_SAVE_NAME)
                val all = readSavedBatch(file) + failedBatch
                file.writeText(
                    JSONObject().put("pageViews", PageViews(all).toJsonLocal()).toString(),
                    CHARSET_UTF8
                )
            } catch (e: Exception) {
                Log.e(TAG, "Could not access the retry file", e)
            }
        }
    }

    private fun readSavedBatch(file: File): List<PageView> {
        if (!file.exists()) return emptyList()
        val array = JSONObject(file.readText(CHARSET_UTF8)).getJSONArray("pageViews")
        return (0 until array.length()).map { array.getJSONObject(it).toPageView() }
    }

    private fun restoreBatchForRetry() {
        synchronized(fileLock) {
            val cacheFile = File(context.cacheDir, BATCH_SAVE_NAME)
            try {
                val saved = readSavedBatch(cacheFile)
                synchronized(lock) { batchPageViews.addAll(0, saved) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not access the retry file", e)
            } finally {
                cacheFile.delete()
            }
        }

        if (verbose) Log.v(TAG, "reload saved page view")
    }


    /**
     * Convert a stream to string
     *
     * @param inputStream the [InputStream]
     */
    private fun convertStreamToString(inputStream: InputStream): String? {
        val sb = StringBuilder()
        var line: String?

        val br = BufferedReader(InputStreamReader(inputStream))
        line = br.readLine()

        while (line != null) {
            sb.append(line)
            line = br.readLine()
        }
        br.close()
        return sb.toString()
    }
}
