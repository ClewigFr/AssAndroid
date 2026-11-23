package fr.clewig.asslib

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PageViewTest {

    private val view = PageView(UUID.randomUUID(), UUID.randomUUID(), timestamp = 1_000_000L)

    @Test
    fun localFormatKeepsAbsoluteTimestamp() {
        val restored = JSONObject(view.toJsonLocal().toString()).toPageView()
        assertEquals(view, restored)
    }

    @Test
    fun wireFormatSendsElapsedTime() {
        val before = System.currentTimeMillis()
        val elapsed = view.toJson().getLong("timestamp")
        val after = System.currentTimeMillis()
        assertTrue(elapsed in (before - view.timestamp)..(after - view.timestamp))
    }

    @Test
    fun batchRoundTrip() {
        val batch = listOf(view, view.copy(page = UUID.randomUUID()))
        val array = JSONObject(
            JSONObject().put("pageViews", PageViews(batch).toJsonLocal()).toString()
        ).getJSONArray("pageViews")
        val restored = (0 until array.length()).map { array.getJSONObject(it).toPageView() }
        assertEquals(batch, restored)
    }
}

class AssHolderTest {

    private class Thing(val a: String, val b: Int)
    private object Holder : AssHolder<Thing, String, Int>(::Thing)

    @Test
    fun createsOnlyOneInstance() {
        val first = Holder.getInstance("x", 1)
        val second = Holder.getInstance("y", 2)
        assertSame(first, second)
        assertEquals("x", second.a)
    }
}

class AppIdTest {

    @Test
    fun parsesValidAppId() {
        val id = UUID.randomUUID()
        assertEquals(id, parseAppId(id.toString()))
        assertEquals(id, parseAppId(" $id "))
    }

    @Test
    fun malformedOrMissingAppIdIsNull() {
        assertEquals(null, parseAppId("not-a-uuid"))
        assertEquals(null, parseAppId(null))
    }

    @Test
    fun payloadCarriesTheAppId() {
        val app = UUID.randomUUID()
        val views = PageViews(listOf(PageView(UUID.randomUUID(), UUID.randomUUID()))).toJson()
        val payload = JSONObject(viewsPayload(app, views).toString())
        assertEquals(app.toString(), payload.getString("app"))
        assertEquals(1, payload.getJSONArray("pageViews").length())
    }

    @Test
    fun payloadWithoutAppIdOmitsTheField() {
        val payload = viewsPayload(null, PageViews(emptyList()).toJson())
        assertTrue(!payload.has("app"))
    }
}

class RetryRulesTest {

    @Test
    fun futureTimestampSendsZeroElapsed() {
        val view = PageView(UUID.randomUUID(), UUID.randomUUID(), System.currentTimeMillis() + 60_000)
        assertEquals(0L, view.toJson().getLong("timestamp"))
    }

    @Test
    fun retryBatchDropsOldViewsAndKeepsTheMostRecent() {
        val now = 10_000_000_000L
        val session = UUID.randomUUID()
        val old = PageView(UUID.randomUUID(), session, now - MAX_RETRY_AGE_MS - 1)
        val recent = (1..MAX_RETRY_VIEWS + 5).map { PageView(UUID.randomUUID(), session, now - it) }.reversed()
        val kept = capRetryBatch(listOf(old) + recent, now)
        assertEquals(MAX_RETRY_VIEWS, kept.size)
        assertEquals(recent.takeLast(MAX_RETRY_VIEWS), kept)
    }
}

class AssHolderReuseTest {

    private class Thing(val a: String)
    private object Holder : AssHolder<Thing, String, Int>({ a, _ -> Thing(a) }) {
        val ignored = mutableListOf<String>()
        override fun onExistingInstance(instance: Thing, arg: String, arg2: Int) {
            ignored += arg
        }
    }

    @Test
    fun reportsIgnoredArguments() {
        Holder.getInstance("x", 1)
        Holder.getInstance("y", 2)
        assertEquals(listOf("y"), Holder.ignored)
    }
}
