package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.Paper

/** A saved paper ready to cite: its metadata and its stored key. */
data class CitablePaper(val paper: Paper, val citeKey: String)

private const val ARXIV_DOI_PREFIX = "10.48550/arxiv."

/** BibTeX splits authors on " and " and reads a comma as "Last, First", so names with either are kept whole in braces. */
private val SPLITS_AUTHOR = Regex("""(?i)\sand\s|,""")

object BibTeX {
    /** One entry, fields in a fixed order, empty ones left out, ending with a newline. */
    fun entry(paper: CitablePaper): String {
        val fields = fields(paper.paper)
        val body = if (fields.isEmpty()) "" else fields.joinToString(",\n", postfix = "\n") { (name, value) -> "  $name = {$value}" }
        return "@${entryType(paper.paper.publication).bibName}{${paper.citeKey},\n$body}\n"
    }

    /** Entries sorted by cite key, separated by one blank line, ending with a newline. Empty for no papers. */
    fun file(papers: List<CitablePaper>): String = papers.sortedBy { it.citeKey }.joinToString("\n") { entry(it) }

    private fun fields(paper: Paper): List<Pair<String, String>> {
        val details = paper.publication
        val type = entryType(details)
        fun text(value: String?) = value?.let(::cleanWhitespace)?.takeIf { it.isNotEmpty() }?.let(::escapeLatex)
        val doi = paper.doi?.trim()?.takeIf { it.isNotEmpty() }
        val firstPage = text(details.firstPage)
        val lastPage = text(details.lastPage)
        val arxivDoi = doi?.takeIf { it.startsWith(ARXIV_DOI_PREFIX, ignoreCase = true) }
        val eprint = arxivDoi?.substring(ARXIV_DOI_PREFIX.length)?.takeIf { it.isNotEmpty() }
        val url = paper.openAccessPdfUrl?.trim()?.takeIf { doi == null && it.isNotEmpty() }
        val authors = paper.authors.mapNotNull { author ->
            text(author.name)?.let { if (SPLITS_AUTHOR.containsMatchIn(it)) "{$it}" else it }
        }
        return listOfNotNull(
            authors.takeIf { it.isNotEmpty() }?.let { "author" to it.joinToString(" and ") },
            text(paper.title)?.let { "title" to protectCapitals(it) },
            paper.year?.let { "year" to it.toString() },
            type.venueField?.let { field ->
                text(paper.venue)?.let { venue ->
                    field to if (field == "journal" || field == "booktitle") protectCapitals(venue) else venue
                }
            },
            text(details.volume)?.let { "volume" to it },
            text(details.issue)?.let { "number" to it },
            firstPage?.let { "pages" to if (lastPage == null || lastPage == it) it else "$it--$lastPage" },
            text(details.publisher)?.takeIf { type.hasPublisher }?.let { "publisher" to it },
            doi?.let { "doi" to it },
            eprint?.let { "eprint" to it },
            eprint?.let { "archivePrefix" to "arXiv" },
            // Not escaped (styles pass it to \url), but braces are percent-encoded so they can't unbalance the entry.
            url?.let { "url" to it.replace("{", "%7B").replace("}", "%7D") }
        )
    }
}
