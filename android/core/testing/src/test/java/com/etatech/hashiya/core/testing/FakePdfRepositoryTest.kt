package com.etatech.hashiya.core.testing

import android.net.Uri
import com.etatech.hashiya.core.data.repository.AttachResult
import com.etatech.hashiya.core.data.repository.DownloadFailure
import com.etatech.hashiya.core.data.repository.DownloadState
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.model.PdfStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FakePdfRepositoryTest {
    private val fake = FakePdfRepository()

    @Test
    fun attachDoneStoresAnAttachedPdf() = runTest {
        assertEquals(AttachResult.Done, fake.attach("W1", Uri.parse("content://docs/1")))

        assertEquals(PdfSource.Attached, fake.observePdf("W1").first()!!.source)
        assertEquals(listOf("W1"), fake.attaches.map { it.first })
    }

    @Test
    fun aFailedAttachChangesNothing() = runTest {
        fake.setAttachResult(AttachResult.NotPdf)

        assertEquals(AttachResult.NotPdf, fake.attach("W1", Uri.parse("content://docs/1")))
        assertNull(fake.observePdf("W1").first())
    }

    @Test
    fun deleteDownloadedKeepsAttached() = runTest {
        fake.setPdf("W1", PaperPdf(PdfSource.Downloaded, 1, 0))
        fake.setPdf("W2", PaperPdf(PdfSource.Attached, 1, 0))

        fake.deleteDownloaded()

        assertNull(fake.observePdf("W1").first())
        assertEquals(PdfSource.Attached, fake.observePdf("W2").first()!!.source)
    }

    @Test
    fun deleteDownloadedZeroesTheDownloadedStorage() = runTest {
        fake.setStorage(PdfStorage(downloadedBytes = 5_000, downloadedCount = 2, attachedBytes = 700, attachedCount = 1))

        fake.deleteDownloaded()

        assertEquals(PdfStorage(downloadedBytes = 0, downloadedCount = 0, attachedBytes = 700, attachedCount = 1), fake.storage())
        assertEquals(1, fake.deleteDownloadedCalls)
    }

    @Test
    fun cancelClearsTheDownloadState() = runTest {
        fake.setDownload("W1", DownloadState.Failed(DownloadFailure.Offline))

        fake.cancelDownload("W1")

        assertNull(fake.observeDownload("W1").first())
    }
}
