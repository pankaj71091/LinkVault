package com.linkvault.app.ui.common

import android.net.Uri

/** "https://www.example.com/some/long/path?query=1" -> "example.com" */
fun displayDomain(url: String): String {
    val host = try {
        Uri.parse(url).host
    } catch (e: Exception) {
        null
    }
    return host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url
}
