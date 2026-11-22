package com.notandrej.wristradio

import android.app.Application
import com.notandrej.wristradio.data.Favourites
import com.notandrej.wristradio.data.RadioBrowser
import com.notandrej.wristradio.playback.PlaybackConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wiring that must exist no matter how the process was started - by the
 * launcher, or by the playback service waking up to resume a station.
 */
class WristRadioApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Favourites.init(this)
        PlaybackConnection.init(this)
        // Learn which directory mirrors are up before the user asks for anything.
        scope.launch { RadioBrowser.resolveMirrors() }
    }
}
