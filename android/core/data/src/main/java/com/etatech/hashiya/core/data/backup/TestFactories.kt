package com.etatech.hashiya.core.data.backup

import java.io.File

/** For fakes in other modules; the app gets these only from [LibraryBackup]. */
fun exportedFileForTest(file: File, fileName: String, missingPdfs: Int = 0): ExportedFile = ExportedFile(file, fileName, missingPdfs)

fun preparedBackupForTest(file: File): PreparedBackup = PreparedBackup(file, BackupLibrary())
