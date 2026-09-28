package com.etatech.hashiya.core.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment

/** Default device for screen-level screenshots. Use with `@Config(qualifiers = PHONE_QUALIFIERS)`. */
const val PHONE_QUALIFIERS = "w360dp-h780dp-xhdpi"

private const val SCREENSHOT_TAG = "screenshot_root"

/** [qualifiers] are Robolectric qualifiers added on top of the class's `@Config` ones. */
enum class ScreenshotVariant(val qualifiers: String, val darkTheme: Boolean) {
    EnglishLight("+en", darkTheme = false),
    EnglishDark("+en-night", darkTheme = true),
    ArabicLight("+ar", darkTheme = false),
    ArabicDark("+ar-night", darkTheme = true)
    ;

    val isArabic: Boolean get() = qualifiers.startsWith("+ar")

    companion object {
        /** For `@ParameterizedRobolectricTestRunner.Parameters`. */
        @JvmStatic
        fun parameters(): List<Array<Any>> = entries.map { arrayOf(it) }
    }
}

/**
 * Applies the variant's locale and night mode as Robolectric qualifiers before the activity starts,
 * so resources resolve exactly as on a device. Declare it first:
 * `@get:Rule(order = 0) val variantRule = ScreenshotVariantRule(variant)` and the compose rule with `order = 1`.
 */
class ScreenshotVariantRule(private val variant: ScreenshotVariant) : TestWatcher() {
    override fun starting(description: Description) {
        RuntimeEnvironment.setQualifiers(variant.qualifiers)
    }
}

/**
 * Renders [content] in the Hashiya theme for [variant] and records or verifies
 * `src/test/screenshots/<name>-<variant>.png`.
 *
 * In Arabic variants, [arabicText] (a string the content shows in Arabic) must be on screen; this fails the test
 * instead of recording English text as an "Arabic" baseline.
 *
 * [beforeCapture] runs once the content is shown, e.g. to open a menu. Menus and dialogs are separate windows, so
 * capturing one needs [wholeScreen], which records every window instead of the content alone.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.captureScreenshot(
    name: String,
    variant: ScreenshotVariant,
    arabicText: String,
    wholeScreen: Boolean = false,
    beforeCapture: ComposeContentTestRule.() -> Unit = {},
    content: @Composable () -> Unit
) {
    setContent {
        // Forced so the direction does not depend on the test manifest's android:supportsRtl.
        val direction = if (variant.isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            HashiyaTheme(darkTheme = variant.darkTheme) {
                Box(Modifier.testTag(SCREENSHOT_TAG)) {
                    Surface { content() }
                }
            }
        }
    }
    beforeCapture()
    if (variant.isArabic) {
        onNodeWithText(arabicText, substring = true, useUnmergedTree = true)
            .assertExists("Arabic variant did not render \"$arabicText\"; check the locale qualifiers")
    }
    val filePath = "src/test/screenshots/$name-${variant.name}.png"
    val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))
    if (wholeScreen) {
        waitForIdle()
        captureScreenRoboImage(filePath, options)
    } else {
        onNodeWithTag(SCREENSHOT_TAG).captureRoboImage(filePath = filePath, roborazziOptions = options)
    }
}
