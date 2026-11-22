package fr.clewig.asslib

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
 * AnonymousSimpleStatsManager
 */
class AnonymousSimpleStatsManager(private val context: Context, val verbose: Boolean) {

    private val sessionId: UUID = UUID.randomUUID()
    private val lock = Any()
    private val fileLock = Any()
    private val batchPageViews: MutableList<PageView> = mutableListOf() // guarded by lock
    private val url: URL = URL("https://gentle-inlet-02091.herokuapp.com/views")

    companion object AnonymousSimpleStats :
        AssHolder<AnonymousSimpleStatsManager, Context, Boolean>(::AnonymousSimpleStatsManager) {
        private const val TIMEOUT_CONNECTION = 45000
        private const val TIMEOUT_SOCKET = 45000
        private val CHARSET_UTF8 = Charset.forName("UTF-8")
        private const val BATCH_THRESHOLD = 20
        private const val DELAY_BEFORE_BATCH_UPLOAD = 120000
        private const val BATCH_SAVE_NAME = "saved_file.json"
    }

    init {
        restoreBatchForRetry()
        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_START)
                fun onForeground() {
                }

                @RequiresApi(Build.VERSION_CODES.N)
                @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
                fun onBackground() {
                    if (verbose) Log.v(this.javaClass.name, "onBackground upload")
                    createAndSendJson(synchronized(lock) { drainBatch() })
                }
            })
        if (verbose) Log.v(this.javaClass.name, "setup")
    }

    /**
     * Send a page view hit
     *
     *  @param pageId the page identifier
     */
    fun logScreen(pageId: String) {
        val page = UUID.fromString(pageId)
        val toSend: List<PageView>? = synchronized(lock) {
            batchPageViews.add(PageView(page, sessionId))
            val isPageViewOldEnough =
                batchPageViews.first().timestamp + DELAY_BEFORE_BATCH_UPLOAD < System.currentTimeMillis()
            if (batchPageViews.size > BATCH_THRESHOLD || isPageViewOldEnough) drainBatch() else null
        }
        toSend?.let { createAndSendJson(it) }

        if (verbose) Log.v(this.javaClass.name, "log screen $page")
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
        GlobalScope.launch(Dispatchers.IO) {
            val jsonArray = PageViews(batchList).toJson()
            val delivered = postCall(url, JSONObject().put("pageViews", jsonArray))
            if (!delivered) saveBatchForRetry(batchList)

            if (verbose) Log.v(this.javaClass.name, jsonArray.toString())
        }
    }

    /**
     * Post request
     *
     * @param url the url
     * @param jsonObj the jsonObject to send
     * @param token the application token
     * @return true if the batch was handled by the server and must not be retried
     */
    private fun postCall(url: URL, jsonObj: JSONObject, token: String? = null): Boolean {
        var urlConnection: HttpURLConnection? = null
        return try {
            urlConnection = url.openConnection() as HttpURLConnection
            if (urlConnection is HttpsURLConnection) {
                try {
                    urlConnection.sslSocketFactory = TLSSocketFactory()
                } catch (e: KeyManagementException) {
                    Log.e(e.javaClass.name, e.message, e)
                } catch (e: NoSuchAlgorithmException) {
                    Log.e(e.javaClass.name, e.message, e)
                }
            }
            urlConnection.connectTimeout = TIMEOUT_CONNECTION
            urlConnection.readTimeout = TIMEOUT_SOCKET
            urlConnection.requestMethod = "POST"
            if (token != null) {
                urlConnection.setRequestProperty("Authorization", "Bearer $token")
            }
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
            body?.let { if (verbose) Log.v(this.javaClass.name, convertStreamToString(BufferedInputStream(it)) ?: "") }
            handled
        } catch (e: IOException) {
            // SocketTimeoutException is an IOException
            Log.e(e.javaClass.name, e.message, e)
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
                Log.v(this.javaClass.name, "OK")
                true
            }
            206 -> {
                Log.v(
                    this.javaClass.name,
                    "Partial OK, bad pageId in the request has not been treated"
                )
                true
            }
            400 -> {
                Log.v(this.javaClass.name, "Failed, the pageIds in the request were not treated")
                true
            }
            else -> {
                Log.v(this.javaClass.name, "error $responseCode")
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
                Log.e(e.javaClass.name, e.message, e)
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
                Log.e(e.javaClass.name, e.message, e)
            } finally {
                cacheFile.delete()
            }
        }

        if (verbose) Log.v(this.javaClass.name, "reload saved page view")
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
