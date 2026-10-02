package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class PaperScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun cards() = composeRule.captureScreenshot("paper_cards", variant, arabicText = "في المكتبة") {
        Column(Modifier.width(360.dp).padding(vertical = 6.dp)) {
            PaperCard(SamplePapers.attention, inLibrary = true, onClick = {}, onSave = {})
            PaperCard(SamplePapers.bert, inLibrary = false, onClick = {}, onSave = {})
            PaperCard(SamplePapers.arabicTitled, inLibrary = false, onClick = {}, onSave = {})
        }
    }

    @Test
    fun preview() = composeRule.captureScreenshot("paper_preview", variant, arabicText = "الملخص") {
        PaperPreviewContent(SamplePapers.bert, inLibrary = false, onToggleSave = {}, onOpenDoi = {}, modifier = Modifier.width(360.dp))
    }

    @Test
    fun previewWithStatus() = composeRule.captureScreenshot("paper_preview_status", variant, arabicText = "قيد القراءة") {
        PaperPreviewContent(
            SamplePapers.bert,
            inLibrary = true,
            onToggleSave = {},
            onOpenDoi = {},
            modifier = Modifier.width(360.dp),
            status = ReadingStatus.Reading
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
