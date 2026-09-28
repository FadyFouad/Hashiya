package com.etatech.hashiya.core.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {
    @Test
    fun eachWordBecomesAQuotedPrefixTerm() {
        assertEquals("\"transf*\"", ftsMatch("transf"))
        assertEquals("\"deep*\" \"learning*\"", ftsMatch("  Deep   LEARNING "))
    }

    @Test
    fun normalizesLikeTheIndex() {
        assertEquals("\"schrodinger*\"", ftsMatch("Schrödinger"))
        assertEquals("\"التعلم*\"", ftsMatch("التَّعلُّم"))
        assertEquals("\"احمد*\"", ftsMatch("أحمد"))
        assertEquals("\"19*\"", ftsMatch("١٩"))
    }

    @Test
    fun ftsSyntaxBecomesPlainWords() {
        assertEquals("\"c*\"", ftsMatch("C++"))
        assertEquals("\"attention*\"", ftsMatch("\"attention"))
        assertEquals("\"title*\" \"deep*\"", ftsMatch("title:deep"))
        assertEquals("\"bert*\" \"gpt*\"", ftsMatch("-bert (gpt*)"))
        assertEquals("\"ming*\" \"wei*\"", ftsMatch("Ming-Wei"))
    }

    @Test
    fun operatorWordsAreSearchedAsWords() {
        assertEquals("\"cats*\" \"and*\" \"dogs*\"", ftsMatch("cats AND dogs"))
        assertEquals("\"or*\" \"not*\" \"near*\"", ftsMatch("OR NOT NEAR"))
    }

    @Test
    fun blankOrPunctuationOnlyMeansNoSearch() {
        assertNull(ftsMatch(""))
        assertNull(ftsMatch("   "))
        assertNull(ftsMatch("\"*-():"))
        assertNull(ftsMatch("ـــ"))
    }
}
