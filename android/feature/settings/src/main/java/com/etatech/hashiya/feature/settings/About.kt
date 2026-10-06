package com.etatech.hashiya.feature.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

internal const val FEEDBACK_EMAIL = "fady.fouad.a@gmail.com"
internal const val ABOUT_SECTION_TAG = "settings_about"
private const val PACKAGE = "com.etatech.hashiya"

/** This install's version, as stores and support quote it (Latin digits in every language). */
data class AppVersion(val name: String, val code: Long) {
    val label: String get() = "$name ($code)"

    companion object {
        fun of(context: Context): AppVersion = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            AppVersion(info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info))
        } catch (_: PackageManager.NameNotFoundException) {
            AppVersion("", 0)
        }
    }
}

/** What the developer needs to reproduce a report; nothing from the library. */
internal fun feedbackInfoLine(version: AppVersion, osRelease: String, model: String, language: String): String =
    "Hashiya ${version.label} · Android $osRelease · $model · $language"

/** An email draft to the developer, the message first and the info line under it. */
internal fun feedbackIntent(subject: String, version: AppVersion, osRelease: String, model: String, language: String): Intent =
    // The address goes in the URI as well: some mail apps ignore EXTRA_EMAIL.
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_EMAIL")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, "\n\n" + feedbackInfoLine(version, osRelease, model, language))
    }

/** The Play Store app, then the web listing. */
internal fun rateIntents(): List<Intent> = listOf(
    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")),
    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
)

/** Opens the draft; false when no email app can (the caller then copies the address). */
internal fun Context.sendFeedback(subject: String, version: AppVersion, language: String): Boolean = try {
    startActivity(feedbackIntent(subject, version, Build.VERSION.RELEASE, Build.MODEL, language))
    true
} catch (_: ActivityNotFoundException) {
    false
}

internal fun Context.copyFeedbackAddress() {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(FEEDBACK_EMAIL, FEEDBACK_EMAIL))
}

/** The first intent something can open; nothing happens if none can. */
internal fun Context.openRatePage() {
    for (intent in rateIntents()) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}
