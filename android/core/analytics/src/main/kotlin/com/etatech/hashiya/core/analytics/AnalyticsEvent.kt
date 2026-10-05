package com.etatech.hashiya.core.analytics

/** The events of the analytics spec §4, with their closed parameters. */
sealed interface AnalyticsEvent {
    val name: String
    val parameters: Map<String, String>

    data class Search(
        val kind: SearchKind,
        val hasFilters: Boolean,
        val route: SearchRoute,
        val results: ResultsBucket,
        val category: ResearchCategory?
    ) : AnalyticsEvent {
        override val name = "search"
        override val parameters: Map<String, String>
            get() = buildMap {
                put("kind", kind.id)
                put("has_filters", yesNo(hasFilters))
                put("route", route.id)
                put("results_bucket", results.id)
                category?.let { put("category", it.id) }
            }
    }

    /** [page] 2…40; others are sent clamped. */
    data class SearchMore(val page: Int) : AnalyticsEvent {
        override val name = "search_more"
        override val parameters get() = mapOf("page" to page.coerceIn(2, 40).toString())
    }

    data class SearchLimitReached(val kind: LimitKind) : AnalyticsEvent {
        override val name = "search_limit_reached"
        override val parameters get() = mapOf("kind" to kind.id)
    }

    data class PaperSaved(val from: SaveSource) : AnalyticsEvent {
        override val name = "paper_saved"
        override val parameters get() = mapOf("from" to from.id)
    }

    data object PaperRemoved : AnalyticsEvent {
        override val name = "paper_removed"
        override val parameters = emptyMap<String, String>()
    }

    data object NoteEdited : AnalyticsEvent {
        override val name = "note_edited"
        override val parameters = emptyMap<String, String>()
    }

    data object CollectionCreated : AnalyticsEvent {
        override val name = "collection_created"
        override val parameters = emptyMap<String, String>()
    }

    data object PaperAddedToCollection : AnalyticsEvent {
        override val name = "paper_added_to_collection"
        override val parameters = emptyMap<String, String>()
    }

    data class Export(val format: ExportFormat, val withPdfs: Boolean) : AnalyticsEvent {
        override val name = "export"
        override val parameters get() = mapOf("format" to format.id, "with_pdfs" to yesNo(withPdfs))
    }

    data class Restore(val succeeded: Boolean) : AnalyticsEvent {
        override val name = "restore"
        override val parameters get() = mapOf("result" to if (succeeded) "ok" else "failed")
    }

    data class PdfOpened(val source: PdfOrigin) : AnalyticsEvent {
        override val name = "pdf_opened"
        override val parameters get() = mapOf("source" to source.id)
    }

    data class PdfDownloaded(val succeeded: Boolean) : AnalyticsEvent {
        override val name = "pdf_downloaded"
        override val parameters get() = mapOf("result" to if (succeeded) "ok" else "failed")
    }

    data class ScreenView(val screen: Screen) : AnalyticsEvent {
        override val name = "screen_view"
        override val parameters get() = mapOf("screen" to screen.id)
    }
}

private fun yesNo(value: Boolean) = if (value) "yes" else "no"
