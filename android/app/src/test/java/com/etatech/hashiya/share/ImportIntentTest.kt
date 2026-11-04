package com.etatech.hashiya.share

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportIntentTest {
    @Test
    fun openWithABibOrRisFileImportsIt() {
        IMPORT_MIME_TYPES.forEach { type ->
            assertEquals("content://files/a.bib", importUri(Intent.ACTION_VIEW, type, "content://files/a.bib", null, hasText = false))
        }
    }

    @Test
    fun sharingABibOrRisFileImportsIt() {
        assertEquals("content://f/1", importUri(Intent.ACTION_SEND, "application/x-research-info-systems", null, "content://f/1", false))
    }

    @Test
    fun sharingATextFileWithoutTextImportsIt() {
        assertEquals("content://f/1", importUri(Intent.ACTION_SEND, "text/plain", null, "content://f/1", hasText = false))
    }

    @Test
    fun sharedTextStillGoesToSearch() {
        assertNull(importUri(Intent.ACTION_SEND, "text/plain", null, null, hasText = true))
        assertNull(importUri(Intent.ACTION_SEND, "text/plain", null, "content://f/1", hasText = true))
    }

    @Test
    fun otherIntentsAreNotImports() {
        assertNull(importUri(Intent.ACTION_MAIN, null, null, null, hasText = false))
        assertNull(importUri(Intent.ACTION_VIEW, "text/html", "https://example.org", null, hasText = false))
        assertNull(importUri(Intent.ACTION_SEND, "image/png", null, "content://f/1", hasText = false))
    }
}
