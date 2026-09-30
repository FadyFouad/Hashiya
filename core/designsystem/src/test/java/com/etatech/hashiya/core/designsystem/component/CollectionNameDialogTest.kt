package com.etatech.hashiya.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionNameDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val confirmed = mutableListOf<String>()
    private var edits = 0
    private var dismissals = 0

    private fun show(initialName: String? = null, nameTaken: Boolean = false) = composeRule.setContent {
        HashiyaTheme {
            CollectionNameDialog(
                initialName = initialName,
                nameTaken = nameTaken,
                onNameEdited = { edits++ },
                onConfirm = { confirmed += it },
                onDismiss = { dismissals++ }
            )
        }
    }

    @Test
    fun newCollectionIsCreatedOnlyWithAValidName() {
        show()
        composeRule.onNodeWithText("New collection").assertExists()
        composeRule.onNodeWithText("Create").assertIsNotEnabled()

        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("   ")
        composeRule.onNodeWithText("Create").assertIsNotEnabled()
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("Thesis")
        composeRule.onNodeWithText("Create").assertIsEnabled().performClick()

        assertEquals(listOf("   Thesis"), confirmed)
        assertTrue(edits > 0)
    }

    @Test
    fun renameStartsWithTheCurrentNameAndRejectsTooLongNames() {
        show(initialName = "Chapter 2")
        composeRule.onNodeWithText("Rename collection").assertExists()
        composeRule.onNodeWithText("Chapter 2").assertExists()
        composeRule.onNodeWithText("Save").assertIsEnabled()

        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextClearance()
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("x".repeat(61))
        composeRule.onNodeWithText("Save").assertIsNotEnabled()

        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextClearance()
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("x".repeat(60))
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun cancelDismissesWithoutConfirming() {
        show()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, dismissals)
        assertEquals(emptyList<String>(), confirmed)
    }

    @Test
    fun showsTheNameTakenErrorUnderTheFieldUntilTheNameIsEdited() {
        var taken by mutableStateOf(false)
        val submitted = mutableListOf<String>()
        composeRule.setContent {
            HashiyaTheme {
                CollectionNameDialog(
                    initialName = null,
                    nameTaken = taken,
                    onNameEdited = { taken = false },
                    onConfirm = {
                        submitted += it
                        taken = true
                    },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput(" thesis ")
        composeRule.onNodeWithText("Create").performClick()

        // The dialog stays open with the typed name and shows the error.
        composeRule.onNodeWithText("A collection with that name already exists").assertExists()
        composeRule.onNodeWithText("New collection").assertExists()
        composeRule.onNodeWithText(" thesis ").assertExists()
        assertEquals(listOf(" thesis "), submitted)

        composeRule.onNodeWithTag(COLLECTION_NAME_FIELD_TAG).performTextInput("a")
        composeRule.onNodeWithText("A collection with that name already exists").assertDoesNotExist()
    }
}
