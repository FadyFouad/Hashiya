package com.etatech.hashiya.feature.library.export

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.etatech.hashiya.core.model.CitationStyle
import com.etatech.hashiya.feature.library.ReferenceExport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportFileTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun bib(name: String, content: String) =
        ReferenceExport(name, content, BIB_MIME_TYPE, complete = true, style = CitationStyle.Bibtex)

    @Test
    fun writesUtf8AndRemovesEarlierExports() {
        val dir = File(temp.root, "exports")
        writeExportFileTo(dir, bib("old.bib", "@misc{a,\n}\n"))

        val file = writeExportFileTo(dir, bib("الفصل.bib", "@misc{paper2019,\n  title = {تعلم}\n}\n"))

        assertEquals(listOf("الفصل.bib"), dir.list()!!.toList())
        assertEquals("@misc{paper2019,\n  title = {تعلم}\n}\n", file.readText(Charsets.UTF_8))
    }

    @Test
    fun shareIntentSendsTheFileWithReadPermission() {
        val uri = Uri.parse("content://com.etatech.hashiya.exports/exports/Thesis.bib")
        val intent = exportShareIntent(uri, bib("Thesis.bib", "@misc{a,\n}\n"))

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(BIB_MIME_TYPE, intent.type)
        assertEquals(uri, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun anRtfExportIsWrittenWithItsContentAndSharedAsRtf() {
        val dir = File(temp.root, "exports")
        val export = ReferenceExport("Thesis – APA.rtf", "{\\rtf1 caf\\u233?}", RTF_MIME_TYPE, complete = true, style = CitationStyle.Apa)

        val file = writeExportFileTo(dir, export)
        val uri = Uri.parse("content://com.etatech.hashiya.exports/exports/Thesis.rtf")
        val intent = exportShareIntent(uri, export)

        assertEquals("Thesis – APA.rtf", file.name)
        assertEquals("{\\rtf1 caf\\u233?}", file.readText(Charsets.UTF_8))
        assertEquals("application/rtf", intent.type)
        assertEquals("Thesis – APA.rtf", intent.getStringExtra(Intent.EXTRA_TITLE))
    }
}
