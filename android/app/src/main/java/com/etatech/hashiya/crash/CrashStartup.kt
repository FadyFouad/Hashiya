package com.etatech.hashiya.crash

import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.crash.librarySizeBucket
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/** Runs once at launch: collection follows the switch (release builds only), then the context keys are set. */
class CrashStartup(
    private val reporter: CrashReporter,
    private val preferences: UserPreferencesRepository,
    private val libraryBackup: LibraryBackup,
    private val languageTag: () -> String,
    private val isRelease: Boolean
) {
    suspend fun run() {
        reporter.setEnabled(isRelease && preferences.crashReportsEnabled.first())
        reporter.setKey(CrashKey.Language, languageKey(languageTag()))
        reporter.setKey(CrashKey.BackupInProgress, "none")
        val papers = try {
            libraryBackup.summary().papers
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (papers != null) reporter.setKey(CrashKey.LibrarySizeBucket, librarySizeBucket(papers))
    }
}

/** en, ar or system: the closed values the Language key allows. */
fun languageKey(tags: String): String = when (tags.substringBefore(',').substringBefore('-')) {
    "en" -> "en"
    "ar" -> "ar"
    else -> "system"
}
