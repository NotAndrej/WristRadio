package com.notandrej.wristradio.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
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

/**
 * Everything the UI needs to know about playback, in one observable value.
 *
 * @param connected  whether we have a live channel to the playback service yet
 * @param station    the station currently loaded, if any
 * @param playing    play intent is on and the player is past the idle state
 * @param buffering  the player is filling its buffer (normal for a live stream)
 * @param volume     0..1, as held by the service so it survives UI restarts
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
 * The app's handle on the playback service, used only while a screen is on.
 *
 * The UI drives a [MediaController] bound to [PlaybackService]. That binding is
 * deliberately *not* permanent: [connect] is called from `onStart` and
 * [disconnect] from `onStop`, so when the app is not on screen nothing here
 * holds the service up. Audio that is genuinely playing keeps the service in
 * the foreground on its own; a paused or idle player is left with no client,
 * and Media3 then stops the service rather than leaving it resident on a 512
 * mAh watch.
 *
 * The ICY track title is *not* read here any more. It is playback state, so the
 * service owns that reader and publishes the title as session extras; this class
 * only mirrors those extras into [PlaybackState].
 */
object PlaybackConnection {

    private const val TAG = "WristRadio.Player"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    /** A station the user asked for before the controller finished connecting. */
    private var pending: Station? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            applyTrackTitle(extras.getString(EXTRA_TRACK_TITLE))
        }

        override fun onDisconnected(controller: MediaController) {
            Log.w(TAG, "playback service disconnected")
            this@PlaybackConnection.controller = null
            // Allow a later connect() to try again rather than short-circuiting
            // on a stale binding.
            future = null
            _state.value = _state.value.copy(connected = false)
        }
    }

    // ---- lifecycle ---------------------------------------------------------

    /** Binds to the service. Idempotent; call from `onStart`. */
    fun connect(context: Context) {
        if (future != null) return
        val app = context.applicationContext

        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val connecting = MediaController.Builder(app, token)
            .setListener(controllerListener)
            .buildAsync()
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
                _state.value = _state.value.copy(
                    connected = true,
                    volume = connected.volume,
                    streamTitle = connected.sessionExtras?.getString(EXTRA_TRACK_TITLE),
                )
                val queued = pending
                pending = null
                if (queued != null) play(queued) else sync()
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    /**
     * Drops our binding. Playback that is still running is unaffected - the
     * service is in the foreground for exactly as long as audio is playing.
     * Call from `onStop`.
     */
    fun disconnect() {
        val binding = future
        future = null
        controller?.removeListener(listener)
        controller = null
        _state.value = _state.value.copy(connected = false)
        // Release the controller so the service is no longer bound by us. If
        // nothing is playing, Media3 stops the service here.
        binding?.let { MediaController.releaseFuture(it) }
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

    /**
     * Play/pause. Pausing stops the stream rather than holding a buffer: a live
     * stream cannot usefully be resumed mid-way, and an idle player is what lets
     * Media3 take the notification and the foreground service down.
     */
    fun toggle() {
        val player = controller ?: return
        if (player.playWhenReady) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
        sync()
    }

    /**
     * Stops playback and unloads the station. The service drops its own wake
     * lock and WiFi lock on stop, and (with no client bound and an idle player)
     * shuts itself down.
     */
    fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
        _state.value = PlaybackState(connected = _state.value.connected, volume = _state.value.volume)
    }

    // ---- volume ------------------------------------------------------------

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        controller?.volume = clamped
        _state.value = _state.value.copy(volume = clamped)
    }

    /** Nudge the volume relative to the player's own current value. */
    fun adjustVolume(delta: Float) {
        val player = controller ?: return
        setVolume(player.volume + delta)
    }

    // ---- state mirroring ---------------------------------------------------

    private fun sync() {
        val player = controller ?: return
        val failure = player.playerError
        val loaded = player.currentMediaItem?.toStation()
        val playing = failure == null &&
            player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE

        _state.value = _state.value.copy(
            connected = true,
            station = loaded,
            playing = playing,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            volume = player.volume,
            error = failure?.let { it.cause?.message ?: it.message },
        )
    }

    private fun applyTrackTitle(title: String?) {
        val current = _state.value.streamTitle
        if (title == current) return
        _state.value = _state.value.copy(streamTitle = title?.takeIf { it.isNotBlank() })
    }
}
