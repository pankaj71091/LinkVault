package com.linkvault.app.ui.common

import com.linkvault.app.data.local.entity.Bookmark

enum class SortOption(val label: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    ALPHABETICAL("Alphabetical")
}

fun List<Bookmark>.sortedByOption(option: SortOption): List<Bookmark> = when (option) {
    SortOption.NEWEST_FIRST -> sortedByDescending { it.createdAt }
    SortOption.OLDEST_FIRST -> sortedBy { it.createdAt }
    SortOption.ALPHABETICAL -> sortedBy { (it.title?.takeIf { title -> title.isNotBlank() } ?: it.url).lowercase() }
}
