package fr.clewig.asslib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AssHolderTest {

    private class Thing(val a: String)
    private object Holder : AssHolder<Thing, String, Int>({ a, _ -> Thing(a) }) {
        val ignored = mutableListOf<String>()
        override fun onExistingInstance(instance: Thing, arg: String, arg2: Int) {
            ignored += arg
        }
    }

    @Test
    fun createsOneInstanceAndReportsIgnoredArguments() {
        val first = Holder.getInstance("x", 1)
        val second = Holder.getInstance("y", 2)
        assertSame(first, second)
        assertEquals("x", second.a)
        assertEquals(listOf("y"), Holder.ignored)
    }
}
