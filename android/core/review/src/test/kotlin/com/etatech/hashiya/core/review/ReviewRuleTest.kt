package com.etatech.hashiya.core.review

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRuleTest {
    private val day = 24L * 60 * 60 * 1000
    private val opened = 1_000L * day

    private fun counters(saves: Int = 0, exported: Boolean = false, lastAskedAt: Long? = null, firstOpenedAt: Long? = opened) =
        ReviewCounters(firstOpenedAt, saves, exported, lastAskedAt)

    @Test
    fun fiveSavesAfterThreeDaysAsks() = assertTrue(ReviewRule.shouldAsk(counters(saves = 5), opened + 3 * day))

    @Test
    fun fourSavesDoNot() = assertFalse(ReviewRule.shouldAsk(counters(saves = 4), opened + 30 * day))

    @Test
    fun oneExportIsEnough() = assertTrue(ReviewRule.shouldAsk(counters(exported = true), opened + 3 * day))

    @Test
    fun notBeforeThreeDays() = assertFalse(ReviewRule.shouldAsk(counters(saves = 9), opened + 3 * day - 1))

    @Test
    fun neverOpenedNeverAsks() = assertFalse(ReviewRule.shouldAsk(counters(saves = 9, firstOpenedAt = null), opened + 30 * day))

    @Test
    fun waitsHundredTwentyDaysAfterAsking() {
        val asked = opened + 10 * day
        assertFalse(ReviewRule.shouldAsk(counters(saves = 9, lastAskedAt = asked), asked + 120 * day - 1))
        assertTrue(ReviewRule.shouldAsk(counters(saves = 9, lastAskedAt = asked), asked + 120 * day))
    }

    @Test
    fun aClockSetBackwardsNeverAsksEarly() {
        // First opened "in the future" (the clock went back): not settled yet.
        assertFalse(ReviewRule.shouldAsk(counters(saves = 9), opened - day))
        // Last asked "in the future": not 120 days yet.
        assertFalse(ReviewRule.shouldAsk(counters(saves = 9, lastAskedAt = opened + 50 * day), opened + 40 * day))
    }
}
