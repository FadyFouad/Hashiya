package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.AppUpdateRepository
import com.etatech.hashiya.core.model.RequiredUpdate
import kotlinx.coroutines.CompletableDeferred

class FakeAppUpdateRepository : AppUpdateRepository {
    var result: RequiredUpdate? = null
    val checkedVersionCodes = mutableListOf<Long>()
    private var gate: CompletableDeferred<Unit>? = null

    /** Makes every following check wait until [releaseChecks], so tests can see a check in progress. */
    fun holdChecks() {
        gate = CompletableDeferred()
    }

    fun releaseChecks() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun requiredUpdate(currentVersionCode: Long): RequiredUpdate? {
        checkedVersionCodes += currentVersionCode
        gate?.await()
        return result
    }
}
