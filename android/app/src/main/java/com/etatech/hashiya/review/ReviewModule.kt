package com.etatech.hashiya.review

import android.content.Context
import com.etatech.hashiya.core.review.DefaultReviewPrompt
import com.etatech.hashiya.core.review.NoOpReviewPrompt
import com.etatech.hashiya.core.review.ReviewPrompt
import com.etatech.hashiya.crash.isDebuggable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReviewModule {
    /** Debug builds (and so every UI and screenshot test) never ask. */
    @Provides
    @Singleton
    fun provideReviewPrompt(@ApplicationContext context: Context, requests: ReviewRequests): ReviewPrompt =
        if (isDebuggable(context)) NoOpReviewPrompt else DefaultReviewPrompt(SharedPreferencesReviewCounterStore(context), requests)
}
