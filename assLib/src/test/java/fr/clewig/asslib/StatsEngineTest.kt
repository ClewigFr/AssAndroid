package fr.clewig.asslib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    /** Unconfined runs each task right away: the test sees its effects synchronously. */
    private fun engine() = StatsEngine(
        appId = app,
        uploader = { payload -> if (serverUp) sent += payload; serverUp },
        store = store,
        serialScope = CoroutineScope(Dispatchers.Unconfined),
        clock = clock,
        log = log
    )

    private fun JSONObject.count() = getJSONArray("pageViews").length()

    @Test
    fun sendsOnceTheThresholdIsReached() {
        val engine = engine()
        repeat(StatsEngine.BATCH_THRESHOLD - 1) { engine.log(UUID.randomUUID()) }
        assertTrue(sent.isEmpty())
        engine.log(UUID.randomUUID())
        assertEquals(1, sent.size)
        assertEquals(StatsEngine.BATCH_THRESHOLD, sent[0].count())
        assertEquals(app.toString(), sent[0].getString("app"))
    }

    @Test
    fun sendsWhenTheOldestViewIsOldEnough() {
        val engine = engine()
        engine.log(UUID.randomUUID())
        clock.now += StatsEngine.MAX_BATCH_AGE_MS + 1
        engine.log(UUID.randomUUID())
        assertEquals(1, sent.size)
        assertEquals(2, sent[0].count())
    }

    @Test
    fun failedBatchIsSavedThenResentWhenTheServerIsBack() {
        val engine = engine()
        serverUp = false
        repeat(StatsEngine.BATCH_THRESHOLD) { engine.log(UUID.randomUUID()) }
        assertEquals(StatsEngine.BATCH_THRESHOLD, store.read().size)

        serverUp = true
        repeat(StatsEngine.BATCH_THRESHOLD) { engine.log(UUID.randomUUID()) }
        assertEquals(listOf(StatsEngine.BATCH_THRESHOLD, StatsEngine.BATCH_THRESHOLD), sent.map { it.count() })
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
        repeat(StatsEngine.BATCH_THRESHOLD * 2 + 3) { engine.log(UUID.randomUUID()) }
        engine.flush()
        val pages = sent.flatMap { p -> (0 until p.count()).map { p.getJSONArray("pageViews").getJSONObject(it).getString("page") } }
        assertEquals(StatsEngine.BATCH_THRESHOLD * 2 + 3, pages.toSet().size)
        assertEquals(pages.size, pages.toSet().size)
    }
}
