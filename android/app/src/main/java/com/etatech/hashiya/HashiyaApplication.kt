package com.etatech.hashiya

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.di.ApplicationScope
import com.etatech.hashiya.core.data.repository.PdfRepository
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import com.etatech.hashiya.crash.CrashStartup
import com.etatech.hashiya.crash.isDebuggable
import com.etatech.hashiya.crash.sanitizingHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class HashiyaApplication : Application() {
    @Inject
    lateinit var pdfRepository: PdfRepository

    @Inject
    lateinit var crashReporter: CrashReporter

    @Inject
    lateinit var preferences: UserPreferencesRepository

    @Inject
    lateinit var libraryBackup: LibraryBackup

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        val isRelease = !isDebuggable(this)
        if (isRelease) {
            // Before super.onCreate (Hilt's setup), so even a crash there reaches Crashlytics without messages. Crashlytics
            // installed its own handler earlier, in FirebaseInitProvider, so this one runs first and hands the crash on.
            Thread.setDefaultUncaughtExceptionHandler(sanitizingHandler(Thread.getDefaultUncaughtExceptionHandler()))
        }
        super.onCreate()
        // Before the other launches, so collection and the context keys are decided before other work can fail.
        scope.launch {
            CrashStartup(
                reporter = crashReporter,
                preferences = preferences,
                libraryBackup = libraryBackup,
                languageTag = { AppCompatDelegate.getApplicationLocales().toLanguageTags() },
                isRelease = isRelease
            ).run()
        }
        // Files left by a crash mid-download, or by a removal made final while the app was killed during Undo.
        scope.launch { pdfRepository.sweepOrphans() }
    }
}
