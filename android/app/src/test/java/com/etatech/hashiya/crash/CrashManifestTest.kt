package com.etatech.hashiya.crash

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CrashManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val metaData = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData

    @Test
    fun crashlyticsStartsOff() {
        assertEquals(false, metaData.getBoolean("firebase_crashlytics_collection_enabled", true))
    }

    @Test
    fun firebaseSessionsAreOff() {
        assertEquals(false, metaData.getBoolean("firebase_sessions_enabled", true))
    }
}
