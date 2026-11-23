package fr.clewig.asslib

/** Collects the library's logs instead of calling android.util.Log. */
internal class FakeLog(override val verbose: Boolean = true) : AssLog {
    val warnings = mutableListOf<String>()
    override fun v(message: String) {}
    override fun w(message: String) {
        warnings += message
    }
    override fun e(message: String, error: Throwable?) {
        warnings += message
    }
}

/** A clock the test moves by hand. */
internal class FakeClock(var now: Long = 1_000_000_000L) : () -> Long {
    override fun invoke() = now
}
