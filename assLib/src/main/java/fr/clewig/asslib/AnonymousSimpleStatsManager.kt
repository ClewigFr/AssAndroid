package fr.clewig.asslib

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * AnonymousSimpleStatsManager
 */
class AnonymousSimpleStatsManager(val context: Context) {

    private val sessionId: UUID = UUID.randomUUID()
    private val batchPageViews: MutableList<PageView> = mutableListOf()
    val url: URL = URL("https://gentle-inlet-02091.herokuapp.com/views")
    private var verbose = false

    companion object AnonymousSimpleStats {
        private const val TIMEOUT_CONNECTION = 15000
        private const val TIMEOUT_SOCKET = 15000
        private val CHARSET_UTF8 = Charset.forName("UTF-8")
    }

    /**
     * Setup the manager
     *
     * @param verbose is log activated
     */
    fun setup(verbose: Boolean) {
        this.verbose = verbose
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
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            createAndSendJson(batchPageViews.toMutableList())
        } else {
            val constraints =
                Constraints.Builder().setTriggerContentMaxDelay(1, TimeUnit.MINUTES).build()
            val uploadWorkRequest: WorkRequest =
                OneTimeWorkRequestBuilder<SendLogWorker>()
                    .setInputData(createInputData(batchPageViews))
                    .setConstraints(constraints)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueue(uploadWorkRequest)
        }
        batchPageViews.clear()

        if (verbose) Log.v(this.javaClass.name, "log screen $page")
    }

    private fun createInputData(batchList: MutableList<PageView>): Data {
        val builder = Data.Builder()
        val pageViews = PageViews(batchList)
        val jsonArray = pageViews.toJson()
        builder.putString(
            SendLogWorker.KEY_JSON,
            JSONObject().put("pageViews", jsonArray).toString()
        )

        return builder.build()
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
    fun postCall(url: URL, jsonObj: JSONObject, token: String? = null): String? {
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

            handleResponseCode(urlConnection.responseCode)

            result = convertStreamToString(`in`)
        } catch (e: IOException) {
            Log.e(e.javaClass.name, e.message, e)
            result = null
        } catch (e: ArrayIndexOutOfBoundsException) {
            Log.e(e.javaClass.name, e.message, e)
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
    private fun handleResponseCode(responseCode: Int) {
        when (responseCode) {
            200 -> Log.v(this.javaClass.name, "OK")
            206 -> Log.v(
                this.javaClass.name,
                "Partial OK, bad pageId in the request has not been treated"
            )
            400 -> Log.v(this.javaClass.name, "Failed, the pageIds in the request were not treated")
            else -> Log.v(this.javaClass.name, "error $responseCode")
        }
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
