package fr.clewig.asslib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRulesTest {

    @Test
    fun defaultsAre50ViewsAnd10Minutes() {
        val log = FakeLog()
        val config = AssConfig(endpoint = "https://x.io", appId = "a")
        assertEquals(BatchRules(50, 10 * 60_000L), batchRules(config.batchSize, config.batchMaxAgeMinutes, log))
        assertTrue(log.warnings.isEmpty())
    }

    @Test
    fun keepsValuesInRange() {
        val log = FakeLog()
        assertEquals(BatchRules(20, 2 * 60_000L), batchRules(20, 2, log))
        assertEquals(BatchRules(200, 60 * 60_000L), batchRules(200, 60, log))
        assertTrue(log.warnings.isEmpty())
    }

    @Test
    fun clampsValuesOutOfRangeWithAWarning() {
        val log = FakeLog()
        assertEquals(BatchRules(20, 60 * 60_000L), batchRules(5, 120, log))
        assertEquals(BatchRules(200, 2 * 60_000L), batchRules(1000, 0, log))
        assertEquals(4, log.warnings.size)
    }
}
