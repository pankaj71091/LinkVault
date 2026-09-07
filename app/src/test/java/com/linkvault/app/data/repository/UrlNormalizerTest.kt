package com.linkvault.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [UrlNormalizer]. Pure-JVM, no Android dependencies.
 *
 * The normalizer is used for duplicate detection in [BookmarkRepository]
 * — every rule that affects equality needs a test, since a missing
 * rule means a user can save the same article twice by varying case,
 * a tracking param, or a trailing slash.
 */
class UrlNormalizerTest {

    // --- Scheme & host normalization ---

    @Test
    fun `lowercases scheme and host`() {
        assertEquals(
            "https://example.com/foo",
            UrlNormalizer.normalize("HTTPS://Example.COM/foo")
        )
    }

    @Test
    fun `strips www prefix from host`() {
        assertEquals(
            "https://example.com/foo",
            UrlNormalizer.normalize("https://www.example.com/foo")
        )
    }

    @Test
    fun `strips www only at the start of host`() {
        // A "www." later in the host (pathologically) should NOT be
        // stripped — only the first label.
        assertEquals(
            "https://www2.example.com/foo",
            UrlNormalizer.normalize("https://www2.example.com/foo")
        )
    }

    // --- Port handling ---

    @Test
    fun `drops default http port 80`() {
        assertEquals(
            "http://example.com/foo",
            UrlNormalizer.normalize("http://example.com:80/foo")
        )
    }

    @Test
    fun `drops default https port 443`() {
        assertEquals(
            "https://example.com/foo",
            UrlNormalizer.normalize("https://example.com:443/foo")
        )
    }

    @Test
    fun `keeps non-default port`() {
        assertEquals(
            "https://example.com:8080/foo",
            UrlNormalizer.normalize("https://example.com:8080/foo")
        )
    }

    @Test
    fun `keeps non-default port on http`() {
        assertEquals(
            "http://example.com:8080/foo",
            UrlNormalizer.normalize("http://example.com:8080/foo")
        )
    }

    // --- Query handling ---

    @Test
    fun `sorts query parameters by name`() {
        assertEquals(
            "https://example.com?a=1&b=2&c=3",
            UrlNormalizer.normalize("https://example.com?c=3&a=1&b=2")
        )
    }

    @Test
    fun `drops utm_ tracking parameters`() {
        assertEquals(
            "https://example.com?id=1",
            UrlNormalizer.normalize("https://example.com?id=1&utm_source=x&utm_medium=email")
        )
    }

    @Test
    fun `drops all listed tracking parameters`() {
        // Spot-check a few of the non-utm_ ones.
        assertEquals(
            "https://example.com?keep=1",
            UrlNormalizer.normalize("https://example.com?keep=1&fbclid=abc&gclid=def&msclkid=ghi")
        )
    }

    @Test
    fun `drops fragment`() {
        assertEquals(
            "https://example.com/article",
            UrlNormalizer.normalize("https://example.com/article#section-2")
        )
    }

    @Test
    fun `preserves query value with equals sign in it`() {
        // The value is the literal string after the first '=',
        // not parsed as key=value.
        assertEquals(
            "https://example.com?token=abc=def==",
            UrlNormalizer.normalize("https://example.com?token=abc=def==")
        )
    }

    // --- Path handling ---

    @Test
    fun `strips trailing slash on root path`() {
        assertEquals(
            "https://example.com",
            UrlNormalizer.normalize("https://example.com/")
        )
    }

    @Test
    fun `keeps trailing slash on non-root path`() {
        // Whether to strip a trailing slash on a deeper path is debatable
        // (some servers treat /foo and /foo/ as different resources).
        // We don't normalize that case — the existing behavior of just
        // preserving it is the safer default.
        assertEquals(
            "https://example.com/foo/",
            UrlNormalizer.normalize("https://example.com/foo/")
        )
    }

    // --- Identity / passthrough ---

    @Test
    fun `non-http scheme is passed through unchanged`() {
        assertEquals(
            "ftp://example.com/foo",
            UrlNormalizer.normalize("ftp://example.com/foo")
        )
    }

    @Test
    fun `mailto scheme is passed through unchanged`() {
        assertEquals(
            "mailto:user@example.com",
            UrlNormalizer.normalize("mailto:user@example.com")
        )
    }

    @Test
    fun `unparseable input is returned unchanged`() {
        val garbage = "not a url at all !!!"
        assertEquals(garbage, UrlNormalizer.normalize(garbage))
    }

    @Test
    fun `empty input returns empty`() {
        assertEquals("", UrlNormalizer.normalize(""))
    }

    @Test
    fun `whitespace-only input returns unchanged`() {
        assertEquals("   ", UrlNormalizer.normalize("   "))
    }

    @Test
    fun `input is trimmed before normalization`() {
        assertEquals(
            "https://example.com",
            UrlNormalizer.normalize("  https://example.com/  ")
        )
    }

    // --- Idempotency ---

    @Test
    fun `normalize is idempotent on a normalized input`() {
        val already = "https://example.com/foo?a=1&b=2"
        assertEquals(already, UrlNormalizer.normalize(already))
    }

    @Test
    fun `normalize is idempotent on a messy input`() {
        val messy = "  HTTPS://WWW.Example.COM:443/foo/?b=2&a=1#section&utm_source=x  "
        val once = UrlNormalizer.normalize(messy)
        val twice = UrlNormalizer.normalize(once)
        assertEquals(once, twice)
    }

    // --- "Is this the same bookmark?" — the real-world contract ---

    @Test
    fun `two cosmetic variants of the same URL normalize to the same string`() {
        val a = "HTTPS://Example.com/pasta?utm_source=twitter&id=42"
        val b = "https://www.example.com/pasta?id=42"
        assertEquals(UrlNormalizer.normalize(a), UrlNormalizer.normalize(b))
    }

    @Test
    fun `URLs that differ in path do NOT collapse`() {
        val a = "https://example.com/foo"
        val b = "https://example.com/bar"
        assertTrue(UrlNormalizer.normalize(a) != UrlNormalizer.normalize(b))
    }
}