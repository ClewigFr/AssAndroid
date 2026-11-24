package fr.clewig.asslib

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One view of a page.
 *
 * @param timestamp epoch milliseconds of the view
 */
internal data class PageView(
    val page: UUID,
    val sessionId: UUID,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Wire format: "timestamp" is the time elapsed since the view at [now], never negative
 * (a clock moved backwards must not send a negative value).
 */
internal fun PageView.toJson(now: Long = System.currentTimeMillis()) = JSONObject().apply {
    put("page", page.toString())
    put("sessionId", sessionId.toString())
    put("timestamp", (now - timestamp).coerceAtLeast(0))
}

/**
 * Local (on-disk) format: keeps the absolute epoch timestamp so it survives restarts.
 */
internal fun PageView.toJsonLocal() = JSONObject().apply {
    put("page", page.toString())
    put("sessionId", sessionId.toString())
    put("timestamp", timestamp)
}

internal fun JSONObject.toPageView() = PageView(
    UUID.fromString(getString("page")),
    UUID.fromString(getString("sessionId")),
    getLong("timestamp")
)

internal fun List<PageView>.toJson(now: Long = System.currentTimeMillis()) =
    JSONArray().apply { forEach { put(it.toJson(now)) } }

internal fun List<PageView>.toJsonLocal() =
    JSONArray().apply { forEach { put(it.toJsonLocal()) } }

/**
 * The application UUID from [appId], or null when it's malformed.
 */
internal fun parseAppId(appId: String): UUID? = try {
    UUID.fromString(appId.trim())
} catch (e: IllegalArgumentException) {
    null
}

/** Body of POST /views: {"app": "<uuid>", "pageViews": [...]}. */
internal fun viewsPayload(app: UUID, pageViews: JSONArray) = JSONObject().apply {
    put("app", app.toString())
    put("pageViews", pageViews)
}
