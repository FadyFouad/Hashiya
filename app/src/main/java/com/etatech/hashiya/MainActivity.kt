package com.etatech.hashiya

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import com.etatech.hashiya.navigation.HashiyaApp
import com.etatech.hashiya.share.shareToSearchRoute
import com.etatech.hashiya.update.AppUpdateViewModel
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity so that AppCompatDelegate.setApplicationLocales can switch the language in-app. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    /** A shared page waiting to be opened in Search; cleared once navigation has happened. */
    private var pendingSearch by mutableStateOf<SearchRoute?>(null)

    private val appUpdate: AppUpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (isFreshLaunch(savedInstanceState)) pendingSearch = intent.sharedSearchRoute()
        setContent {
            val requiredUpdate by appUpdate.requiredUpdate.collectAsState()
            HashiyaTheme {
                HashiyaApp(
                    pendingSearch = pendingSearch,
                    onPendingSearchHandled = { pendingSearch = null },
                    requiredUpdate = requiredUpdate,
                    onOpenStore = ::openStore
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
    }

    /**
     * After a rotation the intent is still the share, and reopening from Recents re-delivers the old
     * share; either way it was handled already.
     */
    private fun isFreshLaunch(savedInstanceState: Bundle?): Boolean = savedInstanceState == null &&
        (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

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
