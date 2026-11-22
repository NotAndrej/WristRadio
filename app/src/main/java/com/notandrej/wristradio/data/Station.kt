package com.notandrej.wristradio.data

import org.json.JSONObject

/**
 * One entry from the radio-browser.info directory, flattened to the handful of
 * fields an internet-radio player actually needs.
 *
 * Instances round-trip through JSON: they are both what the directory search
 * returns and what is persisted for favourites, and the exact same encoding is
 * tucked into the playback [androidx.media3.common.MediaItem] so the now-playing
 * screen can recover the full station even after the process has been restarted
 * and only the media session survives.
 */
data class Station(
    val uuid: String,
    val name: String,
    val url: String,
    val codec: String = "",
    val bitrate: Int = 0,
    val country: String = "",
    val countryCode: String = "",
    val tags: String = "",
    val homepage: String = "",
    val favicon: String = "",
    val hls: Boolean = false,
    val votes: Int = 0,
) {

    /** "128 kbps · MP3 · GB" - only the parts we actually know. */
    val summary: String
        get() = buildList {
            if (bitrate > 0) add("$bitrate kbps")
            codec.trim().takeIf { it.isNotEmpty() && !it.equals("UNKNOWN", true) }
                ?.let { add(it.uppercase()) }
            countryCode.trim().takeIf { it.isNotEmpty() }?.let { add(it.uppercase()) }
        }.joinToString(" · ")

    /** True when the stream can carry ICY metadata (i.e. it is not an HLS playlist). */
    val isIcyCapable: Boolean
        get() = !hls && (url.startsWith("http://") || url.startsWith("https://"))

    fun toJson(): JSONObject = JSONObject().apply {
        put("stationuuid", uuid)
        put("name", name)
        put("url_resolved", url)
        put("codec", codec)
        put("bitrate", bitrate)
        put("country", country)
        put("countrycode", countryCode)
        put("tags", tags)
        put("homepage", homepage)
        put("favicon", favicon)
        put("hls", if (hls) 1 else 0)
        put("votes", votes)
    }

    companion object {
        /**
         * Builds a station from one directory object, or returns null when the
         * entry is unusable - no stream URL, no id, or a stream the directory
         * itself has already marked as broken. Every call site therefore gets
         * playable stations or nothing at all.
         */
        fun from(json: JSONObject): Station? {
            val url = json.optString("url_resolved").trim()
                .ifEmpty { json.optString("url").trim() }
            if (url.isEmpty()) return null

            val uuid = json.optString("stationuuid").trim()
            if (uuid.isEmpty()) return null

            if (json.has("lastcheckok") && json.optInt("lastcheckok", 1) != 1) return null

            return Station(
                uuid = uuid,
                name = json.optString("name").trim().ifEmpty { "Unnamed station" },
                url = url,
                codec = json.optString("codec").trim(),
                bitrate = json.optInt("bitrate"),
                country = json.optString("country").trim(),
                countryCode = json.optString("countrycode").trim(),
                tags = json.optString("tags").trim(),
                homepage = json.optString("homepage").trim(),
                favicon = json.optString("favicon").trim(),
                hls = json.optInt("hls") == 1 || url.contains(".m3u8", ignoreCase = true),
                votes = json.optInt("votes"),
            )
        }
    }
}
