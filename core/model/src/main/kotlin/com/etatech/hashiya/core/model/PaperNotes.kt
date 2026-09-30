package com.etatech.hashiya.core.model

/** The fixed note template. Declaration order is the order on screen. */
enum class NoteSection { Summary, ResearchQuestion, Method, KeyFindings, Limitations, Thoughts }

/** The user's notes on a saved paper, one plain-text field per [NoteSection]. Text is kept exactly as typed. */
data class PaperNotes(
    val summary: String = "",
    val researchQuestion: String = "",
    val method: String = "",
    val keyFindings: String = "",
    val limitations: String = "",
    val thoughts: String = ""
) {
    operator fun get(section: NoteSection): String = when (section) {
        NoteSection.Summary -> summary
        NoteSection.ResearchQuestion -> researchQuestion
        NoteSection.Method -> method
        NoteSection.KeyFindings -> keyFindings
        NoteSection.Limitations -> limitations
        NoteSection.Thoughts -> thoughts
    }

    fun with(section: NoteSection, text: String): PaperNotes = when (section) {
        NoteSection.Summary -> copy(summary = text)
        NoteSection.ResearchQuestion -> copy(researchQuestion = text)
        NoteSection.Method -> copy(method = text)
        NoteSection.KeyFindings -> copy(keyFindings = text)
        NoteSection.Limitations -> copy(limitations = text)
        NoteSection.Thoughts -> copy(thoughts = text)
    }

    /** True when every section is blank (empty or whitespace only). Blank notes are not stored. */
    val isEmpty: Boolean get() = NoteSection.entries.all { this[it].isBlank() }
}
