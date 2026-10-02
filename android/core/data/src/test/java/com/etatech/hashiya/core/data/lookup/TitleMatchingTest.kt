package com.etatech.hashiya.core.data.lookup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchingTest {
    @Test
    fun ignoresCasePunctuationAndSpacing() {
        assertTrue(
            titlesMatch(
                "BERT: Pre-training of Deep  Bidirectional Transformers for Language Understanding.",
                "bert pre training of deep bidirectional transformers for language understanding"
            )
        )
    }

    @Test
    fun ignoresQuoteStyles() {
        assertTrue(titlesMatch("Don’t Stop Pretraining", "Don't stop pretraining"))
    }

    @Test
    fun ignoresAccentEncodingAndAccents() {
        val composed = "Schr\u00f6dinger Equations"
        val decomposed = "Schro\u0308dinger equations"
        assertTrue(titlesMatch(composed, decomposed))
        assertTrue(titlesMatch(composed, "Schrodinger equations"))
    }

    @Test
    fun worksForNonLatinTitles() {
        assertTrue(titlesMatch("تعلم الآلة", "تعلم الآلة."))
    }

    @Test
    fun differentTitlesDoNotMatch() {
        assertFalse(
            titlesMatch(
                "AI-Assisted Pipeline for Dynamic Generation of Trustworthy Health Supplement Content at Scale",
                "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"
            )
        )
    }

    @Test
    fun emptyTitlesNeverMatch() {
        assertFalse(titlesMatch("", ""))
        assertFalse(titlesMatch("!!!", "..."))
    }
}
