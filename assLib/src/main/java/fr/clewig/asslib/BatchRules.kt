package fr.clewig.asslib

/**
 * When the in-memory batch is sent: once it holds [size] views, or when a view arrives and
 * the batch is older than [maxAgeMs]. It is sent anyway when the app goes to background.
 * Fewer, larger uploads save battery: each one wakes the radio, which costs more than the bytes.
 */
internal data class BatchRules(val size: Int = DEFAULT_SIZE, val maxAgeMs: Long = DEFAULT_MINUTES * MINUTE_MS) {
    companion object {
        const val DEFAULT_SIZE = 50
        const val MIN_SIZE = 20
        const val MAX_SIZE = 200
        const val DEFAULT_MINUTES = 10
        const val MIN_MINUTES = 2
        const val MAX_MINUTES = 60
        const val MINUTE_MS = 60_000L
    }
}

/** Rules from [AssConfig]; a value out of range is brought back to the nearest bound, with a warning. */
internal fun batchRules(size: Int, minutes: Int, log: AssLog): BatchRules {
    val keptSize = size.coerceIn(BatchRules.MIN_SIZE, BatchRules.MAX_SIZE)
    if (keptSize != size) {
        log.w("batchSize $size is out of ${BatchRules.MIN_SIZE}..${BatchRules.MAX_SIZE}: $keptSize is used")
    }
    val keptMinutes = minutes.coerceIn(BatchRules.MIN_MINUTES, BatchRules.MAX_MINUTES)
    if (keptMinutes != minutes) {
        log.w("batchMaxAgeMinutes $minutes is out of ${BatchRules.MIN_MINUTES}..${BatchRules.MAX_MINUTES}: $keptMinutes is used")
    }
    return BatchRules(keptSize, keptMinutes * BatchRules.MINUTE_MS)
}
