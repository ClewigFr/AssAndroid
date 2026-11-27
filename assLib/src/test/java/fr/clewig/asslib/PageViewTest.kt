package fr.clewig.asslib

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class PageViewTest {

    private val view = PageView(UUID.randomUUID(), UUID.randomUUID(), timestamp = 1_000_000L)

    @Test
    fun localFormatKeepsAbsoluteTimestamp() {
        assertEquals(view, JSONObject(view.toJsonLocal().toString()).toPageView())
    }

    @Test
    fun wireFormatSendsElapsedTime() {
        assertEquals(500L, view.toJson(now = 1_000_500L).getLong("timestamp"))
    }

    @Test
    fun clockMovedBackwardsSendsZero() {
        assertEquals(0L, view.toJson(now = 999_000L).getLong("timestamp"))
    }

    @Test
    fun batchRoundTrip() {
        val batch = listOf(view, view.copy(page = UUID.randomUUID()))
        val array = JSONObject(JSONObject().put("pageViews", batch.toJsonLocal()).toString())
            .getJSONArray("pageViews")
        assertEquals(batch, (0 until array.length()).map { array.getJSONObject(it).toPageView() })
    }

    @Test
    fun parsesOnlyCanonicalUuids() {
        val id = UUID.randomUUID()
        assertEquals(id, parseUuid(" $id "))
        assertEquals(id, parseUuid(id.toString().uppercase()))
        assertNull(parseUuid("not-a-uuid"))
        assertNull(parseUuid(""))
        assertNull(parseUuid("1-2-3-4-5"))
        assertNull(parseUuid("$id-0"))
    }

    @Test
    fun payloadCarriesTheAppId() {
        val app = UUID.randomUUID()
        val payload = JSONObject(viewsPayload(app, listOf(view).toJson()).toString())
        assertEquals(app.toString(), payload.getString("app"))
        assertEquals(1, payload.getJSONArray("pageViews").length())
    }
}
