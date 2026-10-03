package com.etatech.hashiya.navigation

import android.view.KeyEvent
import androidx.annotation.StringRes
import com.etatech.hashiya.R

/** Keyboard shortcuts (hardware keyboards: tablets, Chromebooks, desktop windowing). Ctrl plus [keyCode]. */
enum class AppShortcut(val keyCode: Int, @StringRes val labelRes: Int) {
    /** The Library's search field on the Library; otherwise Search's field, keeping the current search. */
    Find(KeyEvent.KEYCODE_F, R.string.shortcut_find),

    /** As the Library's Add paper button: a fresh search with the field focused. */
    AddPaper(KeyEvent.KEYCODE_N, R.string.shortcut_add_paper),
    Settings(KeyEvent.KEYCODE_COMMA, R.string.shortcut_settings);

    companion object {
        /** The shortcut for Ctrl + [keyCode], with no other modifier. */
        fun forKey(keyCode: Int, metaState: Int): AppShortcut? = if (KeyEvent.metaStateHasModifiers(metaState, KeyEvent.META_CTRL_ON)) {
            entries.firstOrNull { it.keyCode == keyCode }
        } else {
            null
        }
    }
}
