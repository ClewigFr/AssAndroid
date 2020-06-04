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
    put("timestamp", timestamp)
}

/**
 *
 */
fun PageViews.toJson() = JSONArray().apply {
    pageViews.forEach { put(it.toJson()) }
}
