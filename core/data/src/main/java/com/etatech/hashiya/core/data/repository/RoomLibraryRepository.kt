package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.data.mapping.asEntities
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.data.mapping.readingStatusOf
import com.etatech.hashiya.core.data.mapping.storedValue
import com.etatech.hashiya.core.data.search.ftsMatch
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.ReadingStatus
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomLibraryRepository(private val paperDao: PaperDao, private val now: () -> Long, private val newId: () -> String) :
    LibraryRepository {
    @Inject
    constructor(paperDao: PaperDao) : this(paperDao, System::currentTimeMillis, { UUID.randomUUID().toString() })

    override fun observeLibrary(query: String, status: ReadingStatus?): Flow<List<LibraryPaper>> =
        paperDao.observeLibrary(ftsMatch(query), status?.storedValue).map { rows ->
            rows.map { LibraryPaper(it.asPaper(), readingStatusOf(it.paper.readingStatus)) }
        }

    override fun observeStatusCounts(query: String): Flow<Map<ReadingStatus, Int>> =
        paperDao.observeStatusCounts(ftsMatch(query)).map { rows ->
            // Unknown stored values read as To read, so they are counted there too.
            val counts = ReadingStatus.entries.associateWith { 0 }.toMutableMap()
            rows.forEach { row -> counts.merge(readingStatusOf(row.readingStatus), row.count, Int::plus) }
            counts
        }

    override fun observeSavedIds(): Flow<Set<String>> = paperDao.observeSavedOpenAlexIds().map { it.toSet() }

    override suspend fun save(paper: Paper) {
        val entities = paper.asEntities(localId = newId(), savedAt = now(), status = ReadingStatus.ToRead)
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search)
    }

    override suspend fun setStatus(openAlexId: String, status: ReadingStatus) {
        paperDao.setStatus(openAlexId, status.storedValue)
    }

    override suspend fun remove(openAlexId: String): RemovedPaper? = paperDao.deleteByOpenAlexId(openAlexId)?.let { row ->
        RemovedPaper(
            paper = row.asPaper(),
            localId = row.paper.id,
            savedAt = row.paper.savedAt,
            status = readingStatusOf(row.paper.readingStatus)
        )
    }

    override suspend fun restore(removed: RemovedPaper) {
        val entities = removed.paper.asEntities(localId = removed.localId, savedAt = removed.savedAt, status = removed.status)
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search)
    }
}
