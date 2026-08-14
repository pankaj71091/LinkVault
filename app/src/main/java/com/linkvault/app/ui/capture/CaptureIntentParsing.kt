package com.linkvault.app.ui.capture

import android.content.Intent

data class CapturedLink(val url: String, val title: String?)

private val urlPattern = Regex("""https?://\S+""")

/**
 * Pulls a URL (and, when available, a title) out of an incoming capture
 * Intent — either a share (ACTION_SEND, from the system share sheet) or a
 * direct link open (ACTION_VIEW, from being picked as a browser/link
 * handler — e.g. in LinkSheet's chooser, or as Android's default browser).
 */
fun extractCapturedLink(intent: Intent): CapturedLink? = when (intent.action) {
    Intent.ACTION_SEND -> extractFromSend(intent)
    Intent.ACTION_VIEW -> extractFromView(intent)
    else -> null
}

/**
 * EXTRA_TEXT isn't guaranteed to be a bare URL — some apps share it
 * surrounded by other text (e.g. "Check this out: https://...") — so this
 * looks for the first http(s) URL inside the shared text rather than
 * assuming the whole string is the link. Falls back to the raw text if no
 * URL-shaped substring is found; the save screen's URL field is editable,
 * so a bad guess here is just something the user corrects or cancels,
 * not a dead end.
 *
 * EXTRA_SUBJECT, when present, is used as a starting title — free data
 * already sitting in the intent, so there's no reason to throw it away
 * even though title auto-fetch itself doesn't happen until Phase 3.
 */
private fun extractFromSend(intent: Intent): CapturedLink? {
    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
    if (sharedText.isNullOrBlank()) return null

    val url = urlPattern.find(sharedText)?.value ?: sharedText
    val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotBlank() }
    return CapturedLink(url = url, title = title)
}

/**
 * ACTION_VIEW carries the URL as the intent's data URI, not as extras, and
 * carries no title metadata at all — Phase 3's auto-fetch is what fills
 * titles in for links captured this way.
 */
private fun extractFromView(intent: Intent): CapturedLink? {
    val url = intent.data?.toString()?.takeIf { it.isNotBlank() } ?: return null
    return CapturedLink(url = url, title = null)
}
