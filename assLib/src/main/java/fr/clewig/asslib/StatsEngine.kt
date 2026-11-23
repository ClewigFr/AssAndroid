package fr.clewig.asslib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Batching and retry logic, free of Android so it runs in JVM tests.
 *
 * Every upload and every access to [store] runs on [serialScope], which must run one task
 * at a time: uploads never overlap, so a view saved for retry can't be sent twice.
 * Only the in-memory batch is shared with the caller's thread, under [lock].
 */
internal class StatsEngine(
    private val appId: UUID?,
    private val uploader: Uploader,
    private val store: RetryStore,
    private val serialScope: CoroutineScope,
    private val clock: () -> Long,
    private val log: AssLog,
    private val sessionId: UUID = UUID.randomUUID()
) {
    private val lock = Any()
    private val batch = mutableListOf<PageView>() // guarded by lock

    /** Sends what the previous runs left in the retry file. */
    fun start() {
        serialScope.launch { retrySaved() }
    }

    fun log(page: UUID) {
        val now = clock()
        val toSend = synchronized(lock) {
            batch += PageView(page, sessionId, now)
            val oldEnough = batch.first().timestamp + MAX_BATCH_AGE_MS < now
            if (batch.size >= BATCH_THRESHOLD || oldEnough) drain() else null
        }
        toSend?.let { send(it) }
        log.v("log screen $page")
    }

    /**
     * The app goes to background and the process may be killed: the batch is saved first,
     * sent, then taken out of the file once delivered.
     */
    fun flush() {
        val toSend = synchronized(lock) { drain() }
        if (toSend.isEmpty()) return
        serialScope.launch {
            store.add(toSend)
            if (upload(toSend)) store.remove(toSend)
        }
    }

    private fun send(views: List<PageView>) {
        serialScope.launch {
            if (upload(views)) retrySaved() // the server is reachable again
            else store.add(views)
        }
    }

    private fun retrySaved() {
        val saved = store.read()
        if (saved.isEmpty()) return
        log.v("retry ${saved.size} saved page views")
        if (upload(saved)) store.remove(saved)
    }

    private fun upload(views: List<PageView>): Boolean {
        val payload = viewsPayload(appId, views.toJson(clock()))
        log.v(payload.toString())
        return uploader.upload(payload)
    }

    /** Must be called while holding [lock]. */
    private fun drain(): List<PageView> = batch.toList().also { batch.clear() }

    companion object {
        const val BATCH_THRESHOLD = 20
        const val MAX_BATCH_AGE_MS = 120_000L
    }
}
