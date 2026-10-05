package com.etatech.hashiya.analytics

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AnalyticsManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun analyticsIsOffAndAdIdsAreNotCollectedUntilTheAppSaysSo() {
        val metaData = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        for (
        key in listOf(
            "firebase_analytics_collection_enabled",
            "google_analytics_adid_collection_enabled",
            "google_analytics_ssaid_collection_enabled",
            "google_analytics_default_allow_ad_personalization_signals",
            "google_analytics_automatic_screen_reporting_enabled"
        )
        ) {
            assertFalse(key, metaData.getBoolean(key, true))
        }
    }

    @Test
    fun theAppDoesNotRequestTheAdvertisingIdPermission() {
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()

        assertFalse(requested.contains("com.google.android.gms.permission.AD_ID"))
        assertFalse(requested.contains("android.permission.ACCESS_ADSERVICES_AD_ID"))
    }
}
