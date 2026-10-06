package com.etatech.hashiya.feature.library.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.etatech.hashiya.feature.library.ReferenceExport
import java.io.File

internal const val BIB_MIME_TYPE = "text/x-bibtex"
internal const val RTF_MIME_TYPE = "application/rtf"

/** Matches the cache-path in res/xml/bib_export_paths.xml. */
private const val EXPORTS_DIRECTORY = "exports"

/** Writes [export] into [directory], deleting earlier exports first, so the cache never holds more than the latest file. */
internal fun writeExportFileTo(directory: File, export: ReferenceExport): File {
    directory.deleteRecursively()
    directory.mkdirs()
    return File(directory, export.fileName).apply { writeText(export.content, Charsets.UTF_8) }
}

/** Writes the file to cacheDir/exports and returns a content Uri the share target can read (FileProvider in the manifest). */
internal fun writeExportFile(context: Context, export: ReferenceExport): Uri {
    val file = writeExportFileTo(File(context.cacheDir, EXPORTS_DIRECTORY), export)
    return FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
}

internal fun exportShareIntent(uri: Uri, export: ReferenceExport): Intent = Intent(Intent.ACTION_SEND).apply {
    type = export.mimeType
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_TITLE, export.fileName)
    // ClipData carries the read grant through the chooser to the app the user picks.
    clipData = ClipData.newRawUri(export.fileName, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
