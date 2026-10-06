package com.etatech.hashiya.core.review

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultReviewPromptTest {
    private val day = 24L * 60 * 60 * 1000
    private var now = 1_000L * day
    private var requests = 0
    private val store = InMemoryReviewCounterStore()
    private val prompt = DefaultReviewPrompt(store, { requests++ }, { now })

    @Test
    fun asksOnceTheRuleHoldsAndRecordsWhen() {
        prompt.markOpened()
        repeat(5) { prompt.recordSave() }
        prompt.askIfDue()
        assertEquals(0, requests) // not settled in yet
        now += 3 * day
        prompt.askIfDue()
        assertEquals(1, requests)
        assertEquals(now, store.read().lastAskedAt)
        prompt.askIfDue()
        assertEquals(1, requests) // not again within 120 days
    }

    @Test
    fun markOpenedKeepsTheFirstTime() {
        prompt.markOpened()
        val first = now
        now += 10 * day
        prompt.markOpened()
        assertEquals(first, store.read().firstOpenedAt)
    }

    @Test
    fun anExportCounts() {
        prompt.markOpened()
        now += 3 * day
        prompt.recordExport()
        prompt.askIfDue()
        assertEquals(1, requests)
    }

    @Test
    fun noOpNeverAsks() {
        NoOpReviewPrompt.markOpened()
        repeat(9) { NoOpReviewPrompt.recordSave() }
        NoOpReviewPrompt.askIfDue()
        assertEquals(0, requests)
    }
}
