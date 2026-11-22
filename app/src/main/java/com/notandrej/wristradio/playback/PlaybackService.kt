package com.notandrej.wristradio.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.notandrej.wristradio.MainActivity
import com.notandrej.wristradio.data.Station
import java.util.concurrent.ConcurrentHashMap

/**
 * The foreground playback service.
 *
 * Media3 owns the foreground-service lifecycle: it starts the service in the
 * foreground and posts the media notification while audio is playing.
 *
 * Battery is the reason this class exists in the shape it does:
 *
 * - `SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER` means "playing" is a hard
 *   requirement for both the notification and the foreground service. Pause the
 *   radio and the notification disappears; with no controller bound (the app is
 *   off-screen) Media3 stops the service outright, so nothing of ours is left
 *   scheduled, woken or resident.
 * - ExoPlayer's wake mode is only ever held *while playing*; ExoPlayer releases
 *   its partial wake lock and WiFi lock itself on pause and stop. A 512 mAh
 *   watch cannot afford a wake lock that outlives the audio.
 * - The ICY metadata reader is owned here rather than by the UI, because it is
 *   playback state, not screen state: it must start and stop with the player
 *   even when the app is not on screen, and there must be no thread left
 *   reading a socket once the player has stopped.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var player: ExoPlayer? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var reader: IcyReader? = null
    private var readerUuid: String? = null
    private val readerFailed = ConcurrentHashMap.newKeySet<String>()
    private var publishedTitle: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncMetadataReader()

            // A paused ExoPlayer is *not* idle to Media3, so the media
            // notification and the foreground service would sit there for as
            // long as the watch is on. There is nothing to resume on a live
            // stream anyway - pausing means stopping: the player goes idle and
            // Media3 drops the notification and the foreground state with it.
            if (!player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
                mainHandler.post { if (!player.playWhenReady) player.stop() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Must be set before the session exists. Nothing about this app should be
        // alive, notified or in the foreground while the radio is not playing.
        // (Qualified because Kotlin does not inherit Java statics.)
        setShowNotificationForIdlePlayer(MediaSessionService.SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Held only for the duration of playback, to keep a live stream fed
            // with the screen off. ExoPlayer drops it on pause and on stop.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.addListener(listener) }

        player = exoPlayer
        session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Nothing is playing, so let the service (and the process) go. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        stopMetadataReader()
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        super.onDestroy()
    }

    // ---- ICY track titles --------------------------------------------------

    /**
     * Starts the metadata reader exactly when there is a station playing that
     * can carry ICY metadata, and stops it the moment playback does anything
     * else. Called from the player's own events, so it cannot drift out of sync
     * with playback - including when the app is nowhere near the screen.
     */
    private fun syncMetadataReader() {
        val player = player ?: return
        val station = player.currentMediaItem?.toStation()
        val playing = player.playWhenReady && player.playbackState != Player.STATE_IDLE

        if (!playing || station == null || !station.isIcyCapable ||
            readerFailed.contains(station.uuid)
        ) {
            stopMetadataReader()
            clearTrackTitle()
            return
        }

        if (readerUuid == station.uuid && reader != null) return

        stopMetadataReader()
        readerUuid = station.uuid
        reader = IcyReader(station.url) { title -> publishTrackTitle(station, title) }
            .also { it.onEnd = { readerFailed.add(station.uuid) } }
            .start()
    }

    private fun stopMetadataReader() {
        reader?.cancel()
        reader = null
        readerUuid = null
    }

    private fun publishTrackTitle(station: Station, title: String) {
        if (title == publishedTitle) return
        val player = this.player
        if (player == null || player.currentMediaItem?.mediaId != station.uuid) return
        publishedTitle = title
        mainHandler.post {
            runCatching {
                session?.setSessionExtras(Bundle().apply { putString(EXTRA_TRACK_TITLE, title) })
            }
        }
    }

    private fun clearTrackTitle() {
        if (publishedTitle == null) return
        publishedTitle = null
        mainHandler.post {
            runCatching { session?.setSessionExtras(Bundle.EMPTY) }
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
