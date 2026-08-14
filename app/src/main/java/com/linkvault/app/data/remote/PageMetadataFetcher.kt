package com.linkvault.app.data.remote

import android.text.Html
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

data class PageMetadata(
    val title: String?,
    val faviconUrl: String?,
    val previewImageUrl: String? = null
)

/**
 * Fetches just enough of a page to pull out its <title> and favicon —
 * deliberately not a full HTML parser (no Jsoup dependency): a handful of
 * targeted regexes over the first chunk of the response is enough for what
 * this needs, and keeps this feature from pulling in a heavier library.
 *
 * Every failure mode (bad URL, timeout, non-HTML response, malformed HTML)
 * results in a null return rather than a thrown exception — this is
 * best-effort enrichment of an already-saved bookmark, never something
 * that should be able to fail loudly.
 */
object PageMetadataFetcher {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    // The <title> and favicon <link> tags are always in <head>, near the
    // top of the document — no need to download an entire (possibly huge)
    // page to find them.
    private const val MAX_BYTES_TO_READ = 65_536

    private val USER_AGENT =
        "Mozilla/5.0 (Linux; Android) LinkVault/1.0 (+bookmark metadata fetch)"

    fun fetch(url: String): PageMetadata? = try {
        fetchInternal(url)
    } catch (e: Exception) {
        null
    }

    private fun fetchInternal(url: String): PageMetadata? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }

        try {
            connection.connect()

            if (connection.responseCode !in 200..299) return null

            val contentType = connection.contentType.orEmpty()
            if (!contentType.contains("text/html", ignoreCase = true) &&
                !contentType.contains("application/xhtml", ignoreCase = true)
            ) {
                return null
            }

            // Reflects wherever redirects ultimately landed — needed to
            // correctly resolve a favicon href that turns out to be relative.
            val finalUrl = connection.url.toString()

            val html = connection.inputStream.use { stream ->
                readUpTo(stream, MAX_BYTES_TO_READ).toString(charsetFrom(contentType))
            }

            val title = extractTitle(html)
            val faviconUrl = extractFaviconUrl(html, finalUrl)

            if (title == null && faviconUrl == null) return null
            return PageMetadata(
                title = title,
                faviconUrl = faviconUrl,
                previewImageUrl = null // Disabled for performance
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * A single InputStream.read() call isn't guaranteed to fill the buffer
     * even when more data is available — this loops until either the cap
     * is hit or the stream ends.
     */
    private fun readUpTo(stream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var totalRead = 0
        while (totalRead < maxBytes) {
            val read = stream.read(buffer, totalRead, maxBytes - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return buffer.copyOf(totalRead)
    }

    private fun charsetFrom(contentType: String): Charset {
        val match = Regex("""charset=([^;\s]+)""", RegexOption.IGNORE_CASE).find(contentType)
        val name = match?.groupValues?.get(1)?.trim('"', '\'')
        return try {
            if (name != null) Charset.forName(name) else Charsets.UTF_8
        } catch (e: Exception) {
            Charsets.UTF_8
        }
    }

    private val titleRegex = Regex(
        """<title[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private fun extractTitle(html: String): String? {
        val raw = titleRegex.find(html)?.groupValues?.get(1) ?: return null
        val decoded = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
        val cleaned = decoded.replace(Regex("""\s+"""), " ").trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private val linkTagRegex = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val relAttrRegex = Regex("""rel\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val hrefAttrRegex = Regex("""href\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

    /**
     * Prefers an explicit <link rel="icon"> (or "shortcut icon", or
     * "apple-touch-icon" — all matched by checking for "icon" as a
     * substring of rel, since attribute order in real-world HTML isn't
     * reliable enough to assume rel always comes before href). Falls back
     * to the conventional /favicon.ico path if no explicit tag is found.
     * Doesn't verify the fallback actually exists — that's the image
     * layer's job (it already needs a loading-failure placeholder anyway).
     */
    private fun extractFaviconUrl(html: String, pageUrl: String): String? {
        val explicitHref = linkTagRegex.findAll(html)
            .mapNotNull { match ->
                val tag = match.value
                val rel = relAttrRegex.find(tag)?.groupValues?.get(1)?.lowercase()
                if (rel != null && "icon" in rel) hrefAttrRegex.find(tag)?.groupValues?.get(1) else null
            }
            .firstOrNull()

        val href = explicitHref?.takeIf { it.isNotBlank() } ?: "/favicon.ico"

        return try {
            URL(URL(pageUrl), href).toString()
        } catch (e: Exception) {
            null
        }
    }
}
