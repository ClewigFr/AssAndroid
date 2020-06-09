package fr.clewig.asslib

import android.util.Log
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
import javax.net.ssl.HttpsURLConnection

/**
 * AnonymousSimpleStatsManager
 */
class AnonymousSimpleStatsManager {

    private val sessionId: UUID = UUID.randomUUID()
    private val batchPageViews: List<PageView> = mutableListOf()
    private val url: URL = URL("https://gentle-inlet-02091.herokuapp.com/views")
    private var verbose = false

    companion object AnonymousSimpleStats {
        private const val TIMEOUT_CONNECTION = 15000
        private const val TIMEOUT_SOCKET = 15000
        private val CHARSET_UTF8 = Charset.forName("UTF-8")

        /**
         * Instance of the AnonymousSimpleStatsManager class
         */
        val instance = AnonymousSimpleStatsManager()
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
        batchPageViews.plus(pageView)
        if (batchPageViews.size > 20) {

        }
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                val pageViews = PageViews(batchPageViews)
                postCall(url, JSONObject().put("pageViews", pageViews.toJson()))
                if (verbose) Log.v(
                    this.javaClass.name,
                    JSONObject().put("pageViews", pageViews.toJson()).toString()
                )

            }
        }

        if (verbose) Log.v(this.javaClass.name, "log screen $page")
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
