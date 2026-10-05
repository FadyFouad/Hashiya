package com.etatech.hashiya.analytics

import com.etatech.hashiya.core.analytics.AnalyticsProperty
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.testing.FakeAnalytics
import com.etatech.hashiya.core.testing.FakeLibraryBackup
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsStartupTest {
    private val analytics = FakeAnalytics()
    private val preferences = FakeUserPreferencesRepository()
    private val backup = FakeLibraryBackup()

    private fun startup(isRelease: Boolean = true, languageTag: String = "") =
        AnalyticsStartup(analytics, preferences, backup, { languageTag }, isRelease)

    @Test
    fun aReleaseBuildFollowsTheSwitchAndSetsTheProperties() = runTest {
        preferences.setAnalyticsEnabled(true)
        preferences.setUserApiKey("mine")
        backup.summary = BackupSummary(papers = 182, collections = 0, pdfCount = 0, pdfBytes = 0)

        startup(isRelease = true, languageTag = "ar").run()

        assertEquals(listOf(true), analytics.enabledCalls)
        assertEquals(
            mapOf(
                AnalyticsProperty.Language to "ar",
                AnalyticsProperty.LibrarySizeBucket to "51-500",
                AnalyticsProperty.HasOwnKey to "yes"
            ),
            analytics.properties
        )
    }

    @Test
    fun aDebugBuildNeverEnables() = runTest {
        startup(isRelease = false, languageTag = "en").run()

        assertEquals(listOf(false), analytics.enabledCalls)
    }

    @Test
    fun switchedOffStaysOff() = runTest {
        preferences.setAnalyticsEnabled(false)

        startup(isRelease = true, languageTag = "en").run()

        assertEquals(listOf(false), analytics.enabledCalls)
    }
}
