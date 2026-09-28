package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.data.mapping.asEntities
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.model.Paper
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomLibraryRepository(private val paperDao: PaperDao, private val now: () -> Long, private val newId: () -> String) :
    LibraryRepository {
    @Inject
    constructor(paperDao: PaperDao) : this(paperDao, System::currentTimeMillis, { UUID.randomUUID().toString() })

    override fun observeSavedPapers(): Flow<List<Paper>> = paperDao.observeSavedPapers().map { rows -> rows.map { it.asPaper() } }

    override fun observeSavedIds(): Flow<Set<String>> = paperDao.observeSavedOpenAlexIds().map { it.toSet() }

    override suspend fun save(paper: Paper) {
        val entities = paper.asEntities(localId = newId(), savedAt = now())
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors)
    }

    override suspend fun remove(openAlexId: String): RemovedPaper? = paperDao.deleteByOpenAlexId(openAlexId)?.let { row ->
        RemovedPaper(paper = row.asPaper(), localId = row.paper.id, savedAt = row.paper.savedAt)
    }

    override suspend fun restore(removed: RemovedPaper) {
        val entities = removed.paper.asEntities(localId = removed.localId, savedAt = removed.savedAt)
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors)
    }
}
