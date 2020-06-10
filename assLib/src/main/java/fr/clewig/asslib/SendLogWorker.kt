package fr.clewig.asslib

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import javax.net.ssl.HttpsURLConnection

class SendLogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) :
    Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val json = inputData.getString(KEY_JSON)
        // Do the work here--in this case, upload the images.
        if (json != null) {
            postCall(url, json)
        }

        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }

    companion object SendLogWorker {
        private const val TIMEOUT_CONNECTION = 15000
        private const val TIMEOUT_SOCKET = 15000
        const val KEY_JSON = "JSON_TO_SEND"
        private val CHARSET_UTF8 = Charset.forName("UTF-8")
        val url: URL = URL("https://gentle-inlet-02091.herokuapp.com/views")
    }

    /**
     * Post request
     *
     * @param url the url
     * @param jsonObj the jsonObject to send
     * @param token the application token
     */
    fun postCall(url: URL, jsonObj: String, token: String? = null): String? {
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
            wr.write(jsonObj)
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

