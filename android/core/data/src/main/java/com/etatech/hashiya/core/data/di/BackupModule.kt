package com.etatech.hashiya.core.data.di

import android.content.ContentResolver
import android.content.Context
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.data.backup.ArchiveLibraryBackup
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfStoreGate
import com.etatech.hashiya.core.database.dao.BackupDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
internal object BackupModule {
    @Provides
    @Singleton
    fun provideLibraryBackup(
        @ApplicationContext context: Context,
        backupDao: BackupDao,
        fileStore: PdfFileStore,
        gate: PdfStoreGate,
        contentResolver: ContentResolver,
        crashReporter: CrashReporter
    ): LibraryBackup = ArchiveLibraryBackup(
        backupDao = backupDao,
        fileStore = fileStore,
        gate = gate,
        contentResolver = contentResolver,
        workDir = File(context.cacheDir, "backup"),
        appVersion = "${context.packageManager.getPackageInfo(context.packageName, 0).versionName} (Android)",
        now = System::currentTimeMillis,
        newId = { UUID.randomUUID().toString() },
        io = Dispatchers.IO,
        crashReporter = crashReporter
    )
}
