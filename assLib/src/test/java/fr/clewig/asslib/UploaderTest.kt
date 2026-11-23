package fr.clewig.asslib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploaderTest {

    private val log = FakeLog()

    @Test
    fun acceptedAndRejectedBatchesAreNotRetried() {
        assertTrue(isHandled(200, log))
        assertTrue(isHandled(206, log))
        assertTrue(isHandled(400, log))
    }

    @Test
    fun otherCodesAreRetried() {
        listOf(401, 404, 429, 500, 503).forEach { assertFalse("$it", isHandled(it, log)) }
    }

    @Test
    fun buildsTheViewsUrl() {
        assertEquals("https://stats.example.com/views", viewsUrl(" https://stats.example.com/ ").toString())
        assertNull(viewsUrl("not a url"))
        assertNull(viewsUrl("file:///etc/passwd"))
    }
}
