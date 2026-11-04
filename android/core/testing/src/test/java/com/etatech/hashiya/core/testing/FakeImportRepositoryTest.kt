package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.model.ImportState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeImportRepositoryTest {
    private val repository = FakeImportRepository()

    @Test
    fun startConfirmCancelAndDismissMoveTheJobLikeTheRealOne() = runTest {
        val job = repository.start("thesis.bib", ByteArray(10))
        assertEquals(ImportState.Analyzing, repository.observeCurrentJob().first()?.state)
        repository.job.value = job.copy(state = ImportState.Ready)
        repository.confirm(job.id, addToCollection = false)
        assertEquals(ImportState.Importing, repository.job.value?.state)
        repository.dismiss(job.id)
        assertEquals(ImportState.Importing, repository.job.value?.state)
        repository.cancel(job.id)
        repository.dismiss(job.id)
        assertNull(repository.job.value)
        assertEquals(listOf("thesis.bib" to 10), repository.starts)
        assertEquals(listOf(job.id to false), repository.confirms)
    }
}
