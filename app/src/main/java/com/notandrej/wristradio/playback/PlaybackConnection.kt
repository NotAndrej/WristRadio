package com.notandrej.wristradio.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.notandrej.wristradio.data.RadioBrowser
import com.notandrej.wristradio.data.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything the UI needs to know about playback, in one observable value.
 *
 * @param connected  whether we have a live channel to the playback service yet
 * @param station    the station currently loaded, if any
 * @param playing    play intent is on and the player is past the idle state
 * @param buffering  the player is filling its buffer (normal for a live stream)
 * @param error      a human-readable failure, cleared on the next attempt
 * @param streamTitle the ICY "now playing" track, when the station offers it
 */
data class PlaybackState(
    val connected: Boolean = false,
    val station: Station? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val volume: Float = 1f,
    val error: String? = null,
    val streamTitle: String? = null,
)

/**
 * The app's single handle on the playback service.
 *
 * Rather than holding an ExoPlayer directly, the UI drives a [MediaController]
 * bound to [PlaybackService] through a [SessionToken]. That is the supported
 * Media3 arrangement, and it is what makes playback survive the activity going
 * away: only the service owns the player.
 */
object PlaybackConnection {

    private const val TAG = "WristRadio.Player"
    private const val EXTRA_STATION = "com.notandrej.wristradio.STATION"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    /** A station the user asked for before the controller finished connecting. */
    private var pending: Station? = null

    private var icy: IcyReader? = null
    private var icyUuid: String? = null
    private val icyFailed = ConcurrentHashMap.newKeySet<String>()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    fun init(context: Context) {
        val app = context.applicationContext
        if (future != null) return

        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val connecting = MediaController.Builder(app, token).buildAsync()
        future = connecting
        connecting.addListener(
            {
                val connected = runCatching { connecting.get() }.getOrNull()
                if (connected == null) {
                    Log.w(TAG, "could not connect to PlaybackService")
                    future = null
                    return@addListener
                }
                Log.i(TAG, "connected to playback service")
                controller = connected
                connected.addListener(listener)
                _state.value = _state.value.copy(connected = true)
                val queued = pending
                pending = null
                if (queued != null) play(queued) else sync()
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    // ---- transport ---------------------------------------------------------

    /** Loads a station and starts it. Safe to call before the session connects. */
    fun play(station: Station) {
        val player = controller
        _state.value = _state.value.copy(station = station, error = null, streamTitle = null)
        if (player == null) {
            pending = station
            return
        }
        Log.i(TAG, "play ${station.name} -> ${station.url}")
        player.setMediaItem(station.toMediaItem())
        player.prepare()
        player.play()
        scope.launch { runCatching { RadioBrowser.reportClick(station.uuid) } }
    }

    /** Play/pause for the front-panel button and the notification action. */
    fun toggle() {
        val player = controller ?: return
        if (player.playWhenReady) {
            player.pause()
            stopIcy()
        } else {
            player.play()
        }
        sync()
    }

    fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
        stopIcy()
        _state.value = _state.value.copy(
            station = null,
            playing = false,
            buffering = false,
            error = null,
            streamTitle = null,
        )
    }

    // ---- volume ------------------------------------------------------------

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        controller?.volume = clamped
        _state.value = _state.value.copy(volume = clamped)
    }

    fun adjustVolume(delta: Float) = setVolume(_state.value.volume + delta)

    // ---- state mirroring ---------------------------------------------------

    private fun sync() {
        val player = controller ?: return
        val failure = player.playerError
        val loaded = player.currentMediaItem?.let(::stationOf)
        val playing = failure == null &&
            player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE

        _state.value = _state.value.copy(
            connected = true,
            station = loaded ?: _state.value.station,
            playing = playing,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            volume = player.volume,
            error = failure?.let { it.cause?.message ?: it.message },
        )

        val station = loaded ?: _state.value.station
        if (playing && station != null) startIcyIfNeeded(station) else stopIcy()
    }

    private fun startIcyIfNeeded(station: Station) {
        if (icyUuid == station.uuid && icy != null) return
        stopIcy()
        if (!station.isIcyCapable || icyFailed.contains(station.uuid)) return
        icyUuid = station.uuid
        icy = IcyReader(station.url) { title ->
            if (title != _state.value.streamTitle) {
                _state.value = _state.value.copy(streamTitle = title)
            }
        }.also { reader ->
            reader.onEnd = { icyFailed.add(station.uuid) }
        }.start()
    }

    private fun stopIcy() {
        icy?.cancel()
        icy = null
        icyUuid = null
    }

    // ---- MediaItem <-> Station --------------------------------------------

    private fun Station.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uuid)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(summary)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(Bundle().apply { putString(EXTRA_STATION, toJson().toString()) })
                .build()
        )
        .build()

    /**
     * Recovers the station from a media item. Walking the extras means the
     * now-playing screen still works when the process was restarted by the
     * service and only the media session's memory of the item survives.
     */
    private fun stationOf(item: MediaItem): Station? {
        val encoded = item.mediaMetadata.extras?.getString(EXTRA_STATION)
        if (encoded != null) {
            runCatching {
                Station.from(JSONObject(encoded))
            }.getOrNull()?.let { return it }
        }
        val uri = item.localConfiguration?.uri?.toString() ?: return null
        return Station(
            uuid = item.mediaId,
            name = item.mediaMetadata.title?.toString() ?: "Unknown station",
            url = uri,
        )
    }
}
