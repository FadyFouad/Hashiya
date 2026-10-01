package com.etatech.hashiya.feature.reader.share

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfShareTest {
    @Test
    fun theIntentSendsThePdfWithReadPermission() {
        val uri = Uri.parse("content://com.etatech.hashiya.pdfs/pdfs/paper.pdf")

        val intent = pdfShareIntent(uri, "Attention Is All You Need")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("Attention Is All You Need", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
