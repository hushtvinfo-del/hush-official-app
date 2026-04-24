package com.hushtv.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.hushtv.tv.ui.screens.clickableWithEnter
import com.hushtv.tv.ui.theme.Cyan
import com.hushtv.tv.ui.theme.TextSecondary

private enum class Pane { MAIN, AUDIO, SUBTITLE, ASPECT, SPEED, SLEEP }

/** Full-screen modal with player options (audio, subtitles, aspect, speed, sleep, info). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerOptionsMenu(
    player: ExoPlayer,
    aspectMode: AspectMode,
    onAspectChange: (AspectMode) -> Unit,
    sleepMinutesLeft: Int?,
    onSleepChange: (Int?) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    onShowInfo: () -> Unit,
    onDismiss: () -> Unit,
    initialPane: String? = null,
) {
    var pane by remember {
        mutableStateOf(
            when (initialPane) {
                "audio" -> Pane.AUDIO
                "subtitle" -> Pane.SUBTITLE
                "speed" -> Pane.SPEED
                "aspect" -> Pane.ASPECT
                "sleep" -> Pane.SLEEP
                else -> Pane.MAIN
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // BACK key and tap-outside both route through onDismissRequest
            // so we don't need an onClick on the backdrop — which was
            // firing on EVERY OK press inside the dialog and closing the
            // menu before any track/speed/subtitle selection could take
            // effect. (1.20.4 bug.)
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = Color(0xFF0B111D),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .border(
                        1.dp, Color(0x3306B6D4),
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
            ) {
                Column(Modifier.padding(28.dp)) {
                    when (pane) {
                        Pane.MAIN -> MainPane(
                            aspectMode = aspectMode,
                            sleepMinutesLeft = sleepMinutesLeft,
                            playbackSpeed = playbackSpeed,
                            onAudio = { pane = Pane.AUDIO },
                            onSubtitle = { pane = Pane.SUBTITLE },
                            onAspect = { pane = Pane.ASPECT },
                            onSpeed = { pane = Pane.SPEED },
                            onSleep = { pane = Pane.SLEEP },
                            onInfo = { onDismiss(); onShowInfo() }
                        )
                        Pane.AUDIO -> TrackPicker(
                            title = "Audio track",
                            player = player,
                            trackType = C.TRACK_TYPE_AUDIO,
                            onBack = { pane = Pane.MAIN }
                        )
                        Pane.SUBTITLE -> TrackPicker(
                            title = "Subtitles",
                            player = player,
                            trackType = C.TRACK_TYPE_TEXT,
                            onBack = { pane = Pane.MAIN },
                            allowOff = true
                        )
                        Pane.ASPECT -> AspectPicker(aspectMode, onAspectChange, onBack = { pane = Pane.MAIN })
                        Pane.SPEED -> SpeedPicker(playbackSpeed, onPlaybackSpeedChange, onBack = { pane = Pane.MAIN })
                        Pane.SLEEP -> SleepPicker(sleepMinutesLeft, onSleepChange, onBack = { pane = Pane.MAIN })
                    }
                }
            }
        }
    }
}

@Composable
private fun MainPane(
    aspectMode: AspectMode,
    sleepMinutesLeft: Int?,
    playbackSpeed: Float,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onAspect: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onInfo: () -> Unit
) {
    Text(
        "PLAYER OPTIONS",
        color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 3.sp
    )
    Spacer(Modifier.height(16.dp))
    OptionRow(Icons.Default.Audiotrack, "Audio track", "Choose language", onAudio)
    OptionRow(Icons.Default.ClosedCaption, "Subtitles", "Enable / disable / pick", onSubtitle)
    OptionRow(Icons.Default.Speed, "Playback speed", "${trimTrailingZero(playbackSpeed)}×", onSpeed)
    OptionRow(Icons.Default.AspectRatio, "Aspect ratio", aspectMode.label, onAspect)
    OptionRow(
        Icons.Default.Bedtime, "Sleep timer",
        sleepMinutesLeft?.let { "$it min remaining" } ?: "Off",
        onSleep
    )
    OptionRow(Icons.Default.Info, "Program info", "Show what's on now", onInfo)
}

private fun trimTrailingZero(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString().trimEnd('0').trimEnd('.')

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x08FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Cyan else Color(0x14FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(icon, null, tint = if (focused) Cyan else Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Text("▸", color = TextSecondary, fontSize = 18.sp)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun TrackPicker(
    title: String,
    player: ExoPlayer,
    trackType: @C.TrackType Int,
    onBack: () -> Unit,
    allowOff: Boolean = false
) {
    val tracksState = remember { mutableStateOf<Tracks>(player.currentTracks) }
    val groups = tracksState.value.groups.filter { it.type == trackType }

    Row(verticalAlignment = Alignment.CenterVertically) {
        BackChip(onBack)
        Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))

    if (groups.isEmpty()) {
        Text("No ${title.lowercase()} available in this stream", color = TextSecondary, fontSize = 14.sp)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (allowOff) {
                item {
                    TrackChoice(
                        label = "Off",
                        selected = groups.none { grp -> (0 until grp.length).any { grp.isTrackSelected(it) } },
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(trackType, true)
                                .clearOverridesOfType(trackType)
                                .build()
                            tracksState.value = player.currentTracks
                        }
                    )
                }
            }
            groups.forEachIndexed { gi, grp ->
                for (ti in 0 until grp.length) {
                    val fmt = grp.getTrackFormat(ti)
                    val label = buildString {
                        append(fmt.language?.uppercase() ?: "Unknown")
                        fmt.label?.let { if (it.isNotBlank()) append("  •  $it") }
                        fmt.codecs?.let { append("  ($it)") }
                    }
                    val selected = grp.isTrackSelected(ti)
                    item(key = "$gi-$ti") {
                        TrackChoice(
                            label = label,
                            selected = selected,
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(trackType, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(grp.mediaTrackGroup, ti)
                                    )
                                    .build()
                                tracksState.value = player.currentTracks
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (focused) Color(0x3306B6D4) else Color(0x08FFFFFF),
                RoundedCornerShape(10.dp)
            )
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Cyan else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, null, tint = Cyan, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AspectPicker(
    current: AspectMode, onChange: (AspectMode) -> Unit, onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackChip(onBack)
        Spacer(Modifier.width(12.dp))
        Text("Aspect ratio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))
    AspectMode.values().forEach { mode ->
        TrackChoice(
            label = mode.label,
            selected = mode == current,
            onClick = { onChange(mode) }
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SpeedPicker(
    current: Float, onChange: (Float) -> Unit, onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackChip(onBack)
        Spacer(Modifier.width(12.dp))
        Text("Playback speed", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    speeds.forEach { sp ->
        TrackChoice(
            label = "${trimTrailingZero(sp)}×",
            selected = kotlin.math.abs(current - sp) < 0.01f,
            onClick = { onChange(sp) }
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SleepPicker(
    currentMinutesLeft: Int?, onChange: (Int?) -> Unit, onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackChip(onBack)
        Spacer(Modifier.width(12.dp))
        Text("Sleep timer", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(14.dp))
    val options = listOf(null, 15, 30, 60, 90, 120)
    options.forEach { opt ->
        TrackChoice(
            label = opt?.let { "$it minutes" } ?: "Off",
            selected = currentMinutesLeft == opt,
            onClick = { onChange(opt) }
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun BackChip(onBack: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        color = Color(0x1AFFFFFF),
        shape = CircleShape,
        modifier = Modifier
            .size(36.dp)
            .border(if (focused) 2.dp else 0.dp, if (focused) Cyan else Color.Transparent, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickableWithEnter(onBack)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("◀", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
