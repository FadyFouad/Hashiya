package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.data.FakeArxivDataSource
import com.etatech.hashiya.core.data.FakeOpenAlexLookupDataSource
import com.etatech.hashiya.core.data.lookup.arxivLandingPageFilter
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.model.NetworkWork
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAlexPaperLookupRepositoryTest {
    private val openAlex = FakeOpenAlexLookupDataSource()
    private val arxiv = FakeArxivDataSource()
    private val repository = OpenAlexPaperLookupRepository(openAlex, arxiv)

    private val bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"
    private val bert = PaperIdentifier.Arxiv("1810.04805")
    private val attention = PaperIdentifier.Arxiv("1706.03762")

    private fun work(id: String, title: String) = NetworkWork(id = "https://openalex.org/$id", displayName = title)

    @Test
    fun doiIsLookedUpDirectly() = runTest {
        openAlex.works = mapOf("doi:10.1038/nature14539" to work("W2919115771", "Deep learning"))

        val result = repository.lookup(PaperIdentifier.Doi("10.1038/nature14539"))

        assertEquals("W2919115771", (result as LookupResult.Found).paper.openAlexId)
        assertTrue(openAlex.findRequests.isEmpty())
        assertTrue(arxiv.requests.isEmpty())
    }

    @Test
    fun unknownDoiIsNotFound() = runTest {
        assertEquals(LookupResult.NotFound(arxivTitle = null), repository.lookup(PaperIdentifier.Doi("10.9999/nothing")))
    }

    @Test
    fun offlineDoiLookupFails() = runTest {
        openAlex.getFailure = NetworkFailure.Connectivity
        assertEquals(LookupResult.Failed(SearchError.Offline), repository.lookup(PaperIdentifier.Doi("10.1038/nature14539")))
    }

    @Test
    fun rejectedUserKeyFails() = runTest {
        openAlex.getFailure = NetworkFailure.Http(code = 401, usedUserKey = true)
        assertEquals(LookupResult.Failed(SearchError.InvalidUserKey), repository.lookup(PaperIdentifier.Doi("10.1038/nature14539")))
    }

    @Test
    fun arxivDoiMatchIsTrusted() = runTest {
        openAlex.works = mapOf("doi:10.48550/arXiv.2310.06825" to work("W4387561528", "Mistral 7B"))

        val result = repository.lookup(PaperIdentifier.Arxiv("2310.06825"))

        assertEquals("W4387561528", (result as LookupResult.Found).paper.openAlexId)
        assertTrue(openAlex.findRequests.isEmpty())
        assertTrue(arxiv.requests.isEmpty())
    }

    @Test
    fun fallbackUsesTheLandingPageFilterAndChecksTheTitle() = runTest {
        openAlex.found = listOf(work("W2626778328", "Attention Is All You Need"))
        arxiv.titles = mapOf("1706.03762" to "Attention Is All You Need")

        val result = repository.lookup(attention)

        assertEquals("W2626778328", (result as LookupResult.Found).paper.openAlexId)
        assertEquals(listOf("doi:10.48550/arXiv.1706.03762"), openAlex.workRequests)
        assertEquals(listOf(arxivLandingPageFilter("1706.03762") to 2), openAlex.findRequests)
        assertEquals(listOf("1706.03762"), arxiv.requests)
    }

    @Test
    fun fallbackWithTheWrongTitleIsNotFound() = runTest {
        openAlex.found = listOf(work("W2896457183", "AI-Assisted Pipeline for Dynamic Generation of Trustworthy Health Supplement Content"))
        arxiv.titles = mapOf("1810.04805" to bertTitle)

        assertEquals(LookupResult.NotFound(arxivTitle = bertTitle), repository.lookup(bert))
    }

    @Test
    fun fallbackWithoutMatchesOffersTheArxivTitle() = runTest {
        arxiv.titles = mapOf("1810.04805" to bertTitle)
        assertEquals(LookupResult.NotFound(arxivTitle = bertTitle), repository.lookup(bert))
    }

    @Test
    fun fallbackWithoutMatchesAndArxivDownIsPlainNotFound() = runTest {
        arxiv.failure = NetworkFailure.Connectivity
        assertEquals(LookupResult.NotFound(arxivTitle = null), repository.lookup(bert))
    }

    @Test
    fun twoDifferentWorksAreNotFound() = runTest {
        openAlex.found = listOf(work("W1", bertTitle), work("W2", bertTitle))
        arxiv.titles = mapOf("1810.04805" to bertTitle)

        assertEquals(LookupResult.NotFound(arxivTitle = bertTitle), repository.lookup(bert))
    }

    @Test
    fun theSameWorkTwiceCountsAsOne() = runTest {
        openAlex.found = listOf(work("W1", bertTitle), work("W1", bertTitle))
        arxiv.titles = mapOf("1810.04805" to bertTitle)

        assertEquals("W1", (repository.lookup(bert) as LookupResult.Found).paper.openAlexId)
    }

    @Test
    fun crossCheckWhileArxivIsUnreachableIsUnavailable() = runTest {
        openAlex.found = listOf(work("W1", bertTitle))
        arxiv.failure = NetworkFailure.Connectivity

        assertEquals(LookupResult.Failed(SearchError.ServiceUnavailable), repository.lookup(bert))
    }

    @Test
    fun crossCheckWhenArxivErrorsIsUnavailable() = runTest {
        openAlex.found = listOf(work("W1", bertTitle))
        arxiv.failure = NetworkFailure.Http(code = 503, usedUserKey = false)
        assertEquals(LookupResult.Failed(SearchError.ServiceUnavailable), repository.lookup(bert))

        arxiv.failure = NetworkFailure.MalformedResponse
        assertEquals(LookupResult.Failed(SearchError.ServiceUnavailable), repository.lookup(bert))
    }

    @Test
    fun crossCheckWhenArxivHasNoSuchPaperIsNotFound() = runTest {
        openAlex.found = listOf(work("W1", bertTitle))
        assertEquals(LookupResult.NotFound(arxivTitle = null), repository.lookup(bert))
    }

    @Test
    fun rateLimitDuringTheFallbackFails() = runTest {
        openAlex.findFailure = NetworkFailure.Http(code = 429, usedUserKey = false)
        assertEquals(LookupResult.Failed(SearchError.RateLimited), repository.lookup(bert))
    }
}
