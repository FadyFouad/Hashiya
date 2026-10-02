package com.etatech.hashiya.core.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal const val ARXIV_BASE_URL = "https://export.arxiv.org/"
internal const val ARXIV_USER_AGENT = "Hashiya-Android (https://github.com/FadyFouad/Hashiya)"

/** Reads paper titles from arXiv's API. Used only to check OpenAlex matches; never sends the OpenAlex key. */
interface ArxivDataSource {
    /**
     * The title arXiv has for [id] (e.g. "1810.04805", "hep-th/9901001"), or null when arXiv has no such paper.
     * @throws NetworkException when arXiv can't be reached or answers with an error or unreadable data.
     */
    suspend fun title(id: String): String?
}

/** A plain client: no OpenAlex API-key interceptor, no logging of arXiv traffic. */
internal fun buildArxivOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

internal class OkHttpArxivDataSource(private val client: OkHttpClient, private val baseUrl: HttpUrl) : ArxivDataSource {
    override suspend fun title(id: String): String? {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/query")
            .addQueryParameter("id_list", id)
            .build()
        val request = Request.Builder().url(url).header("User-Agent", ARXIV_USER_AGENT).build()
        val body = client.newCall(request).awaitBody()
        return withContext(Dispatchers.Default) { parseArxivTitle(body) }
    }
}

/** The response body; cancelling the coroutine cancels the call, so a dropped lookup stops reaching arXiv. */
internal suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(NetworkException(NetworkFailure.Connectivity, e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use {
                        if (!it.isSuccessful) {
                            throw NetworkException(NetworkFailure.Http(code = it.code, usedUserKey = false))
                        }
                        it.body.string()
                    }
                    continuation.resume(body)
                } catch (e: IOException) {
                    continuation.resumeWithException(NetworkException(NetworkFailure.Connectivity, e))
                } catch (e: NetworkException) {
                    continuation.resumeWithException(e)
                }
            }
        }
    )
}

private val FEED = Regex("""<feed[\s>]""")
private val ENTRY = Regex("""<entry>(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL)
private val ENTRY_ID = Regex("""<id>(.*?)</id>""", RegexOption.DOT_MATCHES_ALL)
private val ENTRY_TITLE = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
private val WHITESPACE = Regex("""\s+""")
private val NUMERIC_ENTITY = Regex("""&#(x[0-9a-fA-F]+|\d+);""")

/** The first entry's title, or null for an empty feed or an arXiv error entry. */
internal fun parseArxivTitle(xml: String): String? {
    if (!FEED.containsMatchIn(xml)) throw NetworkException(NetworkFailure.MalformedResponse)
    val entry = ENTRY.find(xml)?.groupValues?.get(1) ?: return null
    if ("/api/errors" in ENTRY_ID.find(entry)?.groupValues?.get(1).orEmpty()) return null
    val rawTitle = ENTRY_TITLE.find(entry)?.groupValues?.get(1) ?: throw NetworkException(NetworkFailure.MalformedResponse)
    return decodeXmlEntities(rawTitle).trim().replace(WHITESPACE, " ").ifEmpty { null }
}

private fun decodeXmlEntities(value: String): String = NUMERIC_ENTITY.replace(value) { match ->
    val code = match.groupValues[1]
    val codePoint = if (code.startsWith("x")) code.drop(1).toInt(16) else code.toInt()
    String(Character.toChars(codePoint))
}
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
