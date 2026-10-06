package com.etatech.hashiya.feature.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AboutTest {
    private val version = AppVersion("0.3.0", 3)
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun versionLabelUsesLatinDigits() = assertEquals("0.3.0 (3)", version.label)

    @Test
    fun infoLine() = assertEquals("Hashiya 0.3.0 (3) · Android 14 · Pixel 7 · ar", feedbackInfoLine(version, "14", "Pixel 7", "ar"))

    @Test
    fun feedbackIntentIsADraftToTheDeveloper() {
        val intent = feedbackIntent("Hashiya feedback", version, "14", "Pixel 7", "en")
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        // In the URI too: some mail apps ignore EXTRA_EMAIL.
        assertEquals(Uri.parse("mailto:$FEEDBACK_EMAIL"), intent.data)
        assertEquals(listOf(FEEDBACK_EMAIL), intent.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
        assertEquals("Hashiya feedback", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("\n\nHashiya 0.3.0 (3) · Android 14 · Pixel 7 · en", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun rateTriesThePlayStoreAppThenTheWeb() {
        val intents = rateIntents()
        assertEquals(Uri.parse("market://details?id=com.etatech.hashiya"), intents[0].data)
        assertEquals(Uri.parse("https://play.google.com/store/apps/details?id=com.etatech.hashiya"), intents[1].data)
        assertFalse(intents.any { it.action != Intent.ACTION_VIEW })
    }

    @Test
    fun sendFeedbackStartsTheDraft() {
        assertTrue(activity.sendFeedback("Hashiya feedback", version, "en"))
        assertEquals(Intent.ACTION_SENDTO, shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun noEmailAppReportsFalseAndTheAddressCanBeCopied() {
        // A context whose startActivity always fails, as on a device with no email app.
        val noEmailApp = object : ContextWrapper(activity) {
            override fun startActivity(intent: Intent) = throw ActivityNotFoundException()
        }
        assertFalse(noEmailApp.sendFeedback("Hashiya feedback", version, "en"))
        activity.copyFeedbackAddress()
        val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals(FEEDBACK_EMAIL, clip.getItemAt(0).text.toString())
    }

    @Test
    fun ratingFallsBackToTheWebWhenThereIsNoPlayStore() {
        val started = mutableListOf<Intent>()
        val noPlayStore = object : ContextWrapper(activity) {
            override fun startActivity(intent: Intent) {
                if (intent.data?.scheme == "market") throw ActivityNotFoundException()
                started += intent
            }
        }
        noPlayStore.openRatePage()
        assertEquals(listOf("https"), started.map { it.data?.scheme })
    }
}
