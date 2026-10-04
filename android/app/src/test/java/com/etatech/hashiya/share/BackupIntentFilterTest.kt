package com.etatech.hashiya.share

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.MainActivity
import com.etatech.hashiya.openedBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupIntentFilterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun activitiesFor(type: String) = context.packageManager
        .queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://docs/backup.hashiya"), type).setPackage(context.packageName),
            0
        )
        .map { it.activityInfo.name }

    @Test
    fun openingAZipOpensMainActivity() {
        assertEquals(listOf(MainActivity::class.java.name), activitiesFor("application/zip"))
    }

    @Test
    fun otherFilesAreNotHandled() {
        assertTrue(activitiesFor("application/pdf").isEmpty())
    }

    @Test
    fun onlyAContentUriIsOpenedAsABackup() {
        assertEquals("content://docs/backup.hashiya", Intent(Intent.ACTION_VIEW, Uri.parse("content://docs/backup.hashiya")).openedBackup())
        // A file URI would let any app point the restore at the app's own private files.
        assertNull(Intent(Intent.ACTION_VIEW, Uri.parse("file:///data/data/com.etatech.hashiya/x.hashiya")).openedBackup())
        assertNull(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/backup.hashiya")).openedBackup())
        assertNull(Intent(Intent.ACTION_SEND, Uri.parse("content://docs/backup.hashiya")).openedBackup())
    }
}
