package com.hertzds.agent.tools

import com.hertzds.agent.AgentTool
import com.hertzds.agent.ToolContext
import com.hertzds.agent.ToolResult
import com.hertzds.agent.int
import com.hertzds.agent.intProp
import com.hertzds.agent.requireString
import com.hertzds.agent.schema
import com.hertzds.agent.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

private const val UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

/**
 * Search via DuckDuckGo's no-API HTML endpoints. No key, no account — which is the
 * whole point of a local agent. The lite endpoint is the fallback when the main
 * HTML layout changes or gets rate limited.
 */
class WebSearchTool(private val http: OkHttpClient) : AgentTool {

    override val name = "web_search"
    override val description =
        "Search the web and return ranked results with title, URL and snippet. " +
            "Use this whenever the answer depends on current or external information."
    override val parameters = schema(
        "query" to stringProp("The search query."),
        "max_results" to intProp("How many results to return (1-15, default 6)."),
        required = listOf("query"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult =
        withContext(Dispatchers.IO) {
            val query = args.requireString("query")
            val limit = (args.int("max_results") ?: 6).coerceIn(1, 15)
            context.onProgress(query)

            val results = runCatching { searchHtml(query, limit) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: runCatching { searchLite(query, limit) }.getOrElse {
                    return@withContext ToolResult.error("search failed: ${it.message}")
                }

            if (results.isEmpty()) {
                return@withContext ToolResult("No results for \"$query\".")
            }

            val rendered = results.mapIndexed { index, result ->
                "${index + 1}. ${result.title}\n   ${result.url}\n   ${result.snippet}"
            }.joinToString("\n\n")
            ToolResult("Search results for \"$query\":\n\n$rendered")
        }

    private data class SearchResult(val title: String, val url: String, val snippet: String)

    private fun searchHtml(query: String, limit: Int): List<SearchResult> {
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .header("User-Agent", UA)
            .post(FormBody.Builder().add("q", query).add("kl", "wt-wt").build())
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string().orEmpty()
        }
        return Jsoup.parse(body).select("div.result, div.web-result").mapNotNull { element ->
            val anchor = element.selectFirst("a.result__a") ?: return@mapNotNull null
            val href = cleanUrl(anchor.attr("href"))
            if (href.isBlank()) return@mapNotNull null
            SearchResult(
                title = anchor.text().trim(),
                url = href,
                snippet = element.selectFirst(".result__snippet")?.text()?.trim().orEmpty(),
            )
        }.take(limit)
    }

    private fun searchLite(query: String, limit: Int): List<SearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://lite.duckduckgo.com/lite/?q=$encoded")
            .header("User-Agent", UA)
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string().orEmpty()
        }
        val document = Jsoup.parse(body)
        val links = document.select("a.result-link")
        val snippets = document.select("td.result-snippet")
        return links.mapIndexedNotNull { index, anchor ->
            val href = cleanUrl(anchor.attr("href"))
            if (href.isBlank()) return@mapIndexedNotNull null
            SearchResult(
                title = anchor.text().trim(),
                url = href,
                snippet = snippets.getOrNull(index)?.text()?.trim().orEmpty(),
            )
        }.take(limit)
    }

    /** DuckDuckGo wraps hits in /l/?uddg=<encoded target>. */
    private fun cleanUrl(raw: String): String {
        val href = if (raw.startsWith("//")) "https:$raw" else raw
        val marker = "uddg="
        val index = href.indexOf(marker)
        if (index < 0) return href
        val encoded = href.substring(index + marker.length).substringBefore("&")
        return runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(href)
    }
}

/** Fetches a page and hands the model readable text instead of markup. */
class FetchUrlTool(private val http: OkHttpClient) : AgentTool {

    override val name = "fetch_url"
    override val description =
        "Open a URL and return its readable text content (markup, scripts and navigation stripped). " +
            "Use after web_search to actually read a promising page."
    override val parameters = schema(
        "url" to stringProp("Absolute http(s) URL to fetch."),
        "max_chars" to intProp("Truncate the extracted text to this many characters (default 6000)."),
        required = listOf("url"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult =
        withContext(Dispatchers.IO) {
            val url = args.requireString("url")
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext ToolResult.error("only http(s) URLs are supported")
            }
            val limit = (args.int("max_chars") ?: 6000).coerceIn(500, 40_000)
            context.onProgress(url)

            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", UA).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ToolResult.error("HTTP ${response.code} for $url")
                    }
                    val contentType = response.header("Content-Type").orEmpty()
                    val raw = response.body?.string().orEmpty()
                    if (contentType.contains("json") || contentType.contains("text/plain")) {
                        return@withContext ToolResult(raw.take(limit))
                    }
                    val document = Jsoup.parse(raw, url)
                    document.select("script, style, noscript, svg, nav, footer, header, aside, form").remove()
                    val title = document.title().trim()
                    val text = document.body()?.text()?.replace(Regex("\\s{2,}"), " ")?.trim().orEmpty()
                    val truncated = text.take(limit)
                    val suffix = if (text.length > limit) "\n\n[truncated at $limit characters]" else ""
                    ToolResult("# $title\nSource: $url\n\n$truncated$suffix")
                }
            }.getOrElse { ToolResult.error("could not fetch $url: ${it.message}") }
        }
}
