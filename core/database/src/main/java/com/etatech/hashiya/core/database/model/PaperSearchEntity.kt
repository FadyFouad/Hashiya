package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import com.etatech.hashiya.core.model.searchableText

/**
 * The full-text index: one row per saved paper, keyed by [paperId] (the paper's local id, not indexed).
 * A standalone FTS table, because author names live in another table; [PaperDao] keeps it in step with `papers`.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["paper_id"])
@Entity(tableName = "paper_search")
data class PaperSearchEntity(
    @ColumnInfo(name = "paper_id") val paperId: String,
    val title: String,
    /** Author names joined with spaces. */
    val authors: String,
    val abstract: String,
    val venue: String
)

/** The search row for a paper, every column passed through [searchableText]. Saves and the 1 → 2 migration both use it. */
fun searchEntityFor(paperId: String, title: String, authorNames: List<String>, abstract: String?, venue: String?): PaperSearchEntity =
    PaperSearchEntity(
        paperId = paperId,
        title = searchableText(title),
        authors = searchableText(authorNames.joinToString(" ")),
        abstract = searchableText(abstract.orEmpty()),
        venue = searchableText(venue.orEmpty())
    )
