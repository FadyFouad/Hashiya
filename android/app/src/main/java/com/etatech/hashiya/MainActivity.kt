package com.etatech.hashiya

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import com.etatech.hashiya.navigation.AppShortcut
import com.etatech.hashiya.navigation.HashiyaApp
import com.etatech.hashiya.share.shareToSearchRoute
import com.etatech.hashiya.update.AppUpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** AppCompatActivity so that AppCompatDelegate.setApplicationLocales can switch the language in-app. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    /** A shared page waiting to be opened in Search; cleared once navigation has happened. */
    private var pendingSearch by mutableStateOf<SearchRoute?>(null)

    /** A `.hashiya` file opened from another app, waiting to be shown in Restore; cleared once navigation has happened. */
    private var pendingRestore by mutableStateOf<String?>(null)

    /** A keyboard shortcut waiting for the app to act on it. */
    private var pendingShortcut by mutableStateOf<AppShortcut?>(null)

    private val appUpdate: AppUpdateViewModel by viewModels()

    @Inject
    lateinit var crashReporter: CrashReporter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (isFreshLaunch(savedInstanceState)) pendingSearch = intent.sharedSearchRoute()
        if (isFreshLaunch(savedInstanceState)) pendingRestore = intent.openedBackup()
        setContent {
            val requiredUpdate by appUpdate.requiredUpdate.collectAsState()
            HashiyaTheme {
                HashiyaApp(
                    crashReporter = crashReporter,
                    pendingSearch = pendingSearch,
                    onPendingSearchHandled = { pendingSearch = null },
                    pendingRestore = pendingRestore,
                    onPendingRestoreHandled = { pendingRestore = null },
                    requiredUpdate = requiredUpdate,
                    onOpenStore = ::openStore,
                    shortcut = pendingShortcut,
                    onShortcutHandled = { pendingShortcut = null }
                )
            }
        }
    }

    /** Launch and every return to the foreground. */
    override fun onStart() {
        super.onStart()
        appUpdate.check()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.sharedSearchRoute()?.let { pendingSearch = it }
        intent.openedBackup()?.let { pendingRestore = it }
    }

    /**
     * After a rotation the intent is still the share, and reopening from Recents re-delivers the old
     * share; either way it was handled already.
     */
    private fun isFreshLaunch(savedInstanceState: Bundle?): Boolean = savedInstanceState == null &&
        (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

    /** Ctrl shortcuts no focused view handled. */
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val shortcut = AppShortcut.forKey(keyCode, event.metaState) ?: return super.onKeyShortcut(keyCode, event)
        pendingShortcut = shortcut
        return true
    }

    /**
     * Esc with nothing focused goes back, as the app's content does for a focused Esc, but only when there is somewhere to
     * go back to: Esc never closes the app.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (super.dispatchKeyEvent(event)) return true
        if (event.keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled && onBackPressedDispatcher.hasEnabledCallbacks()) {
            onBackPressedDispatcher.onBackPressed()
        }
        return true
    }

    /** Lists the shortcuts in the system's keyboard shortcuts helper (Meta + /). */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data += KeyboardShortcutGroup(
            getString(R.string.app_name),
            AppShortcut.entries.map { KeyboardShortcutInfo(getString(it.labelRes), it.keyCode, KeyEvent.META_CTRL_ON) }
        )
    }

    /** A device without a store app or browser keeps showing the screen instead of crashing. */
    private fun openStore(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    private fun Intent.sharedSearchRoute(): SearchRoute? {
        if (action != Intent.ACTION_SEND || type?.startsWith("text/") != true) return null
        // These extras may be styled (Spanned), which getStringExtra would return as null.
        val subject = getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
            ?: getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()
        return shareToSearchRoute(getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(), subject)
    }
}

/** A `.hashiya` file opened from Files, Drive or a mail app. Only a content URI: a file URI could point at the app's own files. */
internal fun Intent.openedBackup(): String? = data?.takeIf { action == Intent.ACTION_VIEW && it.scheme == "content" }?.toString()
