package fr.clewig.asslib

/** A double-checked-locking singleton holder. */
@Deprecated("Not meant to be used by apps; will be removed in 0.4.0. Use AnonymousSimpleStatsManager.getInstance.")
public open class AssHolder<out T : Any, in A, in B>(creator: (A, B) -> T) {
    private var creator: ((A, B) -> T)? = creator
    @Volatile
    private var instance: T? = null

    /** Called when getInstance returns an instance created earlier; its arguments are ignored. */
    protected open fun onExistingInstance(instance: @UnsafeVariance T, arg: @UnsafeVariance A, arg2: @UnsafeVariance B) {}

    public fun getInstance(arg: A, arg2: B): T {
        val checkInstance = instance
        if (checkInstance != null) {
            onExistingInstance(checkInstance, arg, arg2)
            return checkInstance
        }

        return synchronized(this) {
            val checkInstanceAgain = instance
            if (checkInstanceAgain != null) {
                onExistingInstance(checkInstanceAgain, arg, arg2)
                checkInstanceAgain
            } else {
                val created = creator!!(arg, arg2)
                instance = created
                creator = null
                created
            }
        }
    }
}
