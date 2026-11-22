package com.notandrej.wristradio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.notandrej.wristradio.playback.PlaybackConnection
import com.notandrej.wristradio.ui.RadioApp
import com.notandrej.wristradio.ui.theme.WristRadioTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WristRadioTheme {
                AskForNotificationPermission()
                BindPlaybackConnection()
                RadioApp()
            }
        }
    }
}

/**
 * The media controller is bound for exactly as long as this activity is
 * on-screen. Dropping it in `onStop` is what lets a paused radio release the
 * service (and the process) instead of holding a 512 mAh watch awake; audio that
 * is still playing keeps the service in the foreground by itself.
 */
@Composable
private fun BindPlaybackConnection() {
    val context = LocalContext.current
    DisposableEffect(context) {
        PlaybackConnection.connect(context)
        onDispose { PlaybackConnection.disconnect() }
    }
}

/**
 * The playback notification is the only way to control the radio once the
 * screen is off, so ask for it. Playback works either way - a denied
 * notification simply means no notification.
 */
@Composable
private fun AskForNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* refused is fine; the service still runs */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
