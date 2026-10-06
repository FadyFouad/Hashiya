package com.etatech.hashiya.core.citation

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.WorkKind
import com.etatech.hashiya.core.model.workKind

/** IEEE references, from the data a saved paper has (see the spec's table). */
object Ieee {
    fun format(paper: Paper): StyledCitation {
        val b = CitationBuilder()
        val d = paper.publication
        val kind = d.workKind()
        val venue = paper.venue.orNullIfBlank()
        val publisher = d.publisher.orNullIfBlank()
        val year = paper.year?.toString()
        val doi = paper.doi.orNullIfBlank()?.let { "doi: ${doiOf(it)}" }
        val pages = pageRange(d.firstPage, d.lastPage)?.let { if (isSinglePage(d.firstPage, d.lastPage)) "p. $it" else "pp. $it" }
        val title = paper.title.orNullIfBlank()

        val names = authors(paper.authors.map { it.name })
        if (names.isNotEmpty()) {
            b.runs(names)
            b.text(", ")
        }
        if (kind == WorkKind.Book) {
            b.italic(title ?: "Untitled")
            b.endSentence()
            val rest = listOfNotNull(publisher ?: venue, year, doi)
            if (rest.isNotEmpty()) {
                b.text(" " + rest.joinToString(", "))
                b.endSentence()
            }
        } else {
            val tail: List<List<Run>> = when (kind) {
                WorkKind.Article -> listOfNotNull(
                    venue?.let { listOf(Run(it, italic = true)) },
                    d.volume.orNullIfBlank()?.let { listOf(Run("vol. $it")) },
                    d.issue.orNullIfBlank()?.let { listOf(Run("no. $it")) },
                    pages?.let { listOf(Run(it)) },
                    year?.let { listOf(Run(it)) },
                    doi?.let { listOf(Run(it)) }
                )

                WorkKind.Conference -> listOfNotNull(
                    venue?.let { listOf(Run("in "), Run(it, italic = true)) },
                    year?.let { listOf(Run(it)) },
                    pages?.let { listOf(Run(it)) },
                    doi?.let { listOf(Run(it)) }
                )

                WorkKind.Chapter -> listOfNotNull(
                    if (venue != null) {
                        listOf(Run("in "), Run(venue, italic = true)) +
                            (publisher?.let { p -> listOf(Run((if (venue.last() in ".?!") " " else ". ") + p)) } ?: emptyList())
                    } else {
                        publisher?.let { listOf(Run(it)) }
                    },
                    year?.let { listOf(Run(it)) },
                    pages?.let { listOf(Run(it)) },
                    doi?.let { listOf(Run(it)) }
                )

                WorkKind.Thesis -> listOfNotNull(
                    listOf(Run("Thesis")),
                    venue?.let {
                        listOf(Run(it))
                    },
                    year?.let { listOf(Run(it)) },
                    doi?.let { listOf(Run(it)) }
                )

                WorkKind.Report -> listOfNotNull(
                    (publisher ?: venue)?.let {
                        listOf(Run(it))
                    },
                    listOf(Run("Tech. Rep.")),
                    year?.let { listOf(Run(it)) },
                    doi?.let { listOf(Run(it)) }
                )

                else -> listOfNotNull(venue?.let { listOf(Run(it)) }, year?.let { listOf(Run(it)) }, doi?.let { listOf(Run(it)) })
            }
            val shown = title ?: "Untitled"
            val closing = if (shown.last() in ".?!") {
                ""
            } else if (tail.isEmpty()) {
                "."
            } else {
                ","
            }
            b.text("\"" + shown + closing + "\"")
            if (tail.isNotEmpty()) {
                b.text(" ")
                tail.forEachIndexed { i, part ->
                    if (i > 0) b.text(", ")
                    b.runs(part)
                }
                b.endSentence()
            }
        }
        if (doi == null) paper.openAccessPdfUrl.orNullIfBlank()?.let { b.text(" [Online]. Available: $it") }
        return b.build()
    }

    /** Numbered [1], [2], … in the order given (the library's saved order). */
    fun list(papers: List<Paper>): List<StyledCitation> =
        papers.mapIndexed { i, p -> StyledCitation(listOf(Run("[${i + 1}] ")) + format(p).runs).merged() }

    internal fun authors(names: List<String>): List<Run> {
        val written = names.map { name -> personName(name).let { p -> p.initials?.let { "$it ${p.family}" } ?: p.family } }
        return when {
            written.isEmpty() -> emptyList()
            written.size == 1 -> listOf(Run(written[0]))
            written.size == 2 -> listOf(Run("${written[0]} and ${written[1]}"))
            written.size <= 6 -> listOf(Run(written.dropLast(1).joinToString(", ") + ", and " + written.last()))
            else -> listOf(Run("${written[0]} "), Run("et al.", italic = true))
        }
    }

    private fun StyledCitation.merged(): StyledCitation = CitationBuilder().apply { runs(this@merged.runs) }.build()
}
