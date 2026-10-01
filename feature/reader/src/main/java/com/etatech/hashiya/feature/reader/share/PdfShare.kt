package com.etatech.hashiya.feature.reader.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal const val PDF_MIME_TYPE = "application/pdf"

internal fun pdfShareIntent(uri: Uri, title: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = PDF_MIME_TYPE
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_TITLE, title)
    // ClipData carries the read grant through the chooser to the app the user picks.
    clipData = ClipData.newRawUri(title, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** Shares [file] (in filesDir/pdfs) through the FileProvider in this module's manifest. */
internal fun sharePdf(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.pdfs", file)
    context.startActivity(Intent.createChooser(pdfShareIntent(uri, title), null))
}
