package com.etatech.hashiya.core.testing

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The fake must behave like the real repository, or feature tests prove nothing. */
class FakeLibraryRepositoryTest {
    private val repository = FakeLibraryRepository()

    @Test
    fun behavesLikeRoomRepository() = runTest {
        repository.save(SamplePapers.attention)
        repository.save(SamplePapers.bert)
        repository.save(SamplePapers.attention)
        assertEquals(listOf(SamplePapers.bert, SamplePapers.attention), repository.observeSavedPapers().first())

        val removed = repository.remove(SamplePapers.attention.openAlexId)!!
        repository.restore(removed)
        assertEquals(listOf(SamplePapers.bert, SamplePapers.attention), repository.observeSavedPapers().first())
        assertEquals(
            setOf(SamplePapers.attention.openAlexId, SamplePapers.bert.openAlexId),
            repository.observeSavedIds().first()
        )
    }
}
