package com.linkvault.app.data.repository

import java.net.URI
import java.net.URISyntaxException

/**
 * Canonicalizes URLs for storage and duplicate detection.
 *
 * Rules applied (in order):
 *  1. Lowercase the scheme and host.
 *  2. Strip a leading "www." from the host.
 *  3. Drop default ports: 80 for http, 443 for https.
 *  4. Sort query parameters by name (stable equality, not for caching).
 *  5. Drop common tracking parameters: utm_*, fbclid, gclid, msclkid,
 *     ref, ref_src, mc_cid, mc_eid.
 *  6. Drop the fragment — for a saved page the "#section" is rarely
 *     semantically meaningful (and certainly shouldn't block dedup).
 *  7. Strip a trailing "/" on a path-only root URL (so "/" becomes "").
 *
 * Non-http(s) schemes and unparseable input are returned **unchanged**:
 * saving a slightly-noisy duplicate beats throwing and losing the share.
 *
 * Uses [java.net.URI] (not android.net.Uri) so the implementation
 * works in plain JVM unit tests under `src/test/`, where Android's
 * Uri class isn't on the classpath.
 */
object UrlNormalizer {

    /** Tracking params to drop. Case-sensitive match against the param name. */
    private val TRACKING_PARAMS: Set<String> = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "msclkid", "ref", "ref_src", "mc_cid", "mc_eid"
    )

    /**
     * Returns the canonical form of [rawUrl], or [rawUrl] itself if it
     * can't be parsed or isn't an http/https URL. Never returns null,
     * never throws.
     */
    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return rawUrl

        val parsed: URI = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            return rawUrl
        }

        // Only normalize http and https. Other schemes (mailto:, file:,
        // ftp:, custom schemes) are passed through unchanged.
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return rawUrl

        val host = parsed.host?.lowercase()?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() } ?: return rawUrl

        // Drop default ports. URI.getPort() returns -1 when no port was specified.
        val port = when {
            parsed.port == -1 -> null
            scheme == "http" && parsed.port == 80 -> null
            scheme == "https" && parsed.port == 443 -> null
            else -> parsed.port
        }

        // Path: keep it; drop the trailing "/" on a root path ("/" -> "").
        val rawPath = parsed.rawPath
        val path = if (rawPath == "/" || rawPath.isNullOrEmpty()) "" else rawPath

        // Query: sort by name, drop tracking params, drop empty values
        // (a bare "?foo=" becomes nothing).
        val query = parsed.rawQuery
        val sortedQuery = query
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) pair.takeIf { it.isNotBlank() }
                else {
                    val name = pair.substring(0, eq)
                    val value = pair.substring(eq + 1)
                    if (name in TRACKING_PARAMS) null
                    else if (value.isEmpty()) name
                    else "$name=$value"
                }
            }
            ?.sortedWith(compareBy(String::length, String::lowercase))
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("&")

        // Fragment is intentionally dropped (rule 6).

        val portPart = port?.let { ":$it" } ?: ""
        val pathPart = if (path.isEmpty()) "" else path
        val queryPart = sortedQuery?.let { "?$it" } ?: ""

        return "$scheme://$host$portPart$pathPart$queryPart"
    }
}