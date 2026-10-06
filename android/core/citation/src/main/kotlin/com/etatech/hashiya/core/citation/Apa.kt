package com.etatech.hashiya.core.citation

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.WorkKind
import com.etatech.hashiya.core.model.workKind

/** APA 7 references, from the data a saved paper has (see the spec's table). */
object Apa {
    private val ITALIC_TITLE = setOf(WorkKind.Book, WorkKind.Thesis, WorkKind.Report, WorkKind.Preprint, WorkKind.Other)

    fun format(paper: Paper): StyledCitation {
        val b = CitationBuilder()
        val kind = paper.publication.workKind()
        val venue = paper.venue.orNullIfBlank()
        val year = "(${paper.year ?: "n.d."})."
        if (paper.authors.isEmpty()) {
            title(b, paper, kind, venue)
            b.text(" $year")
        } else {
            b.text(authors(paper.authors.map { it.name }))
            b.text(" $year ")
            title(b, paper, kind, venue)
        }
        source(b, paper, kind, venue)
        link(paper)?.let { b.text(" $it") }
        return b.build()
    }

    /** Sorted by first author's family name (no authors: the title), then year (none last), then title. */
    fun list(papers: List<Paper>): List<StyledCitation> = papers.sortedWith(
        compareBy<Paper> { sortKey(it.authors.firstOrNull()?.let { a -> personName(a.name).family } ?: it.title) }
            .thenBy { it.year == null }
            .thenBy { it.year }
            .thenBy { sortKey(it.title) }
    ).map(::format)

    internal fun authors(names: List<String>): String {
        val written = names.map { name -> personName(name).let { p -> p.initials?.let { "${p.family}, $it" } ?: p.family } }
        return when {
            written.size == 1 -> written[0]
            written.size <= 20 -> written.dropLast(1).joinToString(", ") + ", & " + written.last()
            else -> written.take(19).joinToString(", ") + ", . . . " + written.last()
        }
    }

    private fun title(b: CitationBuilder, paper: Paper, kind: WorkKind, venue: String?) {
        val title = paper.title.orNullIfBlank()
        when {
            title == null -> b.text("[Untitled]")
            kind in ITALIC_TITLE -> b.italic(title)
            else -> b.text(title)
        }
        when (kind) {
            WorkKind.Thesis -> b.text(" [Thesis" + (venue?.let { ", $it" } ?: "") + "]")
            WorkKind.Preprint -> b.text(" [Preprint]")
            else -> Unit
        }
        b.endSentence()
    }

    private fun source(b: CitationBuilder, paper: Paper, kind: WorkKind, venue: String?) {
        val d = paper.publication
        val publisher = d.publisher.orNullIfBlank()
        val pages = pageRange(d.firstPage, d.lastPage)
        when (kind) {
            WorkKind.Article -> if (venue != null) {
                b.text(" ")
                b.italic(venue)
                d.volume.orNullIfBlank()?.let {
                    b.text(", ")
                    b.italic(it)
                }
                d.issue.orNullIfBlank()?.let { b.text("($it)") }
                pages?.let { b.text(", $it") }
                b.text(".")
            }

            WorkKind.Conference, WorkKind.Chapter -> {
                if (venue != null) {
                    b.text(" In ")
                    b.italic(venue)
                    pages?.let { b.text(if (isSinglePage(d.firstPage, d.lastPage)) " (p. $it)" else " (pp. $it)") }
                    b.text(".")
                }
                publisher?.let {
                    b.text(" $it.")
                }
            }

            WorkKind.Book -> publisher?.let { b.text(" $it.") }

            WorkKind.Thesis -> Unit

            WorkKind.Report -> (publisher ?: venue)?.let { b.text(" $it.") }

            WorkKind.Preprint, WorkKind.Other -> venue?.let { b.text(" $it.") }
        }
    }

    private fun link(paper: Paper): String? =
        paper.doi.orNullIfBlank()?.let { "https://doi.org/${doiOf(it)}" } ?: paper.openAccessPdfUrl.orNullIfBlank()
}
