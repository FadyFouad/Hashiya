package com.etatech.hashiya.core.review

/** Asks for a store rating at good moments. Only `:app` knows the store's API. */
interface ReviewPrompt {
    /** The app opened; the first call starts the 3-day wait. */
    fun markOpened()

    /** A paper was saved from Search, Add by ID or Share. */
    fun recordSave()

    /** A reference export (BibTeX, APA or IEEE) finished writing its file. */
    fun recordExport()

    /** Nothing covers the screen now: asks for the system's rating prompt if [ReviewRule] says so. */
    fun askIfDue()
}

/** Debug builds and tests: counts nothing, never asks. */
object NoOpReviewPrompt : ReviewPrompt {
    override fun markOpened() = Unit

    override fun recordSave() = Unit

    override fun recordExport() = Unit

    override fun askIfDue() = Unit
}

/** Hands the request to whatever shows the store's prompt. */
fun interface ReviewRequester {
    fun request()
}

/** Where the counters live; `:app` keeps them in private preferences that aren't backed up. */
interface ReviewCounterStore {
    fun read(): ReviewCounters

    /** Stores [now] as the first open unless one is stored. */
    fun markOpened(now: Long)

    fun addSave()

    fun markExported()

    fun markAsked(now: Long)
}

/** For tests and previews. */
class InMemoryReviewCounterStore : ReviewCounterStore {
    private var counters = ReviewCounters(firstOpenedAt = null, saves = 0, exported = false, lastAskedAt = null)

    override fun read(): ReviewCounters = counters

    override fun markOpened(now: Long) {
        if (counters.firstOpenedAt == null) counters = counters.copy(firstOpenedAt = now)
    }

    override fun addSave() {
        counters = counters.copy(saves = counters.saves + 1)
    }

    override fun markExported() {
        counters = counters.copy(exported = true)
    }

    override fun markAsked(now: Long) {
        counters = counters.copy(lastAskedAt = now)
    }
}

/**
 * Counts and decides; "last asked" is stored when the request is made, since the store never says whether its prompt
 * appeared. Calls may come from any thread.
 */
class DefaultReviewPrompt(
    private val store: ReviewCounterStore,
    private val requester: ReviewRequester,
    private val now: () -> Long = System::currentTimeMillis
) : ReviewPrompt {
    private val lock = Any()

    override fun markOpened() = synchronized(lock) { store.markOpened(now()) }

    override fun recordSave() = synchronized(lock) { store.addSave() }

    override fun recordExport() = synchronized(lock) { store.markExported() }

    override fun askIfDue() {
        val ask = synchronized(lock) {
            val time = now()
            ReviewRule.shouldAsk(store.read(), time).also { if (it) store.markAsked(time) }
        }
        if (ask) requester.request()
    }
}
