package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.review.ReviewPrompt

/** Counts every call, for tests. */
class FakeReviewPrompt : ReviewPrompt {
    var opened = 0
        private set
    var saves = 0
        private set
    var exports = 0
        private set
    var asks = 0
        private set

    override fun markOpened() {
        opened++
    }

    override fun recordSave() {
        saves++
    }

    override fun recordExport() {
        exports++
    }

    override fun askIfDue() {
        asks++
    }
}
