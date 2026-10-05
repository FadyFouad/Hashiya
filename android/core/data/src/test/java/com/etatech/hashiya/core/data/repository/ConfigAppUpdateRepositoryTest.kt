package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.core.network.AppConfigDataSource
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.NetworkPlatformConfig
import com.etatech.hashiya.core.network.RemoteAppConfig
import com.etatech.hashiya.core.network.quota.OpenAlexLimits
import com.etatech.hashiya.core.testing.InMemoryQuotaPreferences
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigAppUpdateRepositoryTest {
    private class StubDataSource(private val answer: () -> RemoteAppConfig) : AppConfigDataSource {
        override suspend fun fetch(): RemoteAppConfig = answer()
    }

    private val quota = InMemoryQuotaPreferences()

    private fun repository(answer: () -> NetworkPlatformConfig?) =
        ConfigAppUpdateRepository(StubDataSource { RemoteAppConfig(answer(), OpenAlexLimits.Defaults) }, quota)

    @Test
    fun savesTheOpenAlexLimitsItFetched() = runTest {
        val limits = OpenAlexLimits(7, 2, null)
        ConfigAppUpdateRepository(StubDataSource { RemoteAppConfig(null, limits) }, quota).requiredUpdate(1)

        assertEquals(limits, quota.limits)
    }

    @Test
    fun aFailedFetchKeepsTheStoredLimits() = runTest {
        val stored = OpenAlexLimits(9, 3, null)
        quota.limits = stored
        ConfigAppUpdateRepository(StubDataSource { throw NetworkException(NetworkFailure.Connectivity) }, quota).requiredUpdate(1)

        assertEquals(stored, quota.limits)
    }

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
