package com.etatech.hashiya.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperNotesTest {
    @Test
    fun sectionsAreInTemplateOrder() {
        assertEquals(
            listOf(
                NoteSection.Summary,
                NoteSection.ResearchQuestion,
                NoteSection.Method,
                NoteSection.KeyFindings,
                NoteSection.Limitations,
                NoteSection.Thoughts
            ),
            NoteSection.entries
        )
    }

    @Test
    fun everySectionReadsBackWhatWasWritten() {
        val notes = NoteSection.entries.fold(PaperNotes()) { acc, section -> acc.with(section, "text ${section.name}") }

        NoteSection.entries.forEach { section -> assertEquals("text ${section.name}", notes[section]) }
    }

    @Test
    fun withChangesOnlyItsSection() {
        val notes = PaperNotes(summary = "s", method = "m").with(NoteSection.Method, "new")

        assertEquals(PaperNotes(summary = "s", method = "new"), notes)
    }

    @Test
    fun textIsKeptExactlyAsTyped() {
        assertEquals("  two\nlines ", PaperNotes().with(NoteSection.Thoughts, "  two\nlines ")[NoteSection.Thoughts])
    }

    @Test
    fun blankSectionsAreEmpty() {
        assertTrue(PaperNotes().isEmpty)
        assertTrue(PaperNotes(summary = "  ", thoughts = "\n\t").isEmpty)
        assertFalse(PaperNotes(limitations = "small sample").isEmpty)
    }
}
