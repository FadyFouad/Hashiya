package com.etatech.hashiya

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.navigation.HashiyaApp
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity so that AppCompatDelegate.setApplicationLocales can switch the language in-app. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HashiyaTheme {
                HashiyaApp()
            }
        }
    }
}
