package page.cynosure.timer

import android.content.res.Configuration
import android.media.SoundPool
import android.os.Bundle
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DebateTimer() }
    }
}

@Composable
fun DebateTimer(viewModel: ViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    val isPreview = LocalInspectionMode.current
    val soundPool = remember { if (isPreview) null else SoundPool.Builder().setMaxStreams(1).build() }
    val context = LocalContext.current
    val oneBell = remember(soundPool) { soundPool?.load(context, R.raw.one_bell, 1) ?: -1 }
    val twoBells = remember(soundPool) { soundPool?.load(context, R.raw.two_bells, 1) ?: -1 }
    val threeBells = remember(soundPool) { soundPool?.load(context, R.raw.three_bells, 1) ?: -1 }
    // LaunchedEffect guarantees sounds only evaluate once per second when currentTime changes,
    // protecting against accidental plays caused by arbitrary recompositions.
    LaunchedEffect(uiState.currentTime) {
        val time = uiState.currentTime
        val max = uiState.maxTime
        val bells = uiState.bellSettings

        if (time > 0) {
            // Single bells
            if (((time == 60L) && (bells == 2)) || ((time == (max - 1) * 60L) && (bells != 0))) {
                soundPool?.play(oneBell, 3f, 3f, 0, 0, 1f)
            }
            // Double bell
            if ((time == max * 60L) && (bells != 0)) {
                soundPool?.play(twoBells, 3f, 3f, 0, 0, 1f)
            }
            // Triple bells
            if ((time > max * 60L) && (time % 15 == 0L) && (bells != 0)) {
                soundPool?.play(threeBells, 3f, 3f, 0, 0, 1f)
            }
        }
    }

    val displayTime = if (uiState.countUp) {
        if ((uiState.currentTime < (uiState.maxTime + 1) * 60) || (uiState.maxTime == 8)) {
            String.format(
                Locale.getDefault(),
                "%d:%02d",
                uiState.currentTime / 60,
                uiState.currentTime % 60
            )
        } else {
            "--"
        }
    } else {
        val invTime = 60 * uiState.maxTime - uiState.currentTime
        if (invTime > 0) {
            String.format(Locale.getDefault(), "%d:%02d", invTime / 60, invTime % 60)
        } else if (invTime > -60) {
            String.format(Locale.getDefault(), "%d", invTime)
        } else {
            "--"
        }
    }

    val containerColor = when {
        uiState.currentTime >= uiState.maxTime * 60 -> Color(236, 60, 76)
        uiState.currentTime >= (uiState.maxTime - 1) * 60 ||
                (uiState.currentTime < 60 && uiState.bellSettings == 2) -> Color(131, 191, 245, 255)

        else -> Color(255, 255, 255)
    }

    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> {
            Scaffold(
                containerColor = containerColor,
                modifier = Modifier.keepScreenOn()
            ) { innerPadding ->
                Text(
                    text = displayTime,
                    autoSize = TextAutoSize.StepBased(maxFontSize = 500.sp),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .wrapContentHeight(align = Alignment.CenterVertically)
                )
            }
            LocalActivity.current?.window?.insetsController?.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            LocalActivity.current?.window?.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
        }

        else -> {
            Scaffold(
                containerColor = containerColor,
                topBar = {
                    Row(
                        Modifier
                            .padding(10.dp, 2.dp)
                            .safeDrawingPadding()
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { soundPool?.play(oneBell, 3f, 3f, 1, 0, 1f) } )
                            .keepScreenOn(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { viewModel.cycleBellSettings() }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (uiState.bellSettings) {
                                    0 -> Icon(
                                        painter = painterResource(R.drawable.outline_notifications_off_24),
                                        contentDescription = "Bells off", tint = Color.Black
                                    )

                                    1, 2 -> Icon(
                                        painter = painterResource(R.drawable.outline_notifications_active_24),
                                        contentDescription = "Bells on", tint = Color.Black
                                    )
                                }
                                Text(
                                    text = when (uiState.bellSettings) {
                                        0 -> " Off"
                                        1 -> " At ${uiState.maxTime - 1}:00"
                                        else -> " At 1:00, ${uiState.maxTime - 1}:00"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.Black
                                )
                            }
                        }
                        TextButton(onClick = { viewModel.toggleCountDirection() }) {
                            Text(
                                text = if (uiState.countUp) "Count Up" else "Count Down",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black
                            )
                        }
                    }
                },
                bottomBar = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.isPaused) {
                                if (uiState.currentTime != 0L) "Resume" else "Start"
                            } else "Pause",
                            fontWeight = FontWeight.Bold, fontSize = 30.sp
                        )

                        Row(
                            Modifier
                                .padding(10.dp, 16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val times = listOf(3, 4, 5, 6, 7, 8)
                            times.forEach { time ->
                                TextButton(
                                    enabled = uiState.isPaused,
                                    onClick = { viewModel.setMaxTime(time) }
                                ) {
                                    Text(
                                        "${time}m",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick = { viewModel.togglePause() },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    )
            ) { innerPadding ->
                Text(
                    text = displayTime,
                    autoSize = TextAutoSize.StepBased(maxFontSize = 200.sp),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .wrapContentHeight(align = Alignment.CenterVertically)
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(255, 255, 255, 128))
                    .fillMaxWidth()
                    .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            )
            LocalActivity.current?.window?.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            LocalActivity.current?.window?.insetsController?.setSystemBarsAppearance(APPEARANCE_LIGHT_STATUS_BARS, APPEARANCE_LIGHT_STATUS_BARS)
        }
    }
}

@Composable
@Preview
@Preview(device = "spec:parent=pixel_5,orientation=landscape")
fun Preview() {
    DebateTimer()
}

// TODO:
// Prep time?
// Way to switch to fullscreen and back other than rotation
// Press and hold to reset time in fullscreen?
// Instructions
// Get rid of start/pause/resume?
// Setup global gitignore (in dotfiles) to not store .idea, .vscode, etc
