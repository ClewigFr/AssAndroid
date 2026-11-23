package fr.clewig.asslib

import android.util.Log

/** The library's logs; an interface so the logic can be tested on the JVM. */
internal interface AssLog {
    val verbose: Boolean
    /** Only logged when [verbose]. */
    fun v(message: String)
    fun w(message: String)
    fun e(message: String, error: Throwable? = null)
}

internal class AndroidLog(override val verbose: Boolean) : AssLog {
    override fun v(message: String) {
        if (verbose) Log.v(TAG, message)
    }

    override fun w(message: String) {
        Log.w(TAG, message)
    }

    override fun e(message: String, error: Throwable?) {
        Log.e(TAG, message, error)
    }

    private companion object {
        const val TAG = "AnonymousSimpleStats"
    }
}
