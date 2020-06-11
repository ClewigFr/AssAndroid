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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.Charset
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.net.ssl.HttpsURLConnection

/**
 * AnonymousSimpleStatsManager
 */
class AnonymousSimpleStatsManager(val context: Context) {

    private val sessionId: UUID = UUID.randomUUID()
    private val batchPageViews: MutableList<PageView> = mutableListOf()
    private val url: URL = URL("https://gentle-inlet-02091.herokuapp.com/views")
    private var verbose = false

    companion object AnonymousSimpleStats {
        private const val TIMEOUT_CONNECTION = 15000
        private const val TIMEOUT_SOCKET = 15000
        private val CHARSET_UTF8 = Charset.forName("UTF-8")
        private const val BATCH_THRESHOLD = 20
        private const val DELAY_BEFORE_BATCH_UPLOAD = 120000
        private const val BATCH_SAVE_NAME = "saved_file.json"
    }

    /**
     * Setup the manager
     *
     * @param verbose is log activated
     */
    fun setup(verbose: Boolean) {
        this.verbose = verbose
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
                    if (batchPageViews.isNotEmpty()) {
                        createAndSendJson(batchPageViews.toMutableList())
                    }
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
        val pageView = PageView(page, sessionId)
        batchPageViews.add(pageView)
        val isPageViewOldEnough =
            batchPageViews.first().timestamp + DELAY_BEFORE_BATCH_UPLOAD < System.currentTimeMillis()
        if (batchPageViews.size > BATCH_THRESHOLD || isPageViewOldEnough) {
            createAndSendJson(batchPageViews.toMutableList())
        }

        if (verbose) Log.v(this.javaClass.name, "log screen $page")
    }

    private fun createAndSendJson(batchList: MutableList<PageView>) {
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                val pageViews = PageViews(batchList)
                val jsonArray = pageViews.toJson()
                postCall(url, JSONObject().put("pageViews", jsonArray))

                if (verbose) Log.v(this.javaClass.name, jsonArray.toString())
            }
        }
    }

    /**
     * Post request
     *
     * @param url the url
     * @param jsonObj the jsonObject to send
     * @param token the application token
     */
    private fun postCall(url: URL, jsonObj: JSONObject, token: String? = null): String? {
        var result: String?

        var urlConnection: HttpURLConnection? = null
        try {
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
            val wr = OutputStreamWriter(
                urlConnection.outputStream,
                CHARSET_UTF8
            )
            wr.write(jsonObj.toString())
            wr.flush()
            wr.close()

            val `in`: InputStream =
                BufferedInputStream(urlConnection.inputStream)

            handleResponseCode(urlConnection.responseCode, jsonObj)

            result = convertStreamToString(`in`)
        } catch (e: SocketTimeoutException) {
            Log.e(e.javaClass.name, e.message, e)
            saveBatchForRetry()
            result = null
        } catch (e: IOException) {
            Log.e(e.javaClass.name, e.message, e)
            saveBatchForRetry()
            result = null
        } catch (e: ArrayIndexOutOfBoundsException) {
            Log.e(e.javaClass.name, e.message, e)
            saveBatchForRetry()
            result = null
        } finally {
            urlConnection?.disconnect()
        }
        return result
    }

    /**
     * Handle the request response code
     *
     * @param responseCode the response code
     */
    private fun handleResponseCode(responseCode: Int, jsonObj: JSONObject) {
        when (responseCode) {
            200 -> {
                batchPageViews.clear()
                Log.v(this.javaClass.name, "OK")
            }
            206 -> {
                batchPageViews.clear()
                Log.v(
                    this.javaClass.name,
                    "Partial OK, bad pageId in the request has not been treated"
                )
            }
            400 -> {
                batchPageViews.clear()
                Log.v(this.javaClass.name, "Failed, the pageIds in the request were not treated")
            }
            else -> {
                saveBatchForRetry()
                Log.v(this.javaClass.name, "error $responseCode")
            }
        }
    }

    private fun saveBatchForRetry() {
        val pageViews = PageViews(batchPageViews)
        val jsonArray = pageViews.toJsonLocal()
        val file = File.createTempFile(BATCH_SAVE_NAME, null, context.cacheDir)
        file.outputStream().use {
            it.write(JSONObject().put("pageViews", jsonArray).toString().toByteArray())

        }
        batchPageViews.clear()
    }

    private fun restoreBatchForRetry() {
        val cacheFile = File(context.cacheDir, BATCH_SAVE_NAME)

        if (cacheFile.exists()) {
            var savedString = ""
            cacheFile.inputStream().bufferedReader().useLines { lines ->
                savedString = lines.fold("") { some, text ->
                    "$some\n$text"
                }
            }
            JSONObject(savedString).run {
                val array = getJSONArray("pageViews")
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val pageView = item.toPageView()
                    batchPageViews.add(pageView)
                }
            }

            cacheFile.delete()
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
