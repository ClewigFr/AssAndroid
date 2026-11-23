package fr.clewig.asslib

import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Sends one POST /views body. */
internal fun interface Uploader {
    /** @return true if the server handled the batch and it must not be retried */
    fun upload(payload: JSONObject): Boolean
}

internal const val CONFIG_HINT =
    "check that appId and the page UUIDs passed to logScreen come from the same application in the dashboard"

/**
 * 200 and 206 mean accepted, 400 means rejected (retrying wouldn't help), anything else
 * (incl. 429/503) means retry.
 *
 * @return true if the batch must not be retried
 */
internal fun isHandled(responseCode: Int, log: AssLog): Boolean = when (responseCode) {
    200 -> {
        log.v("OK")
        true
    }
    206 -> {
        log.w("Some page views were rejected: $CONFIG_HINT")
        true
    }
    400 -> {
        log.w("Page views rejected by the server (400): $CONFIG_HINT")
        true
    }
    else -> {
        log.w("Server answered $responseCode, page views kept for retry")
        false
    }
}

/**
 * The http(s) URL of POST /views under [endpoint], or null when it is malformed.
 */
internal fun viewsUrl(endpoint: String): URL? = try {
    URL(endpoint.trim().trimEnd('/') + "/views")
        .takeIf { it.protocol == "http" || it.protocol == "https" }
} catch (e: Exception) {
    null
}

internal class HttpUploader(private val url: URL, private val log: AssLog) : Uploader {

    override fun upload(payload: JSONObject): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                if (this is HttpsURLConnection) legacyTlsFactory?.let { sslSocketFactory = it }
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            connection.outputStream.writer(Charsets.UTF_8).use { it.write(payload.toString()) }

            // Read the code first: inputStream throws for 4xx/5xx responses
            val code = connection.responseCode
            if (log.verbose) {
                val body = if (code < 400) connection.inputStream else connection.errorStream
                body?.bufferedReader(Charsets.UTF_8)?.use { log.v(it.readText()) }
            }
            isHandled(code, log)
        } catch (e: IOException) {
            // Offline, timeout, DNS…: the batch is kept and retried later
            log.w("Upload failed (${e.javaClass.simpleName}: ${e.message}), page views kept for retry")
            false
        } catch (e: Exception) {
            // Never throw into the host app (TLS provider bugs, SecurityException…)
            log.e("Upload failed unexpectedly, page views kept for retry", e)
            false
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000

        /** TLS 1.2 is off by default before API 20: one shared factory to turn it on, null elsewhere. */
        val legacyTlsFactory: TLSSocketFactory? by lazy {
            if (Build.VERSION.SDK_INT >= 20) null
            else try {
                TLSSocketFactory()
            } catch (e: Exception) {
                null
            }
        }
    }
}
