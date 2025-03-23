package com.audiiptv.app

data class Channel(
    val name: String,
    val url: String,
    var isFavorite: Boolean = false,
    val category: String = "Uncategorized",
    val logo: String? = null // ✅ Add this line

)
