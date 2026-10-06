package com.etatech.hashiya.core.citation

/** A piece of a citation: plain or italic text. */
data class Run(val text: String, val italic: Boolean = false)

/** A formatted citation as runs, so each output (plain, HTML, RTF) can show the italics its own way. */
data class StyledCitation(val runs: List<Run>) {
    val plain: String get() = Rendering.plain(this)
}

/** Builds the runs, merging neighbours of the same kind. */
internal class CitationBuilder {
    private val runs = mutableListOf<Run>()

    fun text(s: String) = add(Run(s))

    fun italic(s: String) = add(Run(s, italic = true))

    fun runs(more: List<Run>) = more.forEach(::add)

    /** Ends a sentence: a full stop unless the text already ends with . ? or !. */
    fun endSentence() {
        val last = runs.lastOrNull()?.text?.lastOrNull()
        if (last != '.' && last != '?' && last != '!') text(".")
    }

    fun build() = StyledCitation(runs.toList())

    private fun add(run: Run) {
        if (run.text.isEmpty()) return
        val last = runs.lastOrNull()
        if (last != null && last.italic == run.italic) runs[runs.size - 1] = last.copy(text = last.text + run.text) else runs += run
    }
}
