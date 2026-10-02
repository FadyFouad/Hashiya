package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.searchableText

/**
 * The full-text index: one row per saved paper, keyed by [paperId] (the paper's local id, not indexed).
 * A standalone FTS table, because author names and notes live in other tables; [PaperDao] keeps it in step with them.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["paper_id"])
@Entity(tableName = "paper_search")
data class PaperSearchEntity(
    @ColumnInfo(name = "paper_id") val paperId: String,
    val title: String,
    /** Author names joined with spaces. */
    val authors: String,
    val abstract: String,
    val venue: String,
    /** Every note section joined with spaces ([notesSearchText]); empty when the paper has no notes. */
    val notes: String
)

/** The search row for a paper, every column passed through [searchableText]. Saves, restores and the 1 → 2 migration use it. */
fun searchEntityFor(
    paperId: String,
    title: String,
    authorNames: List<String>,
    abstract: String?,
    venue: String?,
    notes: PaperNotes? = null
): PaperSearchEntity = PaperSearchEntity(
    paperId = paperId,
    title = searchableText(title),
    authors = searchableText(authorNames.joinToString(" ")),
    abstract = searchableText(abstract.orEmpty()),
    venue = searchableText(venue.orEmpty()),
    notes = notes?.let(::notesSearchText).orEmpty()
)

/** The search column text for [notes]: every section joined with spaces, through [searchableText]; empty for empty notes. */
fun notesSearchText(notes: PaperNotes): String =
    if (notes.isEmpty) "" else searchableText(NoteSection.entries.joinToString(" ") { notes[it] })
