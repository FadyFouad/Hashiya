package com.etatech.hashiya.crash

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TestCrashReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun onlyTheShellCanTriggerTheTestCrash() {
        val info = context.packageManager.getReceiverInfo(ComponentName(context, TestCrashReceiver::class.java), 0)

        assertEquals("android.permission.DUMP", info.permission)
        assertEquals(true, info.exported)
    }

    @Test
    fun theTestCrashActionReachesOnlyThisReceiver() {
        val receivers = context.packageManager
            .queryBroadcastReceivers(Intent("com.etatech.hashiya.TEST_CRASH").setPackage(context.packageName), 0)
            .map { it.activityInfo.name }

        assertEquals(listOf(TestCrashReceiver::class.java.name), receivers)
    }
}
