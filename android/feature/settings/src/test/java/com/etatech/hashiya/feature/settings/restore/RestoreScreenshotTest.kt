package com.etatech.hashiya.feature.settings.restore

import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.data.backup.RestorePreview
import com.etatech.hashiya.core.data.backup.RestoreResult
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
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
class RestoreScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun preview() = composeRule.captureScreenshot("restore_preview", variant, arabicText = "إضافة إلى المكتبة") {
        RestoreContent(
            uiState = RestoreUiState.Preview(RestorePreview(1_791_122_700_000L, 182, 6, 41, 150, 32)),
            onConfirm = {},
            onLeave = {}
        )
    }

    @Test
    fun done() = composeRule.captureScreenshot("restore_done", variant, arabicText = "تمت استعادة النسخة الاحتياطية") {
        RestoreContent(uiState = RestoreUiState.Done(RestoreResult(150, 3, 6, 38, 3)), onConfirm = {}, onLeave = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
