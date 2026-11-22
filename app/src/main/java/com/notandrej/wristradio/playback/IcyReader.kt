package com.notandrej.wristradio.playback

import android.util.Log
import com.notandrej.wristradio.data.RadioBrowser
import okhttp3.Call
import okhttp3.Request
import java.io.InputStream

/**
 * Best-effort reader for the ICY (SHOUTcast/Icecast) "now playing" title.
 *
 * Most internet radio streams are a plain MP3/AAC byte stream with the track
 * title spliced into it: every `icy-metaint` audio bytes, one length byte
 * followed by that many multiples of 16 bytes of `StreamTitle='...';` metadata.
 * Media3 strips that metadata off the audio so the decoder never sees it, but
 * it does not hand it back to us, so this makes its own short-lived request
 * with `Icy-MetaData: 1` and parses the side channel.
 *
 * It is explicitly optional: it costs one extra connection, it is silently
 * abandoned if the server does not offer metadata or is already at its
 * listener limit, and the caller is told so via [onEnd] so it never retries.
 */
class IcyReader(
    private val url: String,
    private val onTitle: (String) -> Unit,
) {

    @Volatile
    private var cancelled = false

    @Volatile
    private var call: Call? = null

    /** Invoked exactly once, on the reader thread, when reading has stopped. */
    @Volatile
    var onEnd: (() -> Unit)? = null

    fun start(): IcyReader {
        Thread(::read, "wristradio-icy").apply {
            isDaemon = true
            start()
        }
        return this
    }

    fun cancel() {
        cancelled = true
        runCatching { call?.cancel() }
    }

    private fun read() {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", RadioBrowser.USER_AGENT)
                .header("Icy-MetaData", "1")
                .build()
            val current = RadioBrowser.streamHttp.newCall(request)
            call = current

            current.execute().use { response ->
                if (!response.isSuccessful) return
                val metaint = response.header("icy-metaint")?.trim()?.toIntOrNull() ?: return
                if (metaint <= 0 || metaint > MAX_METAINT) return
                val input = response.body?.byteStream() ?: return

                val audio = ByteArray(metaint)
                while (!cancelled) {
                    if (!readFully(input, audio, metaint)) break
                    val lengthByte = input.read()
                    if (lengthByte < 0) break
                    val length = lengthByte * 16
                    if (length == 0) continue
                    val block = ByteArray(length)
                    if (!readFully(input, block, length)) break

                    val text = String(block, Charsets.ISO_8859_1)
                    val title = STREAM_TITLE.find(text)?.groupValues?.get(1)?.trim()
                    if (!title.isNullOrEmpty()) onTitle(title)
                }
            }
        } catch (t: Throwable) {
            if (!cancelled) Log.d(TAG, "icy metadata unavailable: ${t.message}")
        } finally {
            call = null
            onEnd?.invoke()
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        var filled = 0
        while (filled < length) {
            val read = input.read(buffer, filled, length - filled)
            if (read < 0) return false
            filled += read
        }
        return true
    }

    private companion object {
        const val TAG = "WristRadio.Icy"
        const val MAX_METAINT = 64 * 1024
        val STREAM_TITLE = Regex("StreamTitle='(.*?)';", RegexOption.DOT_MATCHES_ALL)
    }
}
