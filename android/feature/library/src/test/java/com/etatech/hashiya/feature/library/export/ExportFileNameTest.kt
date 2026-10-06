package com.etatech.hashiya.feature.library.export

import com.etatech.hashiya.core.model.CitationStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileNameTest {
    @Test
    fun wholeLibrary() = assertEquals("hashiya-library.bib", exportFileName(null, CitationStyle.Bibtex))

    @Test
    fun collectionNameWithUnsafeCharactersReplaced() {
        assertEquals("Chapter 2.bib", exportFileName("Chapter 2", CitationStyle.Bibtex))
        assertEquals("a-b-c-d-e-f-g-h-i-j.bib", exportFileName("a/b\\c:d*e?f\"g<h>i|j", CitationStyle.Bibtex))
        assertEquals("tab-x.bib", exportFileName("tab\tx", CitationStyle.Bibtex))
        assertEquals("الفصل الثاني.bib", exportFileName("الفصل الثاني", CitationStyle.Bibtex))
    }

    @Test
    fun nothingLeftIsCollection() {
        assertEquals("-.bib", exportFileName("/", CitationStyle.Bibtex))
        assertEquals("collection.bib", exportFileName("   ", CitationStyle.Bibtex))
    }

    @Test
    fun namesPerStyle() {
        assertEquals("hashiya-library.bib", exportFileName(null, CitationStyle.Bibtex))
        assertEquals("hashiya-library – APA.rtf", exportFileName(null, CitationStyle.Apa))
        assertEquals("Thesis – IEEE.rtf", exportFileName("Thesis", CitationStyle.Ieee))
        assertEquals("a-b – APA.rtf", exportFileName("a/b", CitationStyle.Apa))
        assertEquals("collection – APA.rtf", exportFileName("  ", CitationStyle.Apa))
    }
}
