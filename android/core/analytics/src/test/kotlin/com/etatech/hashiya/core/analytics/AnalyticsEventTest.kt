package com.etatech.hashiya.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsEventTest {
    @Test
    fun everyEventHasItsSpecNameAndClosedParameters() {
        val cases = listOf(
            AnalyticsEvent.Search(SearchKind.Keyword, true, SearchRoute.Shared, ResultsBucket.Over200, ResearchCategory.Ai) to
                (
                    "search" to mapOf(
                        "kind" to "keyword",
                        "has_filters" to "yes",
                        "route" to "shared",
                        "results_bucket" to "200+",
                        "category" to "ai"
                    )
                    ),
            AnalyticsEvent.Search(SearchKind.Doi, false, SearchRoute.User, ResultsBucket.UpTo25, null) to
                ("search" to mapOf("kind" to "doi", "has_filters" to "no", "route" to "user", "results_bucket" to "1-25")),
            AnalyticsEvent.SearchMore(2) to ("search_more" to mapOf("page" to "2")),
            AnalyticsEvent.SearchMore(99) to ("search_more" to mapOf("page" to "40")),
            AnalyticsEvent.SearchLimitReached(LimitKind.PageCap) to ("search_limit_reached" to mapOf("kind" to "page_cap")),
            AnalyticsEvent.PaperSaved(SaveSource.Share) to ("paper_saved" to mapOf("from" to "share")),
            AnalyticsEvent.PaperRemoved to ("paper_removed" to emptyMap()),
            AnalyticsEvent.NoteEdited to ("note_edited" to emptyMap()),
            AnalyticsEvent.CollectionCreated to ("collection_created" to emptyMap()),
            AnalyticsEvent.PaperAddedToCollection to ("paper_added_to_collection" to emptyMap()),
            AnalyticsEvent.Export(ExportFormat.Backup, true) to ("export" to mapOf("format" to "backup", "with_pdfs" to "yes")),
            AnalyticsEvent.Export(ExportFormat.Apa, false) to ("export" to mapOf("format" to "apa", "with_pdfs" to "no")),
            AnalyticsEvent.Export(ExportFormat.Ieee, false) to ("export" to mapOf("format" to "ieee", "with_pdfs" to "no")),
            AnalyticsEvent.Restore(false) to ("restore" to mapOf("result" to "failed")),
            AnalyticsEvent.PdfOpened(PdfOrigin.Attached) to ("pdf_opened" to mapOf("source" to "attached")),
            AnalyticsEvent.PdfDownloaded(true) to ("pdf_downloaded" to mapOf("result" to "ok")),
            AnalyticsEvent.ScreenView(Screen.Reader) to ("screen_view" to mapOf("screen" to "reader")),
            AnalyticsEvent.ScreenView(Screen.Import) to ("screen_view" to mapOf("screen" to "import")),
            AnalyticsEvent.Import(ImportFileFormat.Bibtex, ImportResult.Ok, LibrarySize.of(126), MatchedShare.of(98, 119), true) to
                (
                    "import" to mapOf(
                        "format" to "bibtex",
                        "result" to "ok",
                        "entries_bucket" to "51-500",
                        "matched" to "most",
                        "to_collection" to "yes"
                    )
                    ),
            AnalyticsEvent.Import(null, ImportResult.TooLarge, LibrarySize.of(0), MatchedShare.of(0, 0), false) to
                (
                    "import" to mapOf("result" to "too_large", "entries_bucket" to "0", "matched" to "none", "to_collection" to "no")
                    )
        )
        for ((event, expected) in cases) {
            assertEquals(expected.first, event.name)
            assertEquals(expected.second, event.parameters)
        }
    }

    @Test
    fun bucketsAndKeys() {
        assertEquals(
            listOf("0", "1-25", "1-25", "26-200", "26-200", "200+"),
            listOf(0L, 1L, 25L, 26L, 200L, 201L).map {
                ResultsBucket.of(it).id
            }
        )
        assertEquals(listOf("0", "1-50", "51-500", "501-5000", "5000+"), listOf(0, 50, 51, 5000, 5001).map { LibrarySize.of(it).id })
        assertEquals(listOf("en", "ar", "en", "system", "system"), listOf("en", "ar", "en-GB,ar", "fr", "").map { Language.of(it).id })
    }

    @Test
    fun theMatchedShareIsNoneSomeMostOrAll() {
        assertEquals(MatchedShare.None, MatchedShare.of(matched = 0, toImport = 10))
        assertEquals(MatchedShare.None, MatchedShare.of(matched = 0, toImport = 0))
        assertEquals(MatchedShare.Some, MatchedShare.of(matched = 4, toImport = 10))
        assertEquals(MatchedShare.Most, MatchedShare.of(matched = 5, toImport = 10))
        assertEquals(MatchedShare.All, MatchedShare.of(matched = 10, toImport = 10))
    }

    @Test
    fun importResultsAndFormatsAreClosedLists() {
        assertEquals(listOf("ok", "cancelled", "unsupported", "too_large", "unreadable", "nothing_new"), ImportResult.entries.map { it.id })
        assertEquals(listOf("bibtex", "ris"), ImportFileFormat.entries.map { it.id })
    }
}
