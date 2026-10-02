package com.etatech.hashiya.feature.reader

import android.graphics.Bitmap
import android.util.Size
import com.etatech.hashiya.feature.reader.pdf.PdfPageSource

/** Pages of one [size]; records every render so tests can see what was drawn and when. */
internal class FakePdfPageSource(override val pageCount: Int, private val size: Size = Size(600, 800)) : PdfPageSource {
    /** Every render, in order: page index to width. */
    val rendered = mutableListOf<Pair<Int, Int>>()
    var closed = false
        private set

    override fun pageSize(index: Int): Size = size

    override suspend fun render(index: Int, width: Int): Bitmap {
        check(!closed) { "rendered after close" }
        rendered += index to width
        return Bitmap.createBitmap(width, (width.toLong() * size.height / size.width).toInt(), Bitmap.Config.ARGB_8888)
    }

    override fun close() {
        closed = true
    }
}
