package com.notandrej.wristradio

import android.app.Application
import com.notandrej.wristradio.data.Favourites
import com.notandrej.wristradio.data.RadioBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wiring that must exist no matter how the process was started - by the
 * launcher, or by the playback service waking up to resume a station.
 *
 * Note what is *not* here: the media controller. That binding only exists while
 * a screen is on (see [com.notandrej.wristradio.MainActivity]), so a process
 * started for the service never opens a second channel to it.
 */
class WristRadioApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Favourites.init(this)
        // Learn which directory mirrors are up once, at process start; never on
        // a timer.
        scope.launch { RadioBrowser.resolveMirrors() }
    }
}
