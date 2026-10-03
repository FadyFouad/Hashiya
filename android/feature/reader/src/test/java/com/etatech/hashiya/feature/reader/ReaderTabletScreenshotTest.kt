package com.etatech.hashiya.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.TABLET_QUALIFIERS
import com.etatech.hashiya.core.testing.captureScreenshot
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The reader on a landscape tablet: the PDF with the notes in a pane beside it. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TABLET_QUALIFIERS)
class ReaderTabletScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val ready = ReaderState.Ready(SamplePapers.attention.title, 14, List(14) { LETTER }, startPage = 0, file = File("x.pdf"))

    @Test
    fun notesPane() = composeRule.captureScreenshot("reader_notes_pane", variant, arabicText = "ملاحظاتي") {
        ReaderContent(
            state = ready,
            pages = mapOf(0 to fakePage(720), 1 to fakePage(720)),
            notes = PaperNotes(
                summary = "Attention alone, without recurrence or convolution, is enough for translation.",
                researchQuestion = "Can a model built only on attention beat recurrent ones?"
            ),
            notesSaveState = NotesSaveState.Saved,
            showNotes = true,
            notesBeside = true,
            actions = ReaderActions(),
            pillAlwaysVisible = true
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
