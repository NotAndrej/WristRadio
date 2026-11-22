package com.notandrej.wristradio.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.notandrej.wristradio.data.Station
import org.json.JSONObject

/**
 * Bundle key under which the fully serialised [Station] travels with the media
 * item, so the now-playing screen can recover it even after the process was
 * restarted by the playback service and only the media session survives.
 */
internal const val EXTRA_STATION = "com.notandrej.wristradio.STATION"

/**
 * Bundle key for the ICY track title, published as *session* extras rather than
 * stuffed into the media item: changing a media item mid-stream risks a
 * re-prepare, and a live stream must never be interrupted just because a
 * metadata block arrived.
 */
const val EXTRA_TRACK_TITLE = "com.notandrej.wristradio.TRACK_TITLE"

internal fun Station.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(uuid)
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(name)
            // Shown in the notification until the ICY title takes over.
            .setArtist(summary)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(Bundle().apply { putString(EXTRA_STATION, toJson().toString()) })
            .build()
    )
    .build()

internal fun MediaItem.toStation(): Station? {
    val encoded = mediaMetadata.extras?.getString(EXTRA_STATION)
    if (encoded != null) {
        runCatching { Station.from(JSONObject(encoded)) }.getOrNull()?.let { return it }
    }
    val uri = localConfiguration?.uri?.toString() ?: return null
    return Station(
        uuid = mediaId,
        name = mediaMetadata.title?.toString() ?: "Unknown station",
        url = uri,
    )
}
