package com.etatech.hashiya.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperCollectionTest {
    @Test
    fun namesAreTrimmedAndLimitedTo60Characters() {
        assertTrue(isValidCollectionName("Chapter 2"))
        assertTrue(isValidCollectionName("  x  "))
        assertTrue(isValidCollectionName("a".repeat(60)))
        assertFalse(isValidCollectionName("a".repeat(61)))
        assertFalse(isValidCollectionName(""))
        assertFalse(isValidCollectionName("   "))
    }

    @Test
    fun nameKeyIgnoresCaseAndSurroundingSpaces() {
        assertEquals("thesis refs", collectionNameKey("  Thesis Refs "))
        assertEquals(collectionNameKey("Thesis"), collectionNameKey(" thesis "))
        assertEquals("الفصل الثاني", collectionNameKey(" الفصل الثاني "))
    }

    @Test
    fun paperHasEmptyPublicationDetailsByDefault() {
        val paper = Paper("W1", null, "T", emptyList(), null, null, null, 0, false, null)
        assertEquals(PublicationDetails(), paper.publication)
    }
}
