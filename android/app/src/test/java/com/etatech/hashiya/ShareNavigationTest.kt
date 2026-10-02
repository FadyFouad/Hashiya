package com.etatech.hashiya

import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first two shares have no paper ID, so no lookup request leaves them. The third shares a real
 * arXiv link, so a lookup does start in the background; it only asserts what Search shows immediately
 * (the field's text), never anything that depends on the lookup's outcome.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ShareNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val nothingNote = "Couldn't find a paper in what you shared."

    private fun shareIntent(text: CharSequence) = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)

    private fun waitForText(text: String) = composeRule.waitUntil(timeoutMillis = 5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun shareWithoutAPaperOpensSearchWithANote() {
        ActivityScenario.launch<MainActivity>(shareIntent("just some words")).use {
            waitForText(nothingNote)
            composeRule.onNodeWithText(nothingNote).assertIsDisplayed()
        }
    }

    @Test
    fun shareIsHandledOnceAcrossRecreation() {
        ActivityScenario.launch<MainActivity>(shareIntent("just some words")).use { scenario ->
            waitForText(nothingNote)
            composeRule.onAllNodesWithText("Library").onFirst().performClick()
            waitForText("No saved papers yet")

            scenario.recreate()
            composeRule.waitForIdle()

            composeRule.onNodeWithText("No saved papers yet").assertIsDisplayed()
            composeRule.onNodeWithText(nothingNote).assertDoesNotExist()
        }
    }

    @Test
    fun aSecondShareOpensTheNewLink() {
        ActivityScenario.launch<MainActivity>(shareIntent("just some words")).use { scenario ->
            waitForText(nothingNote)

            scenario.onActivity { activity ->
                InstrumentationRegistry.getInstrumentation()
                    .callActivityOnNewIntent(activity, shareIntent("https://arxiv.org/abs/1706.03762"))
            }

            waitForText("arXiv:1706.03762")
            composeRule.onNodeWithText("arXiv:1706.03762").assertIsDisplayed()
            composeRule.onNodeWithText(nothingNote).assertDoesNotExist()
        }
    }

    @Test
    fun aStyledShareIsRead() {
        val link = "https://arxiv.org/abs/1706.03762"
        val styled = SpannableString(link).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, link.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        ActivityScenario.launch<MainActivity>(shareIntent(styled)).use {
            waitForText("arXiv:1706.03762")
            composeRule.onNodeWithText("arXiv:1706.03762").assertIsDisplayed()
        }
    }

    @Test
    fun aShareReopenedFromRecentsIsNotReplayed() {
        val fromRecents = shareIntent("just some words").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        ActivityScenario.launch<MainActivity>(fromRecents).use {
            waitForText("No saved papers yet")
            composeRule.onNodeWithText("No saved papers yet").assertIsDisplayed()
            composeRule.onNodeWithText(nothingNote).assertDoesNotExist()
        }
    }
}
