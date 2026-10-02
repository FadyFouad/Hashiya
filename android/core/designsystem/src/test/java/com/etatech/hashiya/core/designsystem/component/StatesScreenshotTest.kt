package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
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

/** The state components take their text from callers, so this test passes text in the variant's language. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class StatesScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun text(english: String, arabic: String) = if (variant.isArabic) arabic else english

    @Test
    fun states() = composeRule.captureScreenshot("states", variant, arabicText = "لا توجد أوراق محفوظة بعد") {
        Column(Modifier.width(360.dp)) {
            EmptyState(
                HashiyaIcons.Library,
                title = text("No saved papers yet", "لا توجد أوراق محفوظة بعد"),
                message = text("Papers you save appear here", "ستظهر هنا الأوراق التي تحفظها"),
                actionLabel = text("Go to Search", "الذهاب إلى البحث")
            )
            ErrorState(
                title = text("Can't reach OpenAlex", "تعذّر الوصول إلى OpenAlex"),
                message = text("Check your connection.", "تحقق من اتصالك."),
                actionLabel = text("Retry", "إعادة المحاولة"),
                onAction = {}
            )
            LoadingSkeleton(rows = 2)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
