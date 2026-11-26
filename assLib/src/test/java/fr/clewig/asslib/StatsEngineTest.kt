package fr.clewig.asslib

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class StatsEngineTest {

    @get:Rule val folder = TemporaryFolder()
    private val clock = FakeClock()
    private val log = FakeLog()
    private val sent = mutableListOf<JSONObject>()
    private var serverUp = true
    private val app = UUID.randomUUID()
    private val store by lazy { RetryStore(folder.root.resolve("retry.json"), clock, log) }

    private var attempts = 0
    private var retryAfterMs: Long? = null

    /** Runs each task right away: the test sees its effects synchronously. */
    private fun engine(rules: BatchRules = BatchRules()) = StatsEngine(
        appId = app,
        uploader = { payload ->
            attempts++
            if (serverUp) sent += payload
            if (serverUp) UploadResult.Done else UploadResult.Retry(retryAfterMs)
        },
        store = store,
        serial = { it.run() },
        clock = clock,
        log = log,
        rules = rules
    )

    private fun JSONObject.count() = getJSONArray("pageViews").length()

    @Test
    fun sendsOnceTheThresholdIsReached() {
        val engine = engine()
        repeat(BatchRules.DEFAULT_SIZE - 1) { engine.log(UUID.randomUUID()) }
        assertTrue(sent.isEmpty())
        engine.log(UUID.randomUUID())
        assertEquals(1, sent.size)
        assertEquals(BatchRules.DEFAULT_SIZE, sent[0].count())
        assertEquals(app.toString(), sent[0].getString("app"))
    }

    @Test
    fun sendsWhenTheOldestViewIsOldEnough() {
        val engine = engine()
        engine.log(UUID.randomUUID())
        clock.now += BatchRules().maxAgeMs + 1
        engine.log(UUID.randomUUID())
        assertEquals(1, sent.size)
        assertEquals(2, sent[0].count())
    }

    @Test
    fun failedBatchIsSavedThenResentWhenTheServerIsBack() {
        val engine = engine()
        serverUp = false
        repeat(BatchRules.DEFAULT_SIZE) { engine.log(UUID.randomUUID()) }
        assertEquals(BatchRules.DEFAULT_SIZE, store.read().size)

        serverUp = true
        clock.now += StatsEngine.MIN_BACKOFF_MS
        repeat(BatchRules.DEFAULT_SIZE) { engine.log(UUID.randomUUID()) }
        assertEquals(listOf(BatchRules.DEFAULT_SIZE, BatchRules.DEFAULT_SIZE), sent.map { it.count() })
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun flushSavesFirstAndForgetsOnceDelivered() {
        val engine = engine()
        engine.log(UUID.randomUUID())
        engine.flush()
        assertEquals(1, sent.size)
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun flushKeepsTheBatchWhenTheServerIsDown() {
        val engine = engine()
        serverUp = false
        engine.log(UUID.randomUUID())
        engine.flush()
        assertEquals(1, store.read().size)
    }

    @Test
    fun deliveredFlushAlsoResendsWhatWasSaved() {
        val engine = engine()
        serverUp = false
        engine.log(UUID.randomUUID())
        engine.flush()
        assertEquals(1, store.read().size)

        serverUp = true
        clock.now += StatsEngine.MIN_BACKOFF_MS
        engine.log(UUID.randomUUID())
        engine.flush()
        assertEquals(listOf(1, 1), sent.map { it.count() })
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun startResendsWhatThePreviousRunSaved() {
        store.add(listOf(PageView(UUID.randomUUID(), UUID.randomUUID(), clock.now - 5_000)))
        clock.now += 1_000
        engine().start()
        assertEquals(1, sent.size)
        assertEquals(6_000L, sent[0].getJSONArray("pageViews").getJSONObject(0).getLong("timestamp"))
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun eachViewIsSentOnlyOnce() {
        val engine = engine()
        engine.start()
        repeat(BatchRules.DEFAULT_SIZE * 2 + 3) { engine.log(UUID.randomUUID()) }
        engine.flush()
        val pages = sent.flatMap { p -> (0 until p.count()).map { p.getJSONArray("pageViews").getJSONObject(it).getString("page") } }
        assertEquals(BatchRules.DEFAULT_SIZE * 2 + 3, pages.toSet().size)
        assertEquals(pages.size, pages.toSet().size)
    }

    @Test
    fun backsOffAfterAFailureThenDoublesTheWait() {
        val engine = engine()
        serverUp = false
        repeat(BatchRules.DEFAULT_SIZE * 3) { engine.log(UUID.randomUUID()) }
        assertEquals(1, attempts) // the next two batches went straight to the file
        assertEquals(BatchRules.DEFAULT_SIZE * 3, store.read().size)

        clock.now += StatsEngine.MIN_BACKOFF_MS
        repeat(BatchRules.DEFAULT_SIZE) { engine.log(UUID.randomUUID()) }
        assertEquals(2, attempts)
        clock.now += StatsEngine.MIN_BACKOFF_MS // the wait is now 2 min
        repeat(BatchRules.DEFAULT_SIZE) { engine.log(UUID.randomUUID()) }
        assertEquals(2, attempts)
    }

    @Test
    fun followsRetryAfter() {
        val engine = engine()
        serverUp = false
        retryAfterMs = 5_000
        repeat(BatchRules.DEFAULT_SIZE) { engine.log(UUID.randomUUID()) }
        serverUp = true
        clock.now += 5_000
        repeat(BatchRules.DEFAULT_SIZE) { engine.log(UUID.randomUUID()) }
        assertEquals(3, attempts) // failed batch, new batch, then the saved one
        assertEquals(listOf(BatchRules.DEFAULT_SIZE, BatchRules.DEFAULT_SIZE), sent.map { it.count() })
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun flushDuringBackoffOnlySaves() {
        val engine = engine()
        serverUp = false
        repeat(BatchRules.DEFAULT_SIZE) { engine.log(UUID.randomUUID()) }
        engine.log(UUID.randomUUID())
        engine.flush()
        assertEquals(1, attempts)
        assertEquals(BatchRules.DEFAULT_SIZE + 1, store.read().size)
    }

    @Test
    fun customRulesSetTheBatchSizeAndAge() {
        val engine = engine(BatchRules(size = 20, maxAgeMs = 2 * BatchRules.MINUTE_MS))
        repeat(19) { engine.log(UUID.randomUUID()) }
        assertEquals(0, sent.size)
        engine.log(UUID.randomUUID())
        assertEquals(listOf(20), sent.map { it.count() })

        engine.log(UUID.randomUUID())
        clock.now += 2 * BatchRules.MINUTE_MS + 1
        engine.log(UUID.randomUUID())
        assertEquals(listOf(20, 2), sent.map { it.count() })
    }
}
