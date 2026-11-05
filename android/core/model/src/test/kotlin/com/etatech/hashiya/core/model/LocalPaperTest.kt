package com.etatech.hashiya.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPaperTest {
    private fun paper(id: String) = Paper(id, null, "T", emptyList(), null, null, null, 0, false, null)

    @Test
    fun `a local id starts with the prefix`() {
        assertTrue(paper("local-3f2b").isLocal)
        assertTrue(isLocalPaperId("local-x"))
    }

    @Test
    fun `OpenAlex ids and null are not local`() {
        assertFalse(paper("W2741809807").isLocal)
        assertFalse(isLocalPaperId(null))
        assertFalse(isLocalPaperId("Wlocal-1"))
    }
}
