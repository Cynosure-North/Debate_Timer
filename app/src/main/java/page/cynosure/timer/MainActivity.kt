package page.cynosure.timer

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.database.ContentObserver
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.layout
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
    val context = LocalContext.current
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current
    val isPreview = LocalInspectionMode.current

    LaunchedEffect(viewModel, isPreview) {
        if (isPreview) {
            viewModel.initBells()
        } else {
            viewModel.initBells(context)
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

    val (textColor, statusBarBackgroundColor, containerColor) = if (!isSystemInDarkTheme()) {
        activity?.window?.insetsController?.setSystemBarsAppearance(APPEARANCE_LIGHT_STATUS_BARS, APPEARANCE_LIGHT_STATUS_BARS)

        Triple(
            Color.Black,
            Color.White.copy(alpha = 0.5f),
            when {
                uiState.currentTime >= uiState.maxTime * 60 -> Color(236, 60, 76)
                uiState.currentTime >= (uiState.maxTime - 1) * 60 ||
                        (uiState.currentTime < 60 && uiState.bellSettings == 2) -> Color(131, 191, 245, 255)
                else -> Color.White
            }
        )
    } else {
        activity?.window?.insetsController?.setSystemBarsAppearance(APPEARANCE_LIGHT_STATUS_BARS, 0)

        Triple(
            Color.White,
            Color.Black.copy(alpha = 0.5f),
            when {
                uiState.currentTime >= uiState.maxTime * 60 -> Color(147, 34, 47, 255)
                uiState.currentTime >= (uiState.maxTime - 1) * 60 ||
                        (uiState.currentTime < 60 && uiState.bellSettings == 2) -> Color(
                    54,
                    95,
                    129,
                    255
                )
                else -> Color.Black
            }
        )
    }

   //       [Locked landscape]  <- tap minimise/maximise button ->   [unlocked portrait]   <------------------------------ rotate ----
   //      (physical device may be portrait)                                                                                        |
   //               |                                                         ^                                                     |
   //               |                                                         |                                                     |
   //               |                                                   rotate device                                               |
   //               |                                                         |                                                     |
   //               |                                                         v                                                     |
   //               |
   //               ------ rotate --------------------------------> [unlocked landscape] <- tap minimise/maximise button -> [locked portrait] (physical device may be landscape)
   //
   //
   //   Tapping always toggles the rotation lock
   //   Rotating the device to match the screen orientation unlocks rotation

    val currentUiState by rememberUpdatedState(uiState) // always fresh, no stale capture
    val rotationLocked by rememberRotationLockState()

    if (!isPreview && !rotationLocked) {
        val listener = remember{
            object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_UI) {
                private var committedZone = -1      // the zone we've actually acted on
                private var candidateZone = -1      // the zone we're currently seeing
                private var candidateSince = 0L     // when we first saw this candidate
                private val debounceMs = 350L       // how long a new zone must persist
                private val hysteresis = 8          // degrees of "buffer" around boundaries

                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return

                    val rawZone = when (orientation) {
                        !in 46..<315 -> 0
                        in 46..134 -> 1
                        in 135..224 -> 2
                        else -> 3
                    }

                    val zone = when {
                        committedZone == -1 -> rawZone            // nothing committed yet — trust it
                        rawZone == committedZone -> rawZone        // already agrees, no ambiguity
                        isNearBoundary(orientation, hysteresis) -> committedZone // near an edge, hold steady
                        else -> rawZone
                    }

                    val now = SystemClock.elapsedRealtime()

                    if (zone != candidateZone) {
                        candidateZone = zone
                        candidateSince = now
                        return
                    }

                    if (zone == committedZone) return
                    if (now - candidateSince < debounceMs) return

                    committedZone = zone
                    applyZone(zone)
                }

                private fun isNearBoundary(orientation: Int, buffer: Int): Boolean {
                    val boundaries = listOf(46, 135, 225, 315) // real zone edges only, no 0
                    return boundaries.any { b ->
                        val diff = kotlin.math.abs(orientation - b) % 360
                        diff <= buffer || diff >= 360 - buffer
                    }
                }

                private fun applyZone(zone: Int) {
                    val state = currentUiState

                    if (zone == 1 || zone == 3) {
                        if (!state.rotationLocked) {
                            if (state.maximised == 0) {
                                viewModel.setMaximise(if (zone == 1) 1 else 2)
                            }
                        } else if (state.maximised != 0) {
                            viewModel.unlockRotation()
                        }
                    } else {
                        if (!state.rotationLocked) {
                            if (state.maximised != 0) {
                                viewModel.setMaximise(0)
                            }
                        } else if (state.maximised == 0) {
                            viewModel.unlockRotation()
                        }
                    }
                }
            }
        }

        if (listener.canDetectOrientation()) {
            listener.enable()
        }
    }
    @SuppressLint("SourceLockedOrientationActivity")
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    if (uiState.maximised != 0 || (isPreview && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE )) {
        activity?.window?.insetsController?.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        activity?.window?.insetsController?.hide(android.view.WindowInsets.Type.systemBars())

        Scaffold(
            Modifier.keepScreenOn(),
            containerColor = containerColor,
            floatingActionButton = {
                IconButton(
                    onClick = { viewModel.setMaximise(0); if (rotationLocked) viewModel.toggleRotationLock() } ) {
                    Icon(
                        painter = painterResource( R.drawable.outline_close_fullscreen_24),
                        contentDescription = "Exit Fullscreen", tint = textColor,
                    )
                }
            }
        ) { innerPadding ->
            Text(
                text = displayTime,
                autoSize = TextAutoSize.StepBased(maxFontSize = 500.sp),
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displayLarge,
                color = textColor,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .wrapContentHeight(align = Alignment.CenterVertically)
                    .combinedClickable(
                        onClick = { viewModel.togglePause() },
                        onLongClick = {if (uiState.isPaused) viewModel.resetTimer() },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null )
                    .rotateVertically(uiState.maximised == 1)
            )
        }
    } else {
        Scaffold(
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = { viewModel.togglePause() },
                    onLongClick = {if (uiState.isPaused) viewModel.resetTimer() },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null )
                .keepScreenOn(),
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
                            onLongClick = { viewModel.playManualBell() } ),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton( onClick = { viewModel.cycleBellSettings() }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (uiState.bellSettings) {
                                0 -> Icon(
                                    painter = painterResource(R.drawable.outline_notifications_off_24),
                                    contentDescription = "Bells off", tint = textColor
                                )

                                1, 2 -> Icon(
                                    painter = painterResource(R.drawable.outline_notifications_active_24),
                                    contentDescription = "Bells on", tint = textColor
                                )
                            }
                            Text(
                                text = when (uiState.bellSettings) {
                                    0 -> " Off"
                                    1 -> " At ${uiState.maxTime - 1}:00"
                                    else -> " At 1:00, ${uiState.maxTime - 1}:00"
                                },
                                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor
                            )
                        }
                    }
                    TextButton( onClick = { viewModel.toggleCountDirection() }) {
                        Text(
                            text = if (uiState.countUp) "Count Up" else "Count Down",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor
                        )
                    }
                }
            },
            bottomBar = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.isPaused) {
                            if (uiState.currentTime != 0L) "Resume" else "Start"
                        } else "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = textColor
                    )
                    Row(
                        Modifier.padding(10.dp, 16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val times = listOf(3, 4, 5, 6, 7, 8)
                        times.forEach { time ->
                            Text(
                                "${time}m",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = textColor,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(
                                        enabled = uiState.isPaused,
                                        onClick = { viewModel.setMaxTime(time) },
                                        indication = ripple(),
                                        interactionSource = remember { MutableInteractionSource() } )
                                    .padding(14.dp, 8.dp)
                            )
                        }
                        IconButton( onClick = { viewModel.setMaximise(2); if (rotationLocked) viewModel.toggleRotationLock() } ) {
                            Icon(
                                painter = painterResource( R.drawable.outline_open_fullscreen_24),
                                contentDescription = "Fullscreen", tint = textColor,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Text(
                text = displayTime,
                autoSize = TextAutoSize.StepBased(maxFontSize = 200.sp),
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displayLarge,
                color = textColor,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .wrapContentHeight(align = Alignment.CenterVertically)
            )
        }

        Box(
            Modifier
                .background(statusBarBackgroundColor)
                .fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
        )
        activity?.window?.insetsController?.show(android.view.WindowInsets.Type.systemBars())

    }
}

fun Modifier.rotateVertically(clockwise: Boolean = true): Modifier = this.layout { measurable, constraints ->
    // Swap constraints to measure with relaxed bounds
    val rotatedConstraints = constraints.copy(
        minWidth = 0,
        minHeight = 0,
        maxWidth = constraints.maxHeight,
        maxHeight = constraints.maxWidth
    )
    val placeable = measurable.measure(rotatedConstraints)

    // Report inverted dimensions to the parent
    layout(placeable.height, placeable.width) {
        placeable.placeRelative(
            x = -(placeable.width - placeable.height) / 2,
            y = -(placeable.height - placeable.width) / 2
        )
    }
}.rotate(if (clockwise) 270f else 90f) // Or your target rotation angle

fun isRotationLocked(context: Context): Boolean {
    return Settings.System.getInt(
        context.contentResolver,
        Settings.System.ACCELEROMETER_ROTATION,
        0 // default value if setting not found
    ) == 0
}

@Composable
fun rememberRotationLockState(): State<Boolean> {
    val context = LocalContext.current
    val rotationLocked = remember {
        mutableStateOf(isRotationLocked(context))
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                rotationLocked.value = isRotationLocked(context)
            }
        }

        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            observer
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    return rotationLocked
}


@Composable
@Preview(group = "Light", uiMode = UI_MODE_NIGHT_NO)
@Preview(group = "Light", uiMode = UI_MODE_NIGHT_NO, device = "spec:parent=pixel_5,orientation=landscape")
@Preview(group = "Dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(group = "Dark", uiMode = UI_MODE_NIGHT_YES, device = "spec:parent=pixel_5,orientation=landscape")
fun Preview() {
    DebateTimer()
}
