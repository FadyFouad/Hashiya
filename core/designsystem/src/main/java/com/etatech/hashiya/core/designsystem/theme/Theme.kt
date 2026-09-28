package com.etatech.hashiya.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun HashiyaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val typography = remember(isArabic) { hashiyaTypography(if (isArabic) PlexArabicFamily else InterFamily) }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = typography,
        content = content
    )
}
