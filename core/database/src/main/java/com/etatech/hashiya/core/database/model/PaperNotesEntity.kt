package com.etatech.hashiya.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.etatech.hashiya.core.model.PaperNotes

/** A saved paper's notes. The row exists only while at least one section has text; deleting the paper deletes it. */
@Entity(
    tableName = "paper_notes",
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paper_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PaperNotesEntity(
    @PrimaryKey @ColumnInfo(name = "paper_id") val paperId: String,
    val summary: String,
    @ColumnInfo(name = "research_question") val researchQuestion: String,
    val method: String,
    @ColumnInfo(name = "key_findings") val keyFindings: String,
    val limitations: String,
    val thoughts: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

fun PaperNotes.asEntity(paperId: String, updatedAt: Long): PaperNotesEntity = PaperNotesEntity(
    paperId = paperId,
    summary = summary,
    researchQuestion = researchQuestion,
    method = method,
    keyFindings = keyFindings,
    limitations = limitations,
    thoughts = thoughts,
    updatedAt = updatedAt
)

fun PaperNotesEntity.asPaperNotes(): PaperNotes = PaperNotes(
    summary = summary,
    researchQuestion = researchQuestion,
    method = method,
    keyFindings = keyFindings,
    limitations = limitations,
    thoughts = thoughts
)
