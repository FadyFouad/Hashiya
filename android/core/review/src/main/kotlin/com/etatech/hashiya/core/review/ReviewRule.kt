package com.etatech.hashiya.core.review

/** What the rating rule reads. Kept on the device; it never leaves it. */
data class ReviewCounters(
    /** When the app was first opened, epoch millis; null before the first open was recorded. */
    val firstOpenedAt: Long?,
    /** Papers saved since install. Removing a paper doesn't lower it. */
    val saves: Int,
    /** Whether a BibTeX export ever finished. */
    val exported: Boolean,
    /** When the app last asked the system for its rating prompt; null if never. */
    val lastAskedAt: Long?
)

/**
 * When to ask for a rating: once the app has shown its value (5 saves or a reference export), the person has had it for 3 days,
 * and it hasn't asked in 120 days. A stored time later than now (the clock went back) reads as "not yet".
 */
object ReviewRule {
    const val MIN_SAVES = 5
    const val SETTLE_MILLIS = 3L * 24 * 60 * 60 * 1000
    const val GAP_MILLIS = 120L * 24 * 60 * 60 * 1000

    fun shouldAsk(counters: ReviewCounters, now: Long): Boolean {
        val firstOpenedAt = counters.firstOpenedAt ?: return false
        if (counters.saves < MIN_SAVES && !counters.exported) return false
        if (now - firstOpenedAt < SETTLE_MILLIS) return false
        val lastAskedAt = counters.lastAskedAt ?: return true
        return now - lastAskedAt >= GAP_MILLIS
    }
}
