package com.audiiptv.app

object M3UParser {

    fun parseFromString(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()

        var currentName = ""
        var currentCategory = "Uncategorized"
        var currentLogo: String? = null

        val regexName = Regex("""#EXTINF:-1.*?,(.*)""")
        val regexCategory = Regex("""group-title="(.*?)"""")
        val regexLogo = Regex("""tvg-logo="(.*?)"""")

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF", true)) {
                // Extract channel name
                currentName = regexName.find(line)?.groupValues?.getOrNull(1)?.trim() ?: "Unknown"

                // Extract group/category
                currentCategory = regexCategory.find(line)?.groupValues?.getOrNull(1)?.trim() ?: "Uncategorized"

                // Extract logo URL
                currentLogo = regexLogo.find(line)?.groupValues?.getOrNull(1)?.trim()

            } else if (line.isNotBlank() && !line.startsWith("#")) {
                // Treat this as the stream URL
                val streamUrl = line
                val channel = Channel(
                    name = currentName,
                    url = streamUrl,
                    category = currentCategory,
                    isFavorite = false,
                    logo = currentLogo
                )
                channels.add(channel)

                // Reset after adding one channel
                currentName = ""
                currentCategory = "Uncategorized"
                currentLogo = null
            }
        }

        return channels
    }
}
