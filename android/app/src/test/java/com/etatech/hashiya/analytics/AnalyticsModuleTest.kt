package com.etatech.hashiya.analytics

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.analytics.NoOpAnalytics
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AnalyticsModuleTest {
    @Test
    fun debugBuildsBindTheNoOpAnalytics() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertSame(NoOpAnalytics, AnalyticsModule.provideAnalytics(context))
    }
}
