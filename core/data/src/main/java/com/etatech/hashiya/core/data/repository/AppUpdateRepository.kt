package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.core.network.AppConfigDataSource
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface AppUpdateRepository {
    /** A required update when [currentVersionCode] is below the published minimum; null otherwise, and on any failure. */
    suspend fun requiredUpdate(currentVersionCode: Long): RequiredUpdate?
}

/** Never blocks by mistake: offline, an error, or a missing or malformed field all mean "no update required". */
internal class ConfigAppUpdateRepository @Inject constructor(private val dataSource: AppConfigDataSource) : AppUpdateRepository {
    override suspend fun requiredUpdate(currentVersionCode: Long): RequiredUpdate? {
        val config = try {
            dataSource.androidConfig()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        val minimum = config.minimumVersionCode ?: return null
        val storeUrl = config.storeUrl?.takeIf(::isHttpsWithHost) ?: return null
        return if (currentVersionCode < minimum) RequiredUpdate(storeUrl) else null
    }
}

/** A store link must be https with a host, or the Update button would lead nowhere. */
private fun isHttpsWithHost(link: String): Boolean {
    val uri = try {
        URI(link)
    } catch (e: URISyntaxException) {
        return false
    }
    return uri.scheme == "https" && !uri.host.isNullOrEmpty()
}
