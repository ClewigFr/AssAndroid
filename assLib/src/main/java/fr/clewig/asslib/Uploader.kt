package fr.clewig.asslib

import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream
import javax.net.ssl.HttpsURLConnection

/** What became of one POST /views. */
internal sealed class UploadResult {
    /** The server handled the batch (accepted or rejected for good): never resend it. */
    object Done : UploadResult()

    /** Send it again later, not before [retryAfterMs] when the server said so (Retry-After). */
    data class Retry(val retryAfterMs: Long? = null) : UploadResult()
}

/** Sends one POST /views body. */
internal fun interface Uploader {
    fun upload(payload: JSONObject): UploadResult
}

internal const val CONFIG_HINT =
    "check that appId and the page UUIDs passed to logScreen come from the same application in the dashboard"

/**
 * 200 and 206 mean accepted, 400 means rejected (retrying wouldn't help), anything else
 * (incl. 415/429/503) means retry.
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

/** Retry-After in seconds as milliseconds; the HTTP-date form and garbage give null. */
internal fun parseRetryAfter(header: String?): Long? =
    header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { it * 1000 }

/** Whether a response's Accept-Encoding (RFC 7694) says request bodies may be gzipped. */
internal fun acceptsGzip(header: String?): Boolean =
    header?.split(',')?.any { it.substringBefore(';').trim().equals("gzip", ignoreCase = true) } == true

/**
 * The http(s) URL of POST /views under [endpoint], or null when it is malformed.
 */
internal fun viewsUrl(endpoint: String): URL? = try {
    URL(endpoint.trim().trimEnd('/') + "/views")
        .takeIf { it.protocol == "http" || it.protocol == "https" }
} catch (e: Exception) {
    null
}

/**
 * POST over [HttpURLConnection]. Bodies are only gzipped once the server has advertised it
 * with Accept-Encoding, so an older backend never gets a body it can't read.
 */
internal class HttpUploader(private val url: URL, private val log: AssLog) : Uploader {

    @Volatile
    private var gzip = false

    override fun upload(payload: JSONObject): UploadResult {
        var connection: HttpURLConnection? = null
        return try {
            val compress = gzip
            log.v("POST ${url.path}${if (compress) " (gzip)" else ""}")
            connection = open(compress)
            val out = connection.outputStream.let { if (compress) GZIPOutputStream(it) else it }
            out.writer(Charsets.UTF_8).use { it.write(payload.toString()) }
            result(connection)
        } catch (e: IOException) {
            // Offline, timeout, DNS…: the batch is kept and retried later
            log.w("Upload failed (${e.javaClass.simpleName}: ${e.message}), page views kept for retry")
            UploadResult.Retry()
        } catch (e: Exception) {
            // Never throw into the host app (TLS provider bugs, SecurityException…)
            log.e("Upload failed unexpectedly, page views kept for retry", e)
            UploadResult.Retry()
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(compress: Boolean) = (url.openConnection() as HttpURLConnection).apply {
        if (this is HttpsURLConnection) legacyTlsFactory?.let { sslSocketFactory = it }
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        requestMethod = "POST"
        doOutput = true
        useCaches = false
        // Not the platform default, which names the device model and Android version
        setRequestProperty("User-Agent", "AnonymousSimpleStats")
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        if (compress) setRequestProperty("Content-Encoding", "gzip")
    }

    private fun result(connection: HttpURLConnection): UploadResult {
        // Read the code first: inputStream throws for 4xx/5xx responses
        val code = connection.responseCode
        gzip = acceptsGzip(connection.getHeaderField("Accept-Encoding"))
        if (log.verbose) {
            val body = if (code < 400) connection.inputStream else connection.errorStream
            body?.bufferedReader(Charsets.UTF_8)?.use { log.v(it.readText()) }
        }
        return if (isHandled(code, log)) UploadResult.Done
        else UploadResult.Retry(parseRetryAfter(connection.getHeaderField("Retry-After")))
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
