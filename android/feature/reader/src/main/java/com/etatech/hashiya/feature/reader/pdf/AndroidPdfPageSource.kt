package com.etatech.hashiya.feature.reader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Size
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [PdfRenderer] behind [PdfPageSource]. PdfRenderer allows one open page at a time and isn't thread-safe, so every call runs on
 * one background thread ([dispatcher], parallelism 1) and under [mutex].
 */
internal class AndroidPdfPageSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val sizes: List<Size>,
    private val dispatcher: CoroutineDispatcher
) : PdfPageSource {
    private val mutex = Mutex()
    private val closeScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var closed = false

    override val pageCount: Int get() = sizes.size

    override fun pageSize(index: Int): Size = sizes[index]

    override suspend fun render(index: Int, width: Int): Bitmap = withContext(dispatcher) {
        mutex.withLock {
            check(!closed) { "The PDF is closed" }
            val size = sizes[index]
            val height = (width.toLong() * size.height / size.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // PDF pages are transparent where nothing is drawn.
            bitmap.eraseColor(Color.WHITE)
            try {
                renderer.openPage(index).use { page -> page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            } catch (e: Exception) {
                bitmap.recycle()
                throw e
            }
            bitmap
        }
    }

    /** Waits for a render in progress, on the render thread, so the renderer never closes under an open page. */
    override fun close() {
        closeScope.launch {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    renderer.close()
                    descriptor.close()
                }
            }
        }
    }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        suspend fun open(file: File): PdfPageSource {
            val dispatcher = Dispatchers.IO.limitedParallelism(1)
            return withContext(dispatcher) {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val renderer = PdfRenderer(descriptor)
                    val sizes = try {
                        (0 until renderer.pageCount).map { index ->
                            renderer.openPage(index).use { page -> Size(page.width, page.height) }
                        }
                    } catch (e: Exception) {
                        renderer.close()
                        throw e
                    }
                    if (sizes.isEmpty()) {
                        renderer.close()
                        throw IOException("The PDF has no pages")
                    }
                    AndroidPdfPageSource(descriptor, renderer, sizes, dispatcher)
                } catch (e: Exception) {
                    descriptor.close()
                    throw e
                } catch (e: LinkageError) {
                    // No native PDF renderer (as under Robolectric): the reader shows "can't be opened" instead of crashing.
                    descriptor.close()
                    throw IOException("PdfRenderer is unavailable", e)
                }
            }
        }
    }
}
