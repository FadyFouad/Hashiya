package com.etatech.hashiya

import android.content.Intent
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

/** Shares without a paper ID, so no request leaves the test. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ShareNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val nothingNote = "Couldn't find a paper in what you shared."

    private fun shareIntent(text: String) = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
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

            waitForText("Looking up arXiv 1706.03762…")
            composeRule.onNodeWithText("Looking up arXiv 1706.03762…").assertIsDisplayed()
            composeRule.onNodeWithText(nothingNote).assertDoesNotExist()
        }
    }
}
