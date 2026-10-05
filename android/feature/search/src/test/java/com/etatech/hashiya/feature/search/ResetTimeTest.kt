package com.etatech.hashiya.feature.search

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.feature.search.components.formatResetTime
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResetTimeTest {
    private val midnightUtc = Instant.parse("2026-10-06T00:00:00Z").toEpochMilli()
    private val riyadh = ZoneId.of("Asia/Riyadh")

    @Test
    fun resetTimeIsShownInTheGivenZone() {
        val time = formatResetTime(midnightUtc, Locale.US, riyadh)

        assertTrue(time, time.contains("3:00"))
        assertTrue(time, time.contains("AM"))
    }

    @Test
    fun arabicMessageIsolatesTheTimeExactlyOnce() {
        val arabic = Locale("ar")
        val config = Configuration(ApplicationProvider.getApplicationContext<Context>().resources.configuration).apply {
            setLocale(arabic)
        }
        val context = ApplicationProvider.getApplicationContext<Context>().createConfigurationContext(config)
        val time = formatResetTime(midnightUtc, arabic, riyadh)

        val message = context.getString(R.string.search_error_daily_limit_message, time)

        assertTrue(message, message.contains("\u2068$time\u2069"))
        assertEquals(1, message.count { it == '\u2068' })
        assertEquals(1, message.count { it == '\u2069' })
    }
}
