package com.etatech.hashiya.review

import com.etatech.hashiya.core.review.ReviewCounters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesReviewCounterStoreTest {
    private fun store() = SharedPreferencesReviewCounterStore(RuntimeEnvironment.getApplication())

    @Test
    fun startsEmpty() = assertEquals(ReviewCounters(null, 0, false, null), store().read())

    @Test
    fun keepsEverythingAcrossInstances() {
        store().apply {
            markOpened(10L)
            markOpened(20L)
            addSave()
            addSave()
            markExported()
            markAsked(30L)
        }
        assertEquals(ReviewCounters(firstOpenedAt = 10L, saves = 2, exported = true, lastAskedAt = 30L), store().read())
    }
}
