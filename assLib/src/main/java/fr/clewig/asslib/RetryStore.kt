package fr.clewig.asslib

import org.json.JSONObject
import java.io.File

internal const val MAX_RETRY_VIEWS = 1000
internal const val MAX_RETRY_AGE_MS = 7L * 24 * 60 * 60 * 1000

/**
 * What the retry file keeps: views younger than [MAX_RETRY_AGE_MS], at most the
 * [MAX_RETRY_VIEWS] most recent ones, so a device offline for long can't grow it forever.
 */
internal fun capRetryBatch(views: List<PageView>, now: Long): List<PageView> =
    views.filter { now - it.timestamp <= MAX_RETRY_AGE_MS }.takeLast(MAX_RETRY_VIEWS)

/**
 * Page views waiting to be sent again, kept in [file] across restarts.
 * Not thread-safe: [StatsEngine] only uses it from its serial dispatcher.
 */
internal class RetryStore(
    private val file: File,
    private val clock: () -> Long,
    private val log: AssLog
) {
    fun read(): List<PageView> = try {
        if (!file.exists()) emptyList()
        else {
            val array = JSONObject(file.readText(Charsets.UTF_8)).getJSONArray("pageViews")
            (0 until array.length()).map { array.getJSONObject(it).toPageView() }
        }
    } catch (e: Exception) {
        // A corrupt file is dropped rather than making every later save fail
        log.w("Corrupt retry file dropped")
        emptyList()
    }

    fun add(views: List<PageView>) = write(read() + views)

    fun remove(views: List<PageView>) = write(read() - views.toSet())

    private fun write(views: List<PageView>) {
        try {
            val kept = capRetryBatch(views, clock())
            if (kept.isEmpty()) file.delete() else replace(JSONObject().put("pageViews", kept.toJsonLocal()).toString())
        } catch (e: Exception) {
            log.e("Could not write the retry file", e)
        }
    }

    /** Write aside then rename: a process killed mid-write leaves the old file intact. */
    private fun replace(content: String) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (tmp.renameTo(file)) return
        file.delete()
        if (!tmp.renameTo(file)) log.e("Could not write the retry file")
    }
}
