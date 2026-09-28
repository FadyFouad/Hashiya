package com.etatech.hashiya

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import com.etatech.hashiya.navigation.HashiyaApp
import com.etatech.hashiya.share.shareToSearchRoute
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity so that AppCompatDelegate.setApplicationLocales can switch the language in-app. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    /** A shared page waiting to be opened in Search; cleared once navigation has happened. */
    private var pendingSearch by mutableStateOf<SearchRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation the intent is still the share; it was handled before the activity was recreated.
        if (savedInstanceState == null) pendingSearch = intent.sharedSearchRoute()
        setContent {
            HashiyaTheme {
                HashiyaApp(pendingSearch = pendingSearch, onPendingSearchHandled = { pendingSearch = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.sharedSearchRoute()?.let { pendingSearch = it }
    }

    private fun Intent.sharedSearchRoute(): SearchRoute? {
        if (action != Intent.ACTION_SEND || type?.startsWith("text/") != true) return null
        // These extras may be styled (Spanned), which getStringExtra would return as null.
        val subject = getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
            ?: getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()
        return shareToSearchRoute(getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(), subject)
    }
}
