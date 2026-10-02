package com.etatech.hashiya.core.network.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbstractRebuilderTest {
    @Test
    fun placesWordsByPosition() {
        val index = mapOf("world" to listOf(1), "Hello" to listOf(0))
        assertEquals("Hello world", rebuildAbstract(index))
    }

    @Test
    fun repeatsWordsThatAppearAtSeveralPositions() {
        val index = mapOf("the" to listOf(0, 3), "cat" to listOf(1), "saw" to listOf(2), "dog" to listOf(4))
        assertEquals("the cat saw the dog", rebuildAbstract(index))
    }

    @Test
    fun returnsNullWhenMissingOrEmpty() {
        assertNull(rebuildAbstract(null))
        assertNull(rebuildAbstract(emptyMap()))
    }
}
