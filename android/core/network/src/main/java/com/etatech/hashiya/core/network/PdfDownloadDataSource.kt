package com.etatech.hashiya.core.network

import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Fetches a paper's open-access PDF from wherever its link points: arXiv, a repository or a publisher. */
interface PdfDownloadDataSource {
    /**
     * GETs [url], following redirects, and hands the body to [consume] with its length when the server sends one. [consume] runs
     * on a network thread and may block. Cancelling the caller cancels the request, which makes [consume]'s reads fail.
     * @throws NetworkException [NetworkFailure.Connectivity] when the server can't be reached or the body breaks off,
     *   [NetworkFailure.Http] for an error status, or with code 0 for a link that isn't a URL or a timeout (spec §11: the server
     *   didn't send the PDF). Other exceptions from [consume] are passed on.
     */
    suspend fun <T> download(url: String, consume: (body: InputStream, contentLength: Long?) -> T): T
}

/**
 * A plain client, separate from OpenAlex's, so the API key never goes to other hosts. The call timeout bounds a whole download.
 */
internal fun buildPdfOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(2, TimeUnit.MINUTES)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

internal class OkHttpPdfDownloadDataSource(private val client: OkHttpClient) : PdfDownloadDataSource {
    override suspend fun <T> download(url: String, consume: (body: InputStream, contentLength: Long?) -> T): T {
        val httpUrl = url.toHttpUrlOrNull() ?: throw NetworkException(NetworkFailure.Http(code = 0, usedUserKey = false))
        val call = client.newCall(Request.Builder().url(httpUrl).header("Accept", "application/pdf, */*").build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e.asDownloadFailure())
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                if (!it.isSuccessful) throw NetworkException(NetworkFailure.Http(code = it.code, usedUserKey = false))
                                consume(it.body.byteStream(), it.body.contentLength().takeIf { length -> length >= 0 })
                            }
                        }
                        result.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { e ->
                                val failure = if (e is IOException) e.asDownloadFailure() else e
                                continuation.resumeWithException(failure)
                            }
                        )
                    }
                }
            )
        }
    }
}

/** A timeout (connect, read or the whole call) means the server didn't send the PDF in time; other I/O failures are connectivity. */
private fun IOException.asDownloadFailure(): NetworkException = NetworkException(
    if (this is InterruptedIOException) NetworkFailure.Http(code = 0, usedUserKey = false) else NetworkFailure.Connectivity,
    this
)
