package com.etatech.hashiya.crash

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.crash.NoOpCrashReporter
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CrashModuleTest {
    @Test
    fun debugBuildsBindTheNoOpReporter() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertSame(NoOpCrashReporter, CrashModule.provideCrashReporter(context))
    }
}
