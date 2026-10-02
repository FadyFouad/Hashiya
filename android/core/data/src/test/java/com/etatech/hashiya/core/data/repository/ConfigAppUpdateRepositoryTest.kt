package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.core.network.AppConfigDataSource
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.NetworkPlatformConfig
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigAppUpdateRepositoryTest {
    private class StubDataSource(private val answer: () -> NetworkPlatformConfig?) : AppConfigDataSource {
        override suspend fun androidConfig(): NetworkPlatformConfig? = answer()
    }

    private fun repository(answer: () -> NetworkPlatformConfig?) = ConfigAppUpdateRepository(StubDataSource(answer))

    @Test
    fun aBuildBelowTheMinimumMustUpdate() = runTest {
        assertEquals(RequiredUpdate(STORE), repository { NetworkPlatformConfig(5, STORE) }.requiredUpdate(4))
    }

    @Test
    fun theMinimumBuildItselfIsAllowed() = runTest {
        assertNull(repository { NetworkPlatformConfig(5, STORE) }.requiredUpdate(5))
    }

    @Test
    fun aNewerBuildIsAllowed() = runTest {
        assertNull(repository { NetworkPlatformConfig(5, STORE) }.requiredUpdate(6))
    }

    @Test
    fun noAndroidEntryMeansNoBlock() = runTest {
        assertNull(repository { null }.requiredUpdate(1))
    }

    @Test
    fun noMinimumMeansNoBlock() = runTest {
        assertNull(repository { NetworkPlatformConfig(null, STORE) }.requiredUpdate(1))
    }

    @Test
    fun noStoreLinkMeansNoBlock() = runTest {
        assertNull(repository { NetworkPlatformConfig(5, null) }.requiredUpdate(1))
    }

    @Test
    fun aStoreLinkThatIsNotHttpsMeansNoBlock() = runTest {
        for (link in listOf(
            "http://play.google.com/store/apps/details?id=com.etatech.hashiya",
            "",
            "play store",
            "https://",
            "https:///store"
        )) {
            assertNull(link, repository { NetworkPlatformConfig(5, link) }.requiredUpdate(1))
        }
    }

    @Test
    fun aNetworkFailureMeansNoBlock() = runTest {
        assertNull(repository { throw NetworkException(NetworkFailure.Connectivity) }.requiredUpdate(1))
    }

    @Test
    fun anUnexpectedErrorMeansNoBlock() = runTest {
        assertNull(repository { throw IllegalStateException("boom") }.requiredUpdate(1))
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowed() = runTest {
        repository { throw CancellationException("left the screen") }.requiredUpdate(1)
    }

    private companion object {
        const val STORE = "https://play.google.com/store/apps/details?id=com.etatech.hashiya"
    }
}
