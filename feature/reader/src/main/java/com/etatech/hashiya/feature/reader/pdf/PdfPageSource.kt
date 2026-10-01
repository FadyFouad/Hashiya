package com.etatech.hashiya.feature.reader.pdf

import android.graphics.Bitmap
import android.util.Size
import java.io.File

/** One open PDF: its page sizes, read once on open, and page rendering. */
interface PdfPageSource {
    val pageCount: Int

    /** The page's size in PDF points. */
    fun pageSize(index: Int): Size

    /** Renders page [index] [width] pixels wide, keeping its aspect ratio, on a white background. */
    suspend fun render(index: Int, width: Int): Bitmap

    /** Releases the file. Renders after this fail. */
    fun close()
}

fun interface PdfPageSourceFactory {
    /** Opens [file]; throws when the platform can't read it (damaged, password-protected, or no pages). */
    suspend fun open(file: File): PdfPageSource
}
