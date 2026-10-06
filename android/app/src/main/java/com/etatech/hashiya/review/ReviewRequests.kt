package com.etatech.hashiya.review

import com.etatech.hashiya.core.review.ReviewRequester
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Requests for the store's rating prompt; [com.etatech.hashiya.MainActivity] shows them while it is started. */
@Singleton
class ReviewRequests @Inject constructor() : ReviewRequester {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    override fun request() {
        _requests.tryEmit(Unit)
    }
}
