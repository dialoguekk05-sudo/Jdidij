package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebSource(
    val title: String,
    val snippet: String,
    val url: String
)

class WebSearchRepository {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun searchWeb(query: String, preferHindi: Boolean = false): List<WebSource> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<WebSource>()
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) return@withContext emptyList()

            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")

            // 1. Query DuckDuckGo Instant Answer API
            try {
                val ddgReq = Request.Builder()
                    .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                    .header("User-Agent", "JarvisAI-Android/1.0")
                    .build()
                client.newCall(ddgReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val abstractText = json.optString("AbstractText", "")
                            val abstractUrl = json.optString("AbstractURL", "")
                            val heading = json.optString("Heading", cleanQuery)
                            if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
                                results.add(WebSource(heading, abstractText, abstractUrl))
                            }
                            val related = json.optJSONArray("RelatedTopics")
                            if (related != null) {
                                for (i in 0 until minOf(related.length(), 4)) {
                                    val item = related.optJSONObject(i) ?: continue
                                    val text = item.optString("Text", "")
                                    val firstUrl = item.optString("FirstURL", "")
                                    if (text.isNotBlank() && firstUrl.isNotBlank()) {
                                        val title = text.substringBefore(" - ").take(64)
                                        results.add(WebSource(title, text, firstUrl))
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("WebSearchRepo", "DuckDuckGo query failed: ${e.message}")
            }

            // 2. Query Wikipedia OpenSearch API (Hindi or English)
            val wikiHost = if (preferHindi || containsDevanagari(cleanQuery)) {
                "hi.wikipedia.org"
            } else {
                "en.wikipedia.org"
            }

            try {
                val wikiUrl =
                    "https://$wikiHost/w/api.php?action=opensearch&search=$encoded&limit=4&namespace=0&format=json"
                val wikiReq = Request.Builder()
                    .url(wikiUrl)
                    .header("User-Agent", "JarvisAI-Android/1.0")
                    .build()
                client.newCall(wikiReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.startsWith("[")) {
                            val arr = JSONArray(body)
                            val titles = arr.optJSONArray(1)
                            val snippets = arr.optJSONArray(2)
                            val urls = arr.optJSONArray(3)
                            if (titles != null && urls != null) {
                                for (i in 0 until minOf(titles.length(), 4)) {
                                    val title = titles.optString(i, "")
                                    val desc = snippets?.optString(i, "").orEmpty()
                                    val link = urls.optString(i, "")
                                    if (title.isNotBlank() && link.isNotBlank()) {
                                        val summary = fetchWikiSummary(wikiHost, title).ifBlank { desc }
                                        results.add(
                                            WebSource(
                                                title = title,
                                                snippet = summary.ifBlank { "Wikipedia article on $title" },
                                                url = link
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("WebSearchRepo", "Wikipedia query failed: ${e.message}")
            }

            results.distinctBy { it.url }.take(5)
        }

    private fun fetchWikiSummary(host: String, title: String): String {
        return try {
            val encodedTitle = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
            val req = Request.Builder()
                .url("https://$host/api/rest_v1/page/summary/$encodedTitle")
                .header("User-Agent", "JarvisAI-Android/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    json.optString("extract", "").take(450)
                } else {
                    ""
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun containsDevanagari(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }
}
