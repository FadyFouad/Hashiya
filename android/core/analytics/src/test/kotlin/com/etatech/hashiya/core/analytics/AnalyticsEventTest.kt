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
            AnalyticsEvent.ScreenView(Screen.Reader) to ("screen_view" to mapOf("screen" to "reader"))
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
}
