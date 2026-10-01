package com.etatech.hashiya.feature.paperdetails

import org.junit.Assert.assertEquals
import org.junit.Test

class CopyConfirmationTest {
    @Test
    fun incompleteAlwaysSaysSo() {
        assertEquals(PaperDetailsMessage.BibTeXIncomplete, copyConfirmation(complete = false, sdkInt = 32))
        assertEquals(PaperDetailsMessage.BibTeXIncomplete, copyConfirmation(complete = false, sdkInt = 35))
    }

    @Test
    fun copiedIsShownOnlyBelowAndroid13() {
        assertEquals(PaperDetailsMessage.BibTeXCopied, copyConfirmation(complete = true, sdkInt = 32))
        assertEquals(null, copyConfirmation(complete = true, sdkInt = 33))
    }
}
