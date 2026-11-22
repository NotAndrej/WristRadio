package com.notandrej.wristradio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import com.notandrej.wristradio.data.Favourites
import com.notandrej.wristradio.playback.PlaybackConnection
import com.notandrej.wristradio.playback.PlaybackState
import kotlin.math.roundToInt

/**
 * The now-playing face: what is on, whether it is actually playing, transport,
 * volume and the favourite star.
 *
 * The content is longer than one round screen, so a finger scrolls it, and the
 * two volume chips are the volume control.
 *
 * A note for whoever comes next: this screen used to hand the whole screen to
 * `onRotaryScrollEvent` so the crown worked as a volume dial. On the target
 * watch that turned out to steal a *touch* scroll too - once the content was
 * scrolled to its end, a further swipe was delivered as a rotary event and
 * quietly dropped the volume by 70%. A volume control that changes itself while
 * you scroll is worse than no crown support, so explicit chips win.
 */
@Composable
fun PlayerScreen(state: PlaybackState, onBack: () -> Unit) {
    BackHandler { onBack() }

    val favourites by Favourites.items.collectAsState()
    val scroll = rememberScrollState()

    val station = state.station
    val isFavourite = station != null && favourites.any { it.uuid == station.uuid }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 14.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(
                onClick = onBack,
                label = { Text("Stations") },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = station?.name ?: "Nothing playing",
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            if (station != null) {
                Text(
                    text = listOfNotNull(
                        station.summary.ifEmpty { null },
                        station.country.ifEmpty { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            StatusLine(state)

            state.streamTitle?.let { title ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colors.secondary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.secondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ToggleChip(
                checked = state.playing,
                onCheckedChange = { PlaybackConnection.toggle() },
                enabled = station != null,
                label = { Text(if (state.playing) "Playing" else "Paused") },
                secondaryLabel = { Text(if (state.playing) "Tap to pause" else "Tap to play") },
                toggleControl = {
                    Icon(
                        imageVector = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip(
                    onClick = { PlaybackConnection.adjustVolume(-0.1f) },
                    label = { Text("Vol \u2212") },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.VolumeDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f),
                )
                Chip(
                    onClick = { PlaybackConnection.adjustVolume(0.1f) },
                    label = { Text("Vol +") },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f),
                )
            }

            VolumeBar(state.volume)

            ToggleChip(
                checked = isFavourite,
                onCheckedChange = { station?.let(Favourites::toggle) },
                enabled = station != null,
                label = { Text("Favourite") },
                secondaryLabel = {
                    Text(if (isFavourite) "Saved on this watch" else "Save on this watch")
                },
                toggleControl = {
                    Icon(
                        imageVector = if (isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Chip(
                onClick = { PlaybackConnection.stop() },
                enabled = station != null,
                label = { Text("Stop") },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun StatusLine(state: PlaybackState) {
    val (text, colour) = when {
        !state.connected -> "Connecting to the player\u2026" to MaterialTheme.colors.onSurfaceVariant
        state.error != null -> "Error: ${state.error}" to MaterialTheme.colors.error
        state.station == null -> "Pick a station to start listening" to MaterialTheme.colors.onSurfaceVariant
        state.buffering -> "Buffering\u2026" to MaterialTheme.colors.secondary
        state.playing -> "Playing" to MaterialTheme.colors.primary
        else -> "Paused" to MaterialTheme.colors.onSurfaceVariant
    }

    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = colour,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VolumeBar(volume: Float) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colors.surface)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(volume.coerceIn(0.002f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colors.primary)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Volume ${(volume * 100).roundToInt()}%",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
