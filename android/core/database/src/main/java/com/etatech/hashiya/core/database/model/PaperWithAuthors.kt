package com.etatech.hashiya.core.database.model

import androidx.room.Embedded
import androidx.room.Relation

/** [authors] arrive in no particular order; sort by [PaperAuthorEntity.position]. */
data class PaperWithAuthors(
    @Embedded val paper: PaperEntity,
    @Relation(parentColumn = "id", entityColumn = "paper_id")
    val authors: List<PaperAuthorEntity>
)
