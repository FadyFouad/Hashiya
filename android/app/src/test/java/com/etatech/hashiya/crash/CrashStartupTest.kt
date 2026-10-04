package com.etatech.hashiya.crash

import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.testing.FakeCrashReporter
import com.etatech.hashiya.core.testing.FakeLibraryBackup
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CrashStartupTest {
    private val reporter = FakeCrashReporter()
    private val preferences = FakeUserPreferencesRepository()
    private val backup = FakeLibraryBackup()

    private fun startup(isRelease: Boolean = true, languageTag: String = "") =
        CrashStartup(reporter, preferences, backup, { languageTag }, isRelease)

    @Test
    fun startupLeavesCollectionOffWhenThePreferenceIsOff() = runTest {
        preferences.setCrashReportsEnabled(false)

        startup().run()

        assertEquals(listOf(false), reporter.enabledCalls)
    }

    @Test
    fun releaseBuildWithThePreferenceOnEnablesCollection() = runTest {
        preferences.setCrashReportsEnabled(true)

        startup().run()

        assertEquals(listOf(true), reporter.enabledCalls)
    }

    @Test
    fun debugBuildsNeverEnable() = runTest {
        preferences.setCrashReportsEnabled(true)

        startup(isRelease = false).run()

        assertEquals(listOf(false), reporter.enabledCalls)
    }

    @Test
    fun setsLanguageAndLibrarySize() = runTest {
        backup.summary = BackupSummary(papers = 182, collections = 0, pdfCount = 0, pdfBytes = 0)

        startup(languageTag = "ar").run()

        assertEquals("ar", reporter.keys[CrashKey.Language])
        assertEquals("51-500", reporter.keys[CrashKey.LibrarySizeBucket])
        assertEquals("none", reporter.keys[CrashKey.BackupInProgress])
    }

    @Test
    fun noChosenLanguageIsSystem() = runTest {
        startup(languageTag = "").run()

        assertEquals("system", reporter.keys[CrashKey.Language])
    }

    @Test
    fun aRegionalOrUnknownTagStaysWithinTheClosedValues() = runTest {
        startup(languageTag = "en-GB,ar").run()
        assertEquals("en", reporter.keys[CrashKey.Language])

        startup(languageTag = "fr").run()
        assertEquals("system", reporter.keys[CrashKey.Language])
    }

    @Test
    fun anUnreadableLibraryLeavesTheSizeUnset() = runTest {
        val failing = object : com.etatech.hashiya.core.data.backup.LibraryBackup by backup {
            override suspend fun summary(): BackupSummary = error("database unavailable")
        }

        CrashStartup(reporter, preferences, failing, { "" }, true).run()

        assertFalse(reporter.keys.containsKey(CrashKey.LibrarySizeBucket))
    }
}
