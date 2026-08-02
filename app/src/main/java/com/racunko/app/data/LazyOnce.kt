package com.racunko.app.data

/**
 * Create-once discipline for storage setup (v1.3 Change 1/7): a successful
 * resolve is cached and NEVER re-run, so directory setup happens at most once
 * per session — never during per-file processing loops.
 *
 * Pure JVM (no Android imports) so the idempotence guarantee is unit-tested.
 */
class LazyOnce<T : Any>(private val resolve: () -> T?) {

    @Volatile
    private var cached: T? = null

    /** How many times [resolve] actually ran — asserted in tests (Change 7). */
    var resolveCalls: Int = 0
        private set

    @Synchronized
    fun get(): T? {
        cached?.let { return it }
        resolveCalls++
        return resolve()?.also { cached = it }
    }

    /** Drop the cache (e.g. the user picked a different tree). */
    @Synchronized
    fun reset() {
        cached = null
    }
}
