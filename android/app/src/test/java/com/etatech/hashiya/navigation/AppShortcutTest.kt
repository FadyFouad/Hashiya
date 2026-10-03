package com.etatech.hashiya.navigation

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppShortcutTest {
    private val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

    @Test
    fun ctrlKeysMapToShortcuts() {
        assertEquals(AppShortcut.Find, AppShortcut.forKey(KeyEvent.KEYCODE_F, ctrl))
        assertEquals(AppShortcut.AddPaper, AppShortcut.forKey(KeyEvent.KEYCODE_N, ctrl))
        assertEquals(AppShortcut.Settings, AppShortcut.forKey(KeyEvent.KEYCODE_COMMA, ctrl))
    }

    @Test
    fun otherCombinationsAreLeftAlone() {
        assertNull(AppShortcut.forKey(KeyEvent.KEYCODE_F, 0))
        assertNull(AppShortcut.forKey(KeyEvent.KEYCODE_F, ctrl or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON))
        assertNull(AppShortcut.forKey(KeyEvent.KEYCODE_F, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON))
        // Copy and paste stay with the text fields.
        assertNull(AppShortcut.forKey(KeyEvent.KEYCODE_C, ctrl))
    }
}
