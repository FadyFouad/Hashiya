package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.RemovedPaper
import com.etatech.hashiya.core.model.Paper
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeLibraryRepository : LibraryRepository {
    private val rows = MutableStateFlow<List<RemovedPaper>>(emptyList())
    private var clock = 0L

    /** When true, [save] throws like a failing disk would. */
    var failOnSave = false

    /** When true, [remove] throws like a failing disk would. */
    var failOnRemove = false

    override fun observeSavedPapers(): Flow<List<Paper>> = rows.map { list -> list.sortedByDescending { it.savedAt }.map { it.paper } }

    override fun observeSavedIds(): Flow<Set<String>> = rows.map { list -> list.map { it.paper.openAlexId }.toSet() }

    override suspend fun save(paper: Paper) {
        if (failOnSave) throw IOException("disk full")
        if (isSaved(paper.openAlexId)) return
        rows.update { it + RemovedPaper(paper, localId = "local-${paper.openAlexId}", savedAt = ++clock) }
    }

    override suspend fun remove(openAlexId: String): RemovedPaper? {
        if (failOnRemove) throw IOException("disk full")
        val row = rows.value.firstOrNull { it.paper.openAlexId == openAlexId } ?: return null
        rows.update { it - row }
        return row
    }

    override suspend fun restore(removed: RemovedPaper) {
        if (isSaved(removed.paper.openAlexId)) return
        rows.update { it + removed }
    }

    private fun isSaved(openAlexId: String) = rows.value.any { it.paper.openAlexId == openAlexId }
}
