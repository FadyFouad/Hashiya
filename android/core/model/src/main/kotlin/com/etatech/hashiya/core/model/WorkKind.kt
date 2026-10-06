package com.etatech.hashiya.core.model

import java.util.Locale

/** What kind of work a paper is, from OpenAlex's work and source types; BibTeX and the citation styles share it. */
enum class WorkKind { Article, Conference, Chapter, Book, Thesis, Report, Preprint, Other }

private val JOURNAL_WORK_TYPES = setOf("article", "review", "letter", "editorial")

/** First match wins: a conference article is a conference paper, a repository article a preprint. */
fun PublicationDetails.workKind(): WorkKind {
    val work = workType?.lowercase(Locale.ROOT)
    val source = sourceType?.lowercase(Locale.ROOT)
    return when {
        source == "conference" -> WorkKind.Conference
        work == "book-chapter" -> WorkKind.Chapter
        work == "book" -> WorkKind.Book
        work == "dissertation" -> WorkKind.Thesis
        work == "report" -> WorkKind.Report
        work == "preprint" || source == "repository" -> WorkKind.Preprint
        work in JOURNAL_WORK_TYPES && source == "journal" -> WorkKind.Article
        else -> WorkKind.Other
    }
}
