package com.etatech.hashiya

import android.app.Application
import com.etatech.hashiya.core.data.di.ApplicationScope
import com.etatech.hashiya.core.data.repository.PdfRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class HashiyaApplication : Application() {
    @Inject
    lateinit var pdfRepository: PdfRepository

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Files left by a crash mid-download, or by a removal made final while the app was killed during Undo.
        scope.launch { pdfRepository.sweepOrphans() }
    }
}
