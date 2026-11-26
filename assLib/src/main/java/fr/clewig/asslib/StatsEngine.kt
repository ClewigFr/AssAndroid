package fr.clewig.asslib

import java.util.UUID
import java.util.concurrent.Executor

/**
 * Batching and retry logic, free of Android so it runs in JVM tests.
 *
 * Every upload and every access to [store] runs on [serial], which must run one task at a
 * time: uploads never overlap, so a view saved for retry can't be sent twice.
 * Only the in-memory batch is shared with the caller's thread, under [lock].
 *
 * After a failed upload nothing is sent before a backoff (the server's Retry-After, else
 * 1 min doubling up to 30 min): batches go straight to the retry file meanwhile, so an
 * offline device or an overloaded server isn't hit again on every batch.
 */
internal class StatsEngine(
    private val appId: UUID,
    private val uploader: Uploader,
    private val store: RetryStore,
    private val serial: Executor,
    private val clock: () -> Long,
    private val log: AssLog,
    private val rules: BatchRules = BatchRules(),
    private val sessionId: UUID = UUID.randomUUID()
) {
    private val lock = Any()
    private val batch = mutableListOf<PageView>() // guarded by lock

    // Only touched on serial
    private var failures = 0
    private var nextAttemptAt = 0L

    /** Sends what the previous runs left in the retry file. */
    fun start() = serial.execute { retrySaved() }

    fun log(page: UUID) {
        val now = clock()
        val toSend = synchronized(lock) {
            batch += PageView(page, sessionId, now)
            val oldEnough = batch.first().timestamp + rules.maxAgeMs < now
            if (batch.size >= rules.size || oldEnough) drain() else null
        }
        toSend?.let { send(it) }
        log.v("log screen $page")
    }

    /** The app goes to background and the process may be killed: the batch is sent now. */
    fun flush() {
        val toSend = synchronized(lock) { drain() }
        if (toSend.isNotEmpty()) send(toSend)
    }

    /**
     * Every batch is saved first, sent, then taken out of the file once delivered: a process
     * killed mid-upload loses nothing (the batch goes with the next retry instead).
     */
    private fun send(views: List<PageView>) = serial.execute {
        store.add(views)
        if (upload(views)) {
            store.remove(views)
            retrySaved() // the server is reachable again
        }
    }

    private fun retrySaved() {
        val saved = store.read()
        if (saved.isEmpty()) return
        log.v("retry ${saved.size} saved page views")
        if (upload(saved)) store.remove(saved)
    }

    /** @return true when the views must not be kept; false when backing off or the server asked to retry */
    private fun upload(views: List<PageView>): Boolean {
        val now = clock()
        if (now < nextAttemptAt) return false
        val payload = viewsPayload(appId, views.toJson(now))
        log.v(payload.toString())
        return when (val result = uploader.upload(payload)) {
            UploadResult.Done -> {
                failures = 0
                nextAttemptAt = 0
                true
            }
            is UploadResult.Retry -> {
                val backoff = result.retryAfterMs?.coerceAtMost(MAX_BACKOFF_MS)
                    ?: (MIN_BACKOFF_MS shl failures.coerceAtMost(MAX_DOUBLINGS)).coerceAtMost(MAX_BACKOFF_MS)
                failures++
                nextAttemptAt = now + backoff
                log.v("next upload in ${backoff / 1000} s")
                false
            }
        }
    }

    /** Must be called while holding [lock]. */
    private fun drain(): List<PageView> = batch.toList().also { batch.clear() }

    companion object {
        const val MIN_BACKOFF_MS = 60_000L
        const val MAX_BACKOFF_MS = 30 * 60_000L
        private const val MAX_DOUBLINGS = 5
    }
}
