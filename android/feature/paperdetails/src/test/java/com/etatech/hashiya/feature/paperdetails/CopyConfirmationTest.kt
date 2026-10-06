package com.etatech.hashiya.feature.paperdetails

import com.etatech.hashiya.core.model.CitationStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class CopyConfirmationTest {
    @Test
    fun incompleteAlwaysSaysSo() {
        for (style in CitationStyle.entries) {
            assertEquals(PaperDetailsMessage.CitationIncomplete, copyConfirmation(style, complete = false, sdkInt = 32))
            assertEquals(PaperDetailsMessage.CitationIncomplete, copyConfirmation(style, complete = false, sdkInt = 35))
        }
    }

    @Test
    fun copiedIsShownOnlyBelowAndroid13() {
        assertEquals(PaperDetailsMessage.ApaCopied, copyConfirmation(CitationStyle.Apa, complete = true, sdkInt = 32))
        assertEquals(PaperDetailsMessage.IeeeCopied, copyConfirmation(CitationStyle.Ieee, complete = true, sdkInt = 32))
        assertEquals(PaperDetailsMessage.BibTeXCopied, copyConfirmation(CitationStyle.Bibtex, complete = true, sdkInt = 32))
        for (style in CitationStyle.entries) assertEquals(null, copyConfirmation(style, complete = true, sdkInt = 33))
    }

    @Test
    fun theRememberedStyleComesFirst() {
        assertEquals(listOf(CitationStyle.Ieee, CitationStyle.Apa, CitationStyle.Bibtex), orderedStyles(CitationStyle.Ieee))
        assertEquals(listOf(CitationStyle.Apa, CitationStyle.Ieee, CitationStyle.Bibtex), orderedStyles(CitationStyle.Apa))
    }
}
