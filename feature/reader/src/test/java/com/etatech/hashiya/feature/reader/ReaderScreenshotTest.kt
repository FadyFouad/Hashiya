package com.etatech.hashiya.feature.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LETTER = 11f / 8.5f

/** A white page with grey bars standing in for lines of text, so the screenshots show pages without a real PDF. */
private fun fakePage(width: Int): Bitmap {
    val height = (width * LETTER).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply { color = Color.rgb(205, 210, 215) }
    val margin = width / 10f
    val line = height / 42f
    var top = margin
    while (top < height - margin) {
        canvas.drawRect(margin, top, width - margin, top + line * 0.45f, paint)
        top += line
    }
    return bitmap
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ReaderScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val ready = ReaderState.Ready(SamplePapers.attention.title, 14, List(14) { LETTER }, startPage = 0, file = File("x.pdf"))
    private val pages = mapOf(0 to fakePage(720), 1 to fakePage(720))

    @Test
    fun pages() = composeRule.captureScreenshot("reader_pages", variant, arabicText = "من") {
        ReaderContent(
            state = ready,
            pages = pages,
            notes = PaperNotes(),
            notesSaveState = NotesSaveState.Idle,
            showNotes = false,
            actions = ReaderActions(),
            pillAlwaysVisible = true
        )
    }

    @Test
    fun notesOverThePdf() = composeRule.captureScreenshot("reader_notes", variant, arabicText = "الخلاصة", wholeScreen = true) {
        ReaderContent(
            state = ready,
            pages = pages,
            notes = PaperNotes(summary = "Attention replaces recurrence; the encoder and decoder are stacks of attention layers."),
            notesSaveState = NotesSaveState.Saved,
            showNotes = true,
            actions = ReaderActions()
        )
    }

    @Test
    fun cantOpen() = composeRule.captureScreenshot("reader_cant_open", variant, arabicText = "تعذّر فتح") {
        ReaderContent(
            state = ReaderState.CantOpen(SamplePapers.attention.title),
            pages = emptyMap(),
            notes = PaperNotes(),
            notesSaveState = NotesSaveState.Idle,
            showNotes = false,
            actions = ReaderActions()
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
