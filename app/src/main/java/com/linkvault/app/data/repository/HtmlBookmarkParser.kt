package com.linkvault.app.data.repository

import android.text.Html

data class ParsedBookmark(
    val url: String,
    val title: String?,
    val folderPath: List<String>
)

/**
 * A simple regex-based parser for Netscape HTML bookmark files (exported
 * by Chrome, Firefox, etc.).
 *
 * It tracks the current folder nesting level by watching for <H3> (folder
 * names) and </DL> (end of folder contents).
 */
object HtmlBookmarkParser {
    private val aTagRegex = Regex("""<A\b[^>]*HREF=["']([^"']*)["'][^>]*>(.*?)</A>""", RegexOption.IGNORE_CASE)
    private val h3TagRegex = Regex("""<H3\b[^>]*>(.*?)</H3>""", RegexOption.IGNORE_CASE)
    private val dlCloseRegex = Regex("""</DL>""", RegexOption.IGNORE_CASE)
    private val dtRegex = Regex("""<DT>""", RegexOption.IGNORE_CASE)

    fun parse(html: String): List<ParsedBookmark> {
        val bookmarks = mutableListOf<ParsedBookmark>()
        val folderStack = mutableListOf<String>()

        // Split by <DT> to process each entry (folder or bookmark)
        val parts = html.split(dtRegex)

        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue

            // 1. Check if this DT starts a folder
            val h3Match = h3TagRegex.find(trimmed)
            if (h3Match != null) {
                val folderName = decodeHtml(h3Match.groupValues[1])
                folderStack.add(folderName)
            }

            // 2. Check if this DT is a bookmark
            val aMatch = aTagRegex.find(trimmed)
            if (aMatch != null) {
                val url = aMatch.groupValues[1]
                val title = decodeHtml(aMatch.groupValues[2])
                bookmarks.add(
                    ParsedBookmark(
                        url = url,
                        title = title.takeIf { it.isNotBlank() },
                        folderPath = folderStack.toList()
                    )
                )
            }

            // 3. Check for folder closures in this block
            val closures = dlCloseRegex.findAll(trimmed).count()
            repeat(closures) {
                if (folderStack.isNotEmpty()) {
                    folderStack.removeAt(folderStack.size - 1)
                }
            }
        }

        return bookmarks
    }

    private fun decodeHtml(input: String): String {
        return try {
            Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (e: Exception) {
            input.trim()
        }
    }
}
