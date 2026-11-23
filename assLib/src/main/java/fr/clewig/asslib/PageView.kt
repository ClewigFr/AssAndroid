package fr.clewig.asslib

import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 *
 */
data class PageView(
    var page: UUID,
    var sessionId: UUID,
    var timestamp: Long = System.currentTimeMillis()
)

/**
 *
 */
data class PageViews(var pageViews: List<PageView>)

/**
 *
 */
fun PageView.toJson() = JSONObject().apply {
    put("page", page)
    put("sessionId", sessionId)
    // A clock moved backwards must not send a negative elapsed time
    put("timestamp", (System.currentTimeMillis() - timestamp).coerceAtLeast(0))
}

/**
 * Local (on-disk) format: keeps the absolute epoch timestamp so it survives restarts.
 */
fun PageView.toJsonLocal() = JSONObject().apply {
    put("page", page)
    put("sessionId", sessionId)
    put("timestamp", timestamp)
}

/**
 *
 */
fun JSONObject.toPageView() = PageView(
    UUID.fromString(getString("page")),
    UUID.fromString(getString("sessionId")),
    getLong("timestamp")
)

/**
 *
 */
fun PageViews.toJson() = JSONArray().apply {
    pageViews.forEach {
        put(it.toJson())
    }
}

/**
 *
 */
fun PageViews.toJsonLocal() = JSONArray().apply {
    pageViews.forEach {
        put(it.toJsonLocal())
    }
}

/**
 * The application UUID from [appId], or null when it's absent or malformed.
 */
internal fun parseAppId(appId: String?): UUID? = try {
    appId?.let { UUID.fromString(it.trim()) }
} catch (e: IllegalArgumentException) {
    null
}

/**
 * Body of POST /views: {"app": "<uuid>", "pageViews": [...]}; "app" is left out when unknown
 * (legacy integrations).
 */
internal fun viewsPayload(app: UUID?, pageViews: JSONArray) = JSONObject().apply {
    app?.let { put("app", it.toString()) }
    put("pageViews", pageViews)
}

internal const val MAX_RETRY_VIEWS = 1000
internal const val MAX_RETRY_AGE_MS = 7L * 24 * 60 * 60 * 1000

/**
 * What the retry file keeps: views younger than [MAX_RETRY_AGE_MS], at most the
 * [MAX_RETRY_VIEWS] most recent ones, so a device offline for long can't grow it forever.
 */
internal fun capRetryBatch(views: List<PageView>, now: Long): List<PageView> =
    views.filter { now - it.timestamp <= MAX_RETRY_AGE_MS }.takeLast(MAX_RETRY_VIEWS)
