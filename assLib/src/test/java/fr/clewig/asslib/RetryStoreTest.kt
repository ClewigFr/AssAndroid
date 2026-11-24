package fr.clewig.asslib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class RetryStoreTest {

    @get:Rule val folder = TemporaryFolder()
    private val clock = FakeClock()
    private val session = UUID.randomUUID()
    private fun view(age: Long = 0) = PageView(UUID.randomUUID(), session, clock.now - age)

    @Test
    fun addAndRemove() {
        val file = folder.root.resolve("retry.json")
        val store = RetryStore(file, clock, FakeLog())
        val a = view()
        val b = view()
        store.add(listOf(a))
        store.add(listOf(b))
        assertEquals(listOf(a, b), store.read())
        store.remove(listOf(a, b))
        assertFalse(file.exists())
    }

    @Test
    fun writeLeavesNoTemporaryFile() {
        val store = RetryStore(folder.root.resolve("retry.json"), clock, FakeLog())
        store.add(listOf(view()))
        store.add(listOf(view()))
        assertEquals(2, store.read().size)
        assertEquals(listOf("retry.json"), folder.root.list()!!.toList())
    }

    @Test
    fun corruptFileIsReplaced() {
        val file = folder.root.resolve("retry.json").apply { writeText("{oops") }
        val store = RetryStore(file, clock, FakeLog())
        val a = view()
        store.add(listOf(a))
        assertEquals(listOf(a), store.read())
    }

    @Test
    fun capDropsOldViewsAndKeepsTheMostRecent() {
        val old = view(age = MAX_RETRY_AGE_MS + 1)
        val recent = (MAX_RETRY_VIEWS + 5 downTo 1).map { view(age = it.toLong()) }
        assertEquals(recent.takeLast(MAX_RETRY_VIEWS), capRetryBatch(listOf(old) + recent, clock.now))
    }
}
