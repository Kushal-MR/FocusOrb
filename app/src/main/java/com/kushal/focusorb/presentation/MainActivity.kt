/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.kushal.focusorb.presentation

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.*
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.tooling.preview.devices.WearDevices
import com.kushal.focusorb.presentation.theme.FocusOrbTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity(), AmbientLifecycleObserver.AmbientLifecycleCallback {

    var isAmbient by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        
        // Register Ambient Observer
        // By registering this observer, the OS knows to put our app into Ambient Mode
        // instead of closing it when the screen times out.
        AmbientLifecycleObserver(this, this).also { observer ->
            lifecycle.addObserver(observer)
        }

        setContent {
            FocusOrbTheme {
                FocusOrbApp(isAmbient = isAmbient)
            }
        }
    }

    override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
        isAmbient = true
    }

    override fun onExitAmbient() {
        isAmbient = false
    }

    override fun onUpdateAmbient() {
        // System requests a screen refresh in ambient mode
    }
}

@Composable
fun FocusOrbApp(isAmbient: Boolean = false, viewModel: FocusViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // ── Bluetooth distraction listener ───────────────────────────────
    // Registers on the Wearable MessageClient when this composable enters
    // composition; unregisters when it leaves. The callback routes directly
    // into the ViewModel's damage pipeline.
    androidx.compose.runtime.DisposableEffect(context) {
        val receiver = DistractionMessageReceiver(
            context = context,
            onDistractionCaught = { viewModel.takeDamage() }
        )
        receiver.register()
        onDispose { receiver.unregister() }
    }

    // ── True Ambient Persistence (Ongoing Activity) ─────────────────────────
    // Instead of forcing the screen to stay bright forever, we register an
    // Ongoing Activity. This tells Wear OS that when the user drops their
    // wrist, it should transition into our custom Ambient Mode instead of
    // kicking us back to the watch face.
    val activity = context as? android.app.Activity
    
    // Permission request launcher for Android 13+ Notifications
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            android.util.Log.w("FocusOrb", "Notification permission denied. Ambient mode persistence may fail.")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.sessionState) {
        if (uiState.sessionState == SessionState.RUNNING || uiState.sessionState == SessionState.PAUSED) {
            OngoingActivityManager.startOngoingActivity(context)
        } else {
            OngoingActivityManager.stopOngoingActivity(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.timerEvent.collect { event ->
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            when (event) {
                TimerEvent.PULSE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
                    }
                }
                TimerEvent.COMPLETED -> {
                    // ── Wake up the screen if in Ambient mode ──────────────────
                    activity?.runOnUiThread {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            activity.setTurnScreenOn(true)
                        } else {
                            @Suppress("DEPRECATION")
                            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                        }
                    }

                    // ── Haptics locked to the supernova timeline ───────────
                    // The visual runs 4000ms and only starts after a 400ms
                    // screen-wake delay, so the waveform opens with 400ms of
                    // silence and every beat below is quoted in real time from
                    // the moment the session completes:
                    //
                    //    400ms  collapse begins  → rising anticipation rumble
                    //   1200ms  detonation (t=0.2) → full-amplitude burst
                    //   1700ms  star genesis      → soft shimmer taps
                    //   3800ms  arrival (t≈1.0)   → crisp lock-in tap
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val timings = longArrayOf(
                            400,                                          // screen wake
                            50, 50, 50, 50, 50, 50, 50, 50,               // anticipation
                            50, 50, 50, 50, 50, 50, 50, 50,               // (800ms total)
                            220,                                          // DETONATION
                            280,                                          // silence
                            70, 230, 70, 230, 70, 230,                    // genesis shimmer
                            1200,                                         // the voyage
                            90                                            // lock-in
                        )
                        val amplitudes = intArrayOf(
                            0,
                            20, 0, 35, 0, 50, 0, 70, 0,                   // building
                            95, 0, 125, 0, 160, 0, 200, 0,                // building
                            255,                                          // DETONATION
                            0,
                            90, 0, 65, 0, 45, 0,                          // shimmer
                            0,
                            180                                           // lock-in
                        )
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        val pattern = longArrayOf(
                            400, 50, 50, 50, 50, 50, 50, 50, 50,
                            50, 50, 50, 50, 50, 50, 50, 50,
                            220, 280, 70, 230, 70, 230, 70, 230, 1200, 90
                        )
                        vibrator.vibrate(pattern, -1)
                    }
                }
                TimerEvent.SHATTER -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val pattern = longArrayOf(0, 100, 100, 100)
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        val pattern = longArrayOf(0, 100, 100, 100)
                        vibrator.vibrate(pattern, -1)
                    }
                }
            }
        }
    }

    var isGalaxyView by remember { mutableStateOf(false) }
    var targetGalaxyPan by remember { mutableStateOf(Offset.Zero) }

    if (isGalaxyView) {
        GalaxyScreen(
            earnedStars = uiState.earnedStars,
            initialPan = targetGalaxyPan,
            // Arriving straight off a supernova, the voyage has already
            // revealed these stars — replaying the entrance would break
            // the handoff. Only animate when opened cold from the orb.
            animateEntrance = uiState.sessionState != SessionState.COMPLETED,
            onClose = {
                isGalaxyView = false
                if (uiState.sessionState == SessionState.COMPLETED) {
                    viewModel.resetSession()
                }
            }
        )
    } else {
        // ── Two-page HorizontalPager ─────────────────────────────────
        // Page 0: The Focus Orb
        // Page 1: Duration Selection Menu (only reachable when IDLE)
        val isIdle = uiState.sessionState == SessionState.IDLE
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { if (isIdle) 2 else 1 })
        val coroutineScope = rememberCoroutineScope()

        // Hoisted so the Scaffold can drive the scroll indicator on the arc
        // while the list itself lives a level down, and so the list opens
        // already centred on whatever duration is currently selected.
        val durationListState = rememberScalingLazyListState(
            initialCenterItemIndex = uiState.selectedDurationIndex
        )

        // Auto-snap back to page 0 when a session starts
        LaunchedEffect(isIdle) {
            if (!isIdle && pagerState.currentPage != 0) {
                pagerState.animateScrollToPage(0)
            }
        }

        val onDurationPage = pagerState.currentPage == 1
        // The clock is native furniture and belongs on every ordinary screen,
        // but it would sit on top of the supernova and the shatter. Those are
        // the two moments the app is asking to be watched, so it steps aside.
        val showTimeText = !isAmbient &&
            uiState.sessionState != SessionState.COMPLETED &&
            uiState.sessionState != SessionState.SHATTERED

        Scaffold(
            timeText = { if (showTimeText) TimeText() },
            vignette = { if (onDurationPage) Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { if (onDurationPage) PositionIndicator(durationListState) }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> {
                        // ── Main Orb Page ──────────────────────────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.toggleSession()
                                        },
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.resetSession()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            FocusOrb(
                                isAmbient = isAmbient,
                                progress = uiState.progress,
                                timeRemainingMs = uiState.timeRemainingMs,
                                sessionState = uiState.sessionState,
                                orbHealth = uiState.orbHealth,
                                currentDuration = uiState.currentDuration,
                                earnedStars = uiState.earnedStars,
                                onTransitionToGalaxy = { finalPan ->
                                    targetGalaxyPan = finalPan
                                    isGalaxyView = true
                                }
                            )

                            // Bottom centre is the only spot on a round display
                            // with full width to spare — the top arc belongs to
                            // the clock, which is where this control used to sit.
                            if (isIdle) {
                                CompactChip(
                                    onClick = { isGalaxyView = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp),
                                    colors = ChipDefaults.secondaryChipColors(
                                        backgroundColor = Color.White.copy(alpha = 0.12f),
                                        contentColor = Color.White
                                    ),
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Galaxy",
                                            style = MaterialTheme.typography.button
                                        )
                                    }
                                )
                            }
                        }
                    }
                    1 -> {
                        // ── Duration Selection Menu ───────────────────────────
                        DurationSelectionPage(
                            selectedIndex = uiState.selectedDurationIndex,
                            listState = durationListState,
                            onSelect = { index ->
                                viewModel.selectDuration(index)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Galaxy Screen ───────────────────────────────────────────────────────
/**
 * The galaxy, plus its dismiss affordance.
 *
 * Wear's usual swipe-to-dismiss is unavailable here: [GalaxyView] consumes
 * horizontal drags to pan the star field, so the gesture would fight itself.
 * An explicit button is the honest answer, parked at bottom centre where it
 * clears both the bezel and the stars.
 */
@Composable
fun GalaxyScreen(
    earnedStars: List<StarSize>,
    initialPan: Offset,
    animateEntrance: Boolean,
    onClose: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        GalaxyView(
            earnedStars = earnedStars,
            initialPan = initialPan,
            animateEntrance = animateEntrance
        )

        CompactButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.secondaryButtonColors(
                backgroundColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close galaxy",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── Duration Selection Page ─────────────────────────────────────────────

/** Representative colour per reward tier, drawn from the galaxy palette. */
fun starTierColor(size: StarSize): Color = when (size) {
    StarSize.SMALL -> Color(0xFF00BCD4)   // Rich Cyan
    StarSize.MEDIUM -> Color(0xFF7B68EE)  // Medium Slate Blue
    StarSize.LARGE -> Color(0xFF00FA9A)   // Medium Spring Green
    StarSize.EPIC -> Color(0xFFFFFACD)    // Glowing Golden White
}

/** Human-readable name for a reward tier. */
fun starTierLabel(size: StarSize): String = when (size) {
    StarSize.SMALL -> "Small Star"
    StarSize.MEDIUM -> "Medium Star"
    StarSize.LARGE -> "Large Star"
    StarSize.EPIC -> "Epic Star"
}

/**
 * Duration picker, built as a [ScalingLazyColumn] of chips.
 *
 * The previous flat Column ran edge to edge, which put the corners of every
 * row underneath the bezel and left the header clipped against the top of the
 * display. ScalingLazyColumn is the Wear idiom for exactly this: it scales and
 * fades rows toward the rim, so the list stays legible inside the circle and
 * the curvature becomes part of the design instead of something fighting it.
 */
@Composable
fun DurationSelectionPage(
    selectedIndex: Int,
    listState: ScalingLazyListState,
    onSelect: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp)
    ) {
        // No list header here on purpose. The screen holds four chips that each
        // read "N min", so a title adds nothing — and as the first item it was
        // scrolling up underneath the system clock, which read as a glitch.
        itemsIndexed(AVAILABLE_DURATIONS) { index, duration ->
            val isSelected = index == selectedIndex
            val tier = starTierColor(duration.starSize)

            // Selection is a tinted fill rather than a solid accent: a
            // full-strength chip is shouting on a black watch face at night.
            val background by animateColorAsState(
                targetValue = if (isSelected) tier.copy(alpha = 0.22f) else Color(0xFF1A1B1E),
                animationSpec = tween(250),
                label = "chipBackground"
            )

            Chip(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(index)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.chipColors(
                    backgroundColor = background,
                    contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                    secondaryContentColor = if (isSelected) tier else Color.White.copy(alpha = 0.45f),
                    iconColor = if (isSelected) tier else Color.White.copy(alpha = 0.35f)
                ),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = "${duration.minutes} min",
                        style = MaterialTheme.typography.button
                    )
                },
                secondaryLabel = {
                    Text(
                        text = starTierLabel(duration.starSize),
                        style = MaterialTheme.typography.caption2
                    )
                }
            )
        }
    }
}

data class Particle(val angle: Float, val speed: Float, val sizeMultiplier: Float)
data class BurstParticle(val angle: Float, val speed: Float, val radius: Float, val alphaDecay: Float)
data class Fracture(val mainPath: Path, val branches: List<Path>)

/**
 * A single volumetric shaft of light thrown out by the supernova detonation.
 *
 * @param lag Fraction of the ray phase to wait before this shaft appears —
 *            staggering them makes the burst feel like an eruption rather
 *            than a symmetric starburst stamp.
 */
data class LightRay(val angle: Float, val length: Float, val width: Float, val lag: Float)

fun generateFracture(random: Random, startRadius: Float, endRadius: Float, startAngle: Float, center: Offset): Fracture {
    val mainPath = Path()
    val branches = mutableListOf<Path>()
    
    // Realistic jagged crack
    val steps = 8 + random.nextInt(6)
    var currentRadius = startRadius
    var currentAngle = startAngle
    
    var cx = center.x + kotlin.math.cos(currentAngle.toDouble()).toFloat() * currentRadius
    var cy = center.y + kotlin.math.sin(currentAngle.toDouble()).toFloat() * currentRadius
    mainPath.moveTo(cx, cy)
    
    val radiusStep = (endRadius - startRadius) / steps // Will be negative if going inwards
    val stepMagnitude = kotlin.math.abs(radiusStep)
    
    for (i in 1..steps) {
        // Crack propagates generally forward with jagged deviations (-30 to +30 degrees)
        val angleChange = (random.nextFloat() - 0.5f) * 1.0f
        currentAngle += angleChange
        
        currentRadius += radiusStep + (random.nextFloat() - 0.5f) * (stepMagnitude * 0.4f)
        
        cx = center.x + kotlin.math.cos(currentAngle.toDouble()).toFloat() * currentRadius
        cy = center.y + kotlin.math.sin(currentAngle.toDouble()).toFloat() * currentRadius
        mainPath.lineTo(cx, cy)
        
        // Micro-branching
        if (i > 1 && i < steps && random.nextFloat() > 0.3f && branches.size < 4) {
            val branchPath = Path()
            branchPath.moveTo(cx, cy)
            
            // Branch shoots off at a sharper angle (30 to 60 degrees away)
            var bAngle = currentAngle + (0.5f + random.nextFloat() * 0.6f) * (if (random.nextBoolean()) 1f else -1f)
            
            val bSteps = 1 + random.nextInt(3)
            val bStepLen = stepMagnitude * (0.6f + random.nextFloat() * 0.8f)
            
            var bx = cx
            var by = cy
            for (j in 1..bSteps) {
                bAngle += (random.nextFloat() - 0.5f) * 0.5f
                // If the crack is propagating inwards, currentRadius is shrinking.
                // We want the branch to also go in the general direction of bAngle.
                // However, since currentAngle is the angle FROM the center, to move INWARDS,
                // we actually move in the direction of (bAngle + PI).
                // Wait! If startRadius=1.1, endRadius=0.1, then radius is decreasing.
                // But currentAngle is just the polar coordinate angle. 
                // To move inwards along currentAngle, the direction vector is actually (currentAngle + PI).
                val moveAngle = bAngle + Math.PI.toFloat()
                bx += kotlin.math.cos(moveAngle.toDouble()).toFloat() * bStepLen
                by += kotlin.math.sin(moveAngle.toDouble()).toFloat() * bStepLen
                branchPath.lineTo(bx, by)
            }
            branches.add(branchPath)
        }
    }
    return Fracture(mainPath, branches)
}

fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

@Composable
fun FocusOrb(
    isAmbient: Boolean = false,
    progress: Float, 
    timeRemainingMs: Long = 30 * 60 * 1000L, 
    sessionState: SessionState = SessionState.IDLE, 
    orbHealth: Int = 3,
    currentDuration: SessionDuration = SessionDuration(30, StarSize.MEDIUM),
    earnedStars: List<StarSize> = emptyList(),
    onTransitionToGalaxy: (Offset) -> Unit = {}
) {
    val isCompleted = sessionState == SessionState.COMPLETED
    val isShattered = sessionState == SessionState.SHATTERED
    val isIdle = sessionState == SessionState.IDLE
    
    val density = androidx.compose.ui.platform.LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    
    // Supernova Animation Timeline (0f to 1f over 4000ms)
    val supernovaTime = remember { Animatable(0f) }
    val coordinates = remember(earnedStars.size) { generateGalaxyLayout(earnedStars.size) }
    
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val finalGalaxyPan = remember(earnedStars.size, screenWidthPx) {
        val hexSpacing = screenWidthPx * 0.085f
        val (newQ, newR) = coordinates.lastOrNull() ?: Pair(0, 0)
        val baseX = hexSpacing * 3f / 2f * newQ
        val baseY = hexSpacing * kotlin.math.sqrt(3f) * (newR + newQ / 2f)
        Offset(-baseX, -baseY)
    }
    
    LaunchedEffect(isCompleted, isAmbient) {
        if (isCompleted) {
            // Only start the completion star animation when the screen is fully awake from ambient mode.
            if (!isAmbient) {
                kotlinx.coroutines.delay(400) // Wait for screen hardware to wake up
                supernovaTime.animateTo(1f, animationSpec = tween(4000, easing = LinearEasing))
                onTransitionToGalaxy(finalGalaxyPan)
            }
        } else {
            supernovaTime.snapTo(0f)
        }
    }
    
    val completionColorBlend by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = tween(4000, easing = LinearEasing),
        label = "completionColorBlend"
    )

    // Shatter animation progress
    val shatterProgress = remember { Animatable(0f) }
    LaunchedEffect(isShattered, isAmbient) {
        if (isShattered) {
            // Only start the shatter animation when the screen is fully awake from ambient mode.
            // The small delay ensures the OLED screen has physically turned on before the explosion.
            if (!isAmbient) {
                kotlinx.coroutines.delay(400) // Wait for screen hardware to wake up
                shatterProgress.animateTo(1f, animationSpec = tween(3000, easing = LinearOutSlowInEasing))
            }
        } else {
            shatterProgress.snapTo(0f)
        }
    }

    // Fracture Snap Animations
    val fracture1Progress = remember { Animatable(if (orbHealth <= 2) 1f else 0f) }
    val fracture2Progress = remember { Animatable(if (orbHealth <= 1) 1f else 0f) }
    val screenShake = remember { Animatable(0f) }

    LaunchedEffect(orbHealth) {
        val snapEasing = CubicBezierEasing(0.0f, 1.0f, 0.2f, 1.0f) // Visceral, instant snap
        
        if (orbHealth < 3) {
            // Screen shake on any damage
            launch {
                screenShake.snapTo(1f)
                screenShake.animateTo(0f, animationSpec = tween(200, easing = LinearOutSlowInEasing))
            }
        }
        
        if (orbHealth == 2 && fracture1Progress.value == 0f) {
            fracture1Progress.animateTo(1f, animationSpec = tween(150, easing = snapEasing))
        }
        if (orbHealth == 1 && fracture2Progress.value == 0f) {
            fracture2Progress.animateTo(1f, animationSpec = tween(150, easing = snapEasing))
        }
        if (orbHealth == 3) {
            fracture1Progress.snapTo(0f)
            fracture2Progress.snapTo(0f)
            screenShake.snapTo(0f)
        }
    }

    // Generate random shatter particles once
    val particles = remember {
        val random = Random(42)
        List(15) {
            Particle(
                angle = random.nextFloat() * 2 * Math.PI.toFloat(),
                speed = 30f + random.nextFloat() * 60f,
                sizeMultiplier = 0.4f + random.nextFloat() * 0.6f
            )
        }
    }
    
    // Generate supernova burst particles (slower speed, higher decay for floating effect)
    val burstParticles = remember {
        val random = Random(100)
        List(26) {
            BurstParticle(
                angle = random.nextFloat() * 2 * Math.PI.toFloat(),
                speed = 20f + random.nextFloat() * 60f,
                radius = 2f + random.nextFloat() * 6f,
                alphaDecay = 1.0f + random.nextFloat() * 2.0f
            )
        }
    }

    // Volumetric light rays fired at the moment of detonation. Staggered lags
    // stop them reading as a single symmetric asterisk.
    val lightRays = remember {
        val random = Random(77)
        List(16) {
            LightRay(
                angle = random.nextFloat() * 2 * Math.PI.toFloat(),
                length = 0.55f + random.nextFloat() * 0.75f,
                width = 1.5f + random.nextFloat() * 4.5f,
                lag = random.nextFloat() * 0.18f
            )
        }
    }

    // Shared shimmer clock so the newborn star and the historical stars
    // twinkle continuously into — and through — the galaxy handoff.
    val starClockTransition = rememberInfiniteTransition(label = "starClock")
    val starClock by starClockTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "starClockValue"
    )

    // Star Genesis Scale Physics — a punchier spring than a stock preset:
    // it overshoots hard, then settles into the Majestic Bask.
    val starScale by animateFloatAsState(
        targetValue = if (isCompleted && supernovaTime.value >= 0.2f) 3.5f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 180f),
        label = "starGenesis"
    )

    // Pulsating Aura
    val auraPulsate = rememberInfiniteTransition(label = "aura")
    val auraPulseScale by auraPulsate.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "auraScale"
    )

    // Define colors
    val deepNavyBlue = Color(0xFF001F3F)
    val richCyan = Color(0xFF00BCD4)
    val glowingGoldenWhite = Color(0xFFFFFACD)

    // Interpolate base color based on session progress
    val progressBaseColor = if (progress < 0.5f) {
        lerp(deepNavyBlue, richCyan, progress * 2f)
    } else {
        lerp(richCyan, glowingGoldenWhite, (progress - 0.5f) * 2f)
    }

    val t = supernovaTime.value
    // Collapse phase (0f to 0.2f): fade to pure white just before bursting
    val collapseProgress = (t / 0.2f).coerceIn(0f, 1f)
    val baseColorBlend = if (!isCompleted) 0f else collapseProgress
    val baseColor = lerp(progressBaseColor, Color.White, baseColorBlend)

    // Dynamic breathing animation
    val breathingAnim = remember { Animatable(0.85f) }
    var isBreathingIn by remember { mutableStateOf(true) }
    
    LaunchedEffect(orbHealth, isShattered, isCompleted) {
        if (isShattered || isCompleted) {
            breathingAnim.stop()
            return@LaunchedEffect
        }
        
        val breathDuration = when(orbHealth) {
            3 -> 4000
            2 -> 2000
            1 -> 1000
            else -> 4000
        }
        
        while(true) {
            val target = if (isBreathingIn) 1.0f else 0.85f
            breathingAnim.animateTo(
                targetValue = target, 
                animationSpec = tween(breathDuration, easing = FastOutSlowInEasing)
            )
            isBreathingIn = !isBreathingIn
        }
    }

    val pathMeasure = remember { PathMeasure() }
    val drawPath = remember { Path() }
    val branchDrawPath = remember { Path() }
    var fractures by remember { mutableStateOf<List<Fracture>?>(null) }
    
    // Pre-allocate paints for the fracture to avoid inside-draw allocations
    val fractureGlowPaint = remember { Paint() }
    val fractureCorePaint = remember { Paint() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (screenShake.value > 0f) {
                    val maxShake = 15f * density.density
                    val dampening = screenShake.value
                    val oscillation = kotlin.math.sin(screenShake.value * 12 * Math.PI.toFloat())
                    val shakeOffset = maxShake * dampening * oscillation
                    translationX = shakeOffset
                    translationY = shakeOffset * 0.5f // Add slight vertical shake
                }
            }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.minDimension / 2.5f
            
            // Initialize fractures using the absolute center and baseRadius
            if (fractures == null) {
                // Use a truly random seed so it's different every time
                val random = Random(System.currentTimeMillis())
                // Start thick at the edge (1.1f) and go microscopic towards the center (0.1f)
                fractures = listOf(
                    generateFracture(random, baseRadius * 1.1f, baseRadius * (0.1f + random.nextFloat() * 0.2f), random.nextFloat() * 2 * Math.PI.toFloat(), center),
                    generateFracture(random, baseRadius * 1.1f, baseRadius * (0.1f + random.nextFloat() * 0.2f), random.nextFloat() * 2 * Math.PI.toFloat(), center)
                )
            }
            
            if (isAmbient) {
                // Ambient Mode: Minimal, static white outline
                drawCircle(
                    color = Color.White,
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Draw simple static fractures if damaged
                if (orbHealth < 3 && fractures != null) {
                    if (orbHealth <= 2) {
                        drawPath(
                            path = fractures!![0].mainPath,
                            color = Color.White,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    if (orbHealth <= 1) {
                        drawPath(
                            path = fractures!![1].mainPath,
                            color = Color.White,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            } else if (isShattered) {
                // Shatter explosion rendering
                val progressVal = shatterProgress.value
                val currentAlpha = 1f - (progressVal * progressVal)
                
                particles.forEach { particle ->
                    val distance = particle.speed * progressVal * 4.5f
                    val px = center.x + kotlin.math.cos(particle.angle.toDouble()).toFloat() * distance
                    val py = center.y + kotlin.math.sin(particle.angle.toDouble()).toFloat() * distance
                    val pRadius = (baseRadius / 4f) * particle.sizeMultiplier * currentAlpha
                    
                    if (currentAlpha > 0) {
                        drawCircle(
                            color = progressBaseColor.copy(alpha = currentAlpha),
                            radius = pRadius,
                            center = Offset(px, py)
                        )
                    }
                }
            } else {
                // Dimming based on health
                val healthDimming = when(orbHealth) {
                    3 -> 1.0f
                    2 -> 0.6f
                    1 -> 0.3f
                    else -> 1.0f
                }
                
                // Stage 1: The Collapse (0.0f to 0.2f)
                // Now in two beats — the orb draws a breath and swells slightly
                // before it implodes. The anticipation is what sells the
                // detonation that follows.
                val coreScale = if (!isCompleted) {
                    breathingAnim.value
                } else {
                    val inhale = breathingAnim.value * 1.18f
                    if (collapseProgress < 0.35f) {
                        val a = FastOutSlowInEasing.transform(collapseProgress / 0.35f)
                        androidx.compose.ui.util.lerp(breathingAnim.value, inhale, a)
                    } else {
                        // Cubic acceleration inward — slow release, violent finish.
                        val a = (collapseProgress - 0.35f) / 0.65f
                        androidx.compose.ui.util.lerp(inhale, 0.12f, a * a * a)
                    }
                }

                // Disappear the core entirely after the collapse completes
                val coreAlpha = if (!isCompleted || t < 0.2f) 1f else 0f
                
                // Scale the radius
                val outerGlowRadius = baseRadius * coreScale
                
                if (coreAlpha > 0f) {
                    // Draw the glowing orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.9f * healthDimming * coreAlpha),
                                0.25f to baseColor.copy(alpha = 1f * healthDimming * coreAlpha),
                                0.7f to baseColor.copy(alpha = 0.5f * (if (isCompleted) 1f else breathingAnim.value) * healthDimming * coreAlpha),
                                1.0f to Color.Transparent
                            ),
                            center = center,
                            radius = outerGlowRadius
                        ),
                        center = center,
                        radius = outerGlowRadius
                    )
                }
                
                val newStarIndex = kotlin.math.max(0, earnedStars.size - 1)
                val newStarColor = getStarThemeColor(newStarIndex, currentDuration.starSize)
                val screenSpan = kotlin.math.max(size.width, size.height)

                // Stage 1.5: Shockwave rings (0.18f to 0.62f)
                // Two rings at different speeds — a fast thin white leading edge
                // and a slower, softer coloured wake behind it.
                if (isCompleted && t >= 0.18f && t < 0.62f) {
                    val sw = ((t - 0.18f) / 0.44f).coerceIn(0f, 1f)
                    val maxRing = screenSpan * 0.62f

                    val leadT = (sw / 0.62f).coerceAtMost(1f)
                    if (leadT < 1f) {
                        val e = 1f - (1f - leadT) * (1f - leadT) * (1f - leadT)
                        drawCircle(
                            color = Color.White.copy(alpha = (1f - leadT) * (1f - leadT) * 0.55f),
                            radius = baseRadius * 0.1f + e * maxRing,
                            center = center,
                            style = Stroke(width = 0.6f + (1f - leadT) * 3f),
                            blendMode = BlendMode.Screen
                        )
                    }

                    // Wide and faint rather than narrow and solid — a thin,
                    // opaque stroke reads as a drawn outline instead of an
                    // expanding wave of energy.
                    val e2 = 1f - (1f - sw) * (1f - sw)
                    drawCircle(
                        color = newStarColor.copy(alpha = (1f - sw) * (1f - sw) * 0.30f),
                        radius = baseRadius * 0.05f + e2 * maxRing * 0.78f,
                        center = center,
                        style = Stroke(width = 2f + (1f - sw) * 14f),
                        blendMode = BlendMode.Screen
                    )
                }

                // Stage 1.75: Volumetric light rays (0.19f to 0.34f)
                // Shafts stay anchored just outside the core and only their
                // outer end travels, so they read as light lancing out of the
                // star. Letting the inner end advance too made them detach and
                // drift as loose grey sticks.
                if (isCompleted && t >= 0.19f && t < 0.34f) {
                    val rp = ((t - 0.19f) / 0.15f).coerceIn(0f, 1f)
                    // Short and thick. Long thin shafts render as hairline
                    // scratches across the face; keeping them close to the core
                    // lets them fuse with its glow into one anisotropic burst.
                    val maxLen = screenSpan * 0.20f
                    val inner = baseRadius * 0.08f

                    lightRays.forEach { ray ->
                        val local = ((rp - ray.lag) / (1f - ray.lag)).coerceIn(0f, 1f)
                        if (local <= 0f || local >= 1f) return@forEach

                        val eased = 1f - (1f - local) * (1f - local) * (1f - local)
                        val cosA = kotlin.math.cos(ray.angle.toDouble()).toFloat()
                        val sinA = kotlin.math.sin(ray.angle.toDouble()).toFloat()
                        val outer = inner + eased * maxLen * ray.length

                        drawLine(
                            color = lerp(Color.White, newStarColor, local)
                                .copy(alpha = (1f - local) * (1f - local) * 0.85f),
                            start = Offset(center.x + cosA * inner, center.y + sinA * inner),
                            end = Offset(center.x + cosA * outer, center.y + sinA * outer),
                            strokeWidth = ray.width * 1.15f * (1f - local * 0.5f),
                            cap = StrokeCap.Round,
                            blendMode = BlendMode.Screen
                        )
                    }
                }

                // Stage 2: The Burst Particle Emitter (0.2f to 0.8f)
                if (isCompleted && t >= 0.2f && t < 0.8f) {
                    val pProgress = ((t - 0.2f) / 0.6f).coerceIn(0f, 1f)
                    val easedProgress = FastOutSlowInEasing.transform(pProgress)

                    burstParticles.forEach { particle ->
                        // Particles rapidly decelerate and fade
                        val currentAlpha = (1f - (pProgress * particle.alphaDecay)).coerceIn(0f, 1f)

                        if (currentAlpha > 0f) {
                            val distance = baseRadius * 0.2f + particle.speed * easedProgress * 3f
                            val cosA = kotlin.math.cos(particle.angle.toDouble()).toFloat()
                            val sinA = kotlin.math.sin(particle.angle.toDouble()).toFloat()
                            val px = center.x + cosA * distance
                            val py = center.y + sinA * distance

                            // Color transition from hot white to target star color as they expand
                            val pColor = lerp(Color.White, newStarColor, pProgress)

                            // Motion trail — longest at launch, gone by the time
                            // the particle has spent its momentum.
                            val trailLen = particle.speed * 0.7f * (1f - easedProgress)
                            if (trailLen > 1f) {
                                drawLine(
                                    color = pColor.copy(alpha = currentAlpha * 0.5f),
                                    start = Offset(px - cosA * trailLen, py - sinA * trailLen),
                                    end = Offset(px, py),
                                    strokeWidth = particle.radius * 0.9f,
                                    cap = StrokeCap.Round,
                                    blendMode = BlendMode.Screen
                                )
                            }

                            drawCircle(
                                color = pColor.copy(alpha = currentAlpha),
                                radius = particle.radius,
                                center = Offset(px, py),
                                blendMode = BlendMode.Screen
                            )
                        }
                    }
                }

                // Stage 2.5: Detonation flash
                // Deliberately brief — roughly 240ms, decaying cubically. Held
                // any longer it stops reading as a flash and just veils the
                // whole watch face in flat grey.
                if (isCompleted && t >= 0.185f && t < 0.245f) {
                    val f = 1f - ((t - 0.185f) / 0.06f).coerceIn(0f, 1f)
                    drawRect(color = Color.White.copy(alpha = f * f * f * 0.85f))
                }

                // Stage 3 & 4: The Bask (0.4f to 0.6f) & The Voyage & Reveal (0.6f to 1.0f)
                if (isCompleted && starScale > 0.01f) {
                    val baseStarRadius = with(density) { (currentDuration.starSize.sizeDp / 2).dp.toPx() }

                    val voyageProgress = ((t - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    val easedVoyage = FastOutSlowInEasing.transform(voyageProgress)

                    // Hex Math for target slot (already calculated in finalGalaxyPan, but let's calculate current pan)
                    val hexSpacing = size.width * 0.085f
                    val currentPanX = androidx.compose.ui.util.lerp(0f, finalGalaxyPan.x, easedVoyage)
                    val currentPanY = androidx.compose.ui.util.lerp(0f, finalGalaxyPan.y, easedVoyage)

                    val lensRadius = screenSpan

                    // The dust field fades up as the voyage begins and is handed
                    // off to GalaxyView at the same pan, so the two screens read
                    // as one continuous space.
                    if (voyageProgress > 0.001f) {
                        drawGalaxyDust(
                            pan = Offset(currentPanX, currentPanY),
                            alpha = voyageProgress,
                            twinkle = starClock
                        )
                    }

                    // Unit vector along the direction the galaxy is sliding, used
                    // to trail the incoming historical stars.
                    val panLen = kotlin.math.sqrt(
                        finalGalaxyPan.x * finalGalaxyPan.x + finalGalaxyPan.y * finalGalaxyPan.y
                    )
                    val streakLen = kotlin.math.sin(Math.PI.toFloat() * voyageProgress) * hexSpacing * 0.55f

                    // Stage 4 Reveal: Draw Historical Stars Sliding and Fading In
                    if (voyageProgress > 0.01f && earnedStars.size > 1) {
                        for (i in 0 until earnedStars.size - 1) {
                            val histSize = earnedStars[i]
                            val (hq, hr) = coordinates[i]
                            
                            val hBaseX = hexSpacing * 3f / 2f * hq
                            val hBaseY = hexSpacing * kotlin.math.sqrt(3f) * (hr + hq / 2f)
                            
                            // Add currentPan so they slide underneath
                            val hRawX = center.x + hBaseX + currentPanX
                            val hRawY = center.y + hBaseY + currentPanY
                            
                            val hdx = hRawX - center.x
                            val hdy = hRawY - center.y
                            val hRawDist = kotlin.math.sqrt(hdx * hdx + hdy * hdy)
                            val hNormDist = (hRawDist / lensRadius).coerceIn(0f, 1f)
                            
                            val hProx = (1f - hNormDist)
                            val hSteep = hProx * hProx * hProx * hProx
                            val maxBonusScale = 3.2f
                            val baseTargetScale = 0.35f
                            val hMag = baseTargetScale + maxBonusScale * hSteep
                            val hTargetAlpha = (1f - hNormDist).coerceIn(0.3f, 1f)
                            
                            val hFish = if (hRawDist < 0.01f) 1f else {
                                val n = (hRawDist / lensRadius).coerceIn(0f, 1f)
                                val displaced = n * (1f - 0.45f * n * n)
                                val pushBoost = 1f + hSteep * 1.1f
                                (displaced * lensRadius * pushBoost) / hRawDist
                            }
                            val hFinalX = center.x + hdx * hFish
                            val hFinalY = center.y + hdy * hFish
                            
                            val hBaseRadius = with(density) { (histSize.sizeDp / 2f).dp.toPx() }
                            val hColor = getStarThemeColor(i, histSize)
                            val hAlpha = hTargetAlpha * voyageProgress

                            // Motion streak trailing behind the slide, peaking
                            // mid-voyage and gone by the time they settle.
                            if (streakLen > 1f && panLen > 0.01f) {
                                val ux = finalGalaxyPan.x / panLen
                                val uy = finalGalaxyPan.y / panLen
                                drawLine(
                                    color = hColor.copy(alpha = hAlpha * 0.35f),
                                    start = Offset(hFinalX - ux * streakLen, hFinalY - uy * streakLen),
                                    end = Offset(hFinalX, hFinalY),
                                    strokeWidth = hBaseRadius * hMag * 0.55f,
                                    cap = StrokeCap.Round,
                                    blendMode = BlendMode.Screen
                                )
                            }

                            drawCinematicStar(
                                x = hFinalX, y = hFinalY,
                                scale = hMag,
                                alpha = hAlpha,
                                baseRadius = hBaseRadius,
                                themeColor = hColor,
                                pulseAuraScale = 1f,
                                twinkle = starClock + i * 0.381966f,
                                spikeIntensity = voyageProgress
                            )
                        }
                    }

                    // Draw the New Star
                    // It is perfectly centered on screen, so it always sits at the peak of the fisheye lens
                    val maxBonusScale = 3.2f
                    val baseTargetScale = 0.35f
                    val targetMagnification = baseTargetScale + maxBonusScale * 1f // steepCurve is 1 at center
                    val currentScale = androidx.compose.ui.util.lerp(starScale, targetMagnification, easedVoyage)

                    // Diffraction spikes bloom outward over the first ~200ms of
                    // the star's life rather than snapping on with it.
                    val newStarSpikes = ((t - 0.24f) / 0.2f).coerceIn(0f, 1f)

                    drawCinematicStar(
                        x = center.x, y = center.y,
                        scale = currentScale,
                        alpha = 1f, // The new star is always fully visible
                        baseRadius = baseStarRadius,
                        themeColor = newStarColor,
                        pulseAuraScale = auraPulseScale,
                        twinkle = starClock + newStarIndex * 0.381966f,
                        spikeIntensity = newStarSpikes
                    )
                }
                
                // Draw Crystalline Fractures if damaged.
                // Gated on coreAlpha so the cracks vanish with the orb they sit
                // on, instead of hanging in empty space through the supernova.
                if (orbHealth < 3 && coreAlpha > 0f && fractures != null) {
                    val activeFractures = listOf(
                        Pair(fractures!![0], fracture1Progress.value),
                        Pair(fractures!![1], fracture2Progress.value)
                    )
                    
                    val glowColor = if (currentDuration.starSize == StarSize.EPIC) Color(0xFFFF5252) else Color(0xFF00BCD4)
                    
                    val maxStrokeGlow = 12f // Thicker base for light bleed
                    val minStrokeGlow = 2f
                    val maxStrokeCore = 3f // Thinner hot core
                    val minStrokeCore = 0.5f
                    val stepLength = 10f // finer chunks for smoother tapering
                    
                    activeFractures.forEach { (fracture, progress) ->
                        if (progress > 0f) {
                            pathMeasure.setPath(fracture.mainPath, false)
                            val length = pathMeasure.length
                            val animatedLength = length * progress
                            
                            var currentD = 0f
                            while (currentD < animatedLength) {
                                val nextD = (currentD + stepLength).coerceAtMost(animatedLength)
                                drawPath.reset()
                                pathMeasure.getSegment(currentD, nextD, drawPath, true)
                                
                                val midD = (currentD + nextD) / 2f
                                val fraction = midD / length
                                
                                val currentWidthGlow = maxStrokeGlow + (minStrokeGlow - maxStrokeGlow) * fraction
                                val currentWidthCore = maxStrokeCore + (minStrokeCore - maxStrokeCore) * fraction
                                
                                drawIntoCanvas { canvas ->
                                    fractureGlowPaint.apply {
                                        this.color = glowColor
                                        this.style = PaintingStyle.Stroke
                                        this.strokeWidth = currentWidthGlow
                                        this.strokeCap = StrokeCap.Round
                                        this.strokeJoin = StrokeJoin.Round
                                        this.blendMode = BlendMode.Screen
                                        // Dynamic blur mask filter based on width
                                        this.asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(currentWidthGlow * 1.5f + 1f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                                    }
                                    canvas.drawPath(drawPath, fractureGlowPaint)
                                    
                                    fractureCorePaint.apply {
                                        this.color = Color.White
                                        this.style = PaintingStyle.Stroke
                                        this.strokeWidth = currentWidthCore
                                        this.strokeCap = StrokeCap.Round
                                        this.strokeJoin = StrokeJoin.Round
                                        this.blendMode = BlendMode.SrcOver
                                    }
                                    canvas.drawPath(drawPath, fractureCorePaint)
                                }
                                currentD += stepLength
                            }
                            
                            // Micro-branches
                            fracture.branches.forEach { branch ->
                                pathMeasure.setPath(branch, false)
                                val bAnimLength = pathMeasure.length * progress
                                if (bAnimLength > 0f) {
                                    branchDrawPath.reset()
                                    pathMeasure.getSegment(0f, bAnimLength, branchDrawPath, true)
                                    
                                    drawIntoCanvas { canvas ->
                                        fractureGlowPaint.strokeWidth = 2f
                                        canvas.drawPath(branchDrawPath, fractureGlowPaint)
                                        fractureCorePaint.strokeWidth = 0.5f
                                        canvas.drawPath(branchDrawPath, fractureCorePaint)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isShattered) {
            val progressVal = shatterProgress.value
            Text(
                text = "Focus Lost",
                style = MaterialTheme.typography.title2,
                color = Color.White.copy(alpha = progressVal),
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (isIdle) {
            // Bottom padding clears the Galaxy chip parked below it.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${currentDuration.minutes} min",
                    style = MaterialTheme.typography.title3,
                    color = Color.White
                )
                Text(
                    text = "Tap to begin · Swipe to change",
                    style = MaterialTheme.typography.caption3,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        } else if (!isCompleted) {
            // Format and display time remaining
            val totalSeconds = timeRemainingMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val timeString = String.format("%02d:%02d", minutes, seconds)

            Text(
                text = timeString,
                style = MaterialTheme.typography.title2,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    FocusOrbTheme {
        FocusOrbApp()
    }
}

fun generateHexSpiral(count: Int): List<Pair<Int, Int>> {
    val results = mutableListOf(Pair(0, 0))
    if (count <= 1) return results
    
    // Generate enough rings to ensure we can pick a full circle.
    var ringsNeeded = 1
    while (1 + 3 * ringsNeeded * (ringsNeeded + 1) < count * 2) {
        ringsNeeded++
    }
    
    val tempHex = mutableListOf(Pair(0, 0))
    val directions = listOf(
        Pair(-1, 1), Pair(-1, 0), Pair(0, -1),
        Pair(1, -1), Pair(1, 0), Pair(0, 1)
    )
    
    for (ring in 1..ringsNeeded) {
        var q = ring
        var r = 0
        for (i in 0 until 6) {
            for (j in 0 until ring) {
                tempHex.add(Pair(q, r))
                q += directions[i].first
                r += directions[i].second
            }
        }
    }
    
    // Sort by physical distance from center (squared) to form a perfect circular cluster
    return tempHex.sortedBy { (q, r) ->
        val x = 1.5f * q
        val y = kotlin.math.sqrt(3f) * (r + q / 2f)
        x * x + y * y // squared distance
    }.take(count)
}

fun generateGalaxyLayout(count: Int): List<Pair<Int, Int>> {
    val hexSlots = generateHexSpiral(count)
    val layout = mutableListOf<Pair<Int, Int>>()
    var nextSlotIdx = 0
    
    val random = kotlin.random.Random(42) // Consistent seed so historical layout never changes
    for (i in 0 until count) {
        if (i > 0 && random.nextFloat() < 0.20f) {
            val targetIdx = random.nextInt(i)
            layout.add(layout[targetIdx])
        } else {
            if (nextSlotIdx < hexSlots.size) {
                layout.add(hexSlots[nextSlotIdx])
                nextSlotIdx++
            } else {
                layout.add(Pair(0, 0))
            }
        }
    }
    return layout
}

val STAR_PALETTE = listOf(
    Color(0xFF00BCD4), // Rich Cyan
    Color(0xFF8A2BE2), // Blue Violet
    Color(0xFFFF3366), // Vibrant Pink
    Color(0xFF00FA9A), // Medium Spring Green
    Color(0xFF7B68EE), // Medium Slate Blue
    Color(0xFFFF00FF), // Magenta
    Color(0xFF1E90FF), // Dodger Blue
    Color(0xFF00FF7F)  // Spring Green
)

fun getStarThemeColor(index: Int, size: StarSize): Color {
    if (size == StarSize.EPIC) return Color(0xFFFFFACD) // Glowing Golden White
    
    // Hash index to deterministically pick a beautiful color
    val hash = (index * 31 + 17) % STAR_PALETTE.size
    return STAR_PALETTE[hash]
}

@Composable
fun GalaxyView(
    earnedStars: List<StarSize>,
    initialPan: Offset = Offset.Zero,
    animateEntrance: Boolean = true
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var pan by remember { mutableStateOf(initialPan) }
    val flingAnimatable = remember { Animatable(initialPan, Offset.VectorConverter) }
    var flingJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }

    val coordinates = remember(earnedStars.size) { generateGalaxyLayout(earnedStars.size) }

    // ── Ambient shimmer clock ────────────────────────────────────────────
    // One shared linear ramp; each star reads it at its own phase offset.
    val galaxyClock = rememberInfiniteTransition(label = "galaxyClock")
    val twinkleClock by galaxyClock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkleClock"
    )
    val newestPulse by galaxyClock.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "newestPulse"
    )

    // ── Entrance ─────────────────────────────────────────────────────────
    // Skipped when we arrive straight off a supernova — the voyage has already
    // faded these stars in, and popping them again would break the handoff.
    val entrance = remember { Animatable(if (animateEntrance) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animateEntrance) {
            entrance.animateTo(1f, animationSpec = tween(1100, easing = FastOutSlowInEasing))
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val hexSpacing = screenWidthPx * 0.085f
    val maxBaseDist = remember(coordinates, hexSpacing) {
        var maxDist = 0f
        coordinates.forEach { (q, r) ->
            val baseX = hexSpacing * 3f / 2f * q
            val baseY = hexSpacing * kotlin.math.sqrt(3f) * (r + q / 2f)
            val dist = kotlin.math.sqrt(baseX * baseX + baseY * baseY)
            if (dist > maxDist) maxDist = dist
        }
        maxDist
    }
    val maxPanRadius = if (maxBaseDist > 0f) maxBaseDist + screenWidthPx * 0.25f else screenWidthPx * 0.5f

    fun clampPan(p: Offset): Offset {
        val dist = kotlin.math.sqrt(p.x * p.x + p.y * p.y)
        return if (dist > maxPanRadius) p * (maxPanRadius / dist) else p
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                val decay = splineBasedDecay<Offset>(this)
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        flingJob?.cancel()
                        
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        
                        var isDragging = true
                        while (isDragging) {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull { it.id == down.id }
                            
                            if (drag != null && drag.pressed) {
                                val change = drag.position - drag.previousPosition
                                pan = clampPan(pan + change)
                                tracker.addPosition(drag.uptimeMillis, drag.position)
                                drag.consume()
                            } else {
                                isDragging = false
                            }
                        }
                        
                        val velocity = tracker.calculateVelocity()
                        val velocityOffset = Offset(velocity.x, velocity.y)
                        flingJob = coroutineScope.launch {
                            flingAnimatable.snapTo(pan)
                            flingAnimatable.animateDecay(velocityOffset, decay) {
                                pan = clampPan(this.value)
                                if (pan != this.value) { // Hit boundary
                                    coroutineScope.launch { flingAnimatable.stop() }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val screenCenter = Offset(size.width / 2, size.height / 2)
        // Read the synchronous pan state instead of the animatable
        // pan is already read synchronously during drag.
        // Full screen dimension as radius — fade-to-zero happens well off-screen
        val maxRadius = kotlin.math.max(size.width, size.height)
        val lensRadius = maxRadius
        // Hex spacing: outer rings extend past screen when centered
        val hexSpacing = size.width * 0.085f

        // Parallax dust sits behind everything and scrolls at a fraction of the
        // pan, so the galaxy reads as a volume rather than a flat plane.
        drawGalaxyDust(pan = pan, alpha = entrance.value, twinkle = twinkleClock)

        // Staggered reveal: oldest stars land first, newest arrives last.
        val starCount = earnedStars.size
        val staggerWindow = 0.45f

        earnedStars.forEachIndexed { index, starSize ->
            val (q, r) = coordinates[index]

            val slot = if (starCount <= 1) 0f else index.toFloat() / (starCount - 1)
            val entranceLocal = ((entrance.value - slot * staggerWindow) / (1f - staggerWindow))
                .coerceIn(0f, 1f)
            if (entranceLocal <= 0f) return@forEachIndexed
            val entranceEased = FastOutSlowInEasing.transform(entranceLocal)

            // 1. Calculate absolute screen position using screen-relative spacing
            val baseX = hexSpacing * 3f / 2f * q
            val baseY = hexSpacing * kotlin.math.sqrt(3f) * (r + q / 2f)
            val rawScreenX = screenCenter.x + baseX + pan.x
            val rawScreenY = screenCenter.y + baseY + pan.y
            
            // 2. Distance from center
            val dx = rawScreenX - screenCenter.x
            val dy = rawScreenY - screenCenter.y
            val rawDist = kotlin.math.sqrt(dx * dx + dy * dy)
            val normalizedDist = (rawDist / lensRadius).coerceIn(0f, 1f)
            
            // 3. Steep magnification curve: pow(4) for dramatic center pop
            //    Stars at center get up to 3.5x base size, edge stars shrink to 0.25x
            val proximityFactor = (1f - normalizedDist)  // 1 at center, 0 at edge
            val steepCurve = proximityFactor * proximityFactor * proximityFactor * proximityFactor // pow(4)
            val maxBonusScale = 3.2f
            val baseScale = 0.35f
            val magnification = baseScale + maxBonusScale * steepCurve  // 0.25 to 3.5
            
            // 4. No artificial fade — stars stay visible until hardware bezel clips them
            val alpha = (1f - normalizedDist).coerceIn(0.3f, 1f) * entranceEased

            // Skip invisible stars
            if (alpha < 0.01f) return@forEachIndexed

            // 5. Aggressive fish-eye radial displacement
            //    pushBoost scales with magnification so center bubble shoves neighbors apart
            val fishEyeFactor = if (rawDist < 0.01f) 1f else {
                val n = (rawDist / lensRadius).coerceIn(0f, 1f)
                val displaced = n * (1f - 0.45f * n * n)
                val pushBoost = 1f + steepCurve * 1.1f
                (displaced * lensRadius * pushBoost) / rawDist
            }
            val starScreenX = screenCenter.x + dx * fishEyeFactor
            val starScreenY = screenCenter.y + dy * fishEyeFactor
            
            val baseStarRadius = with(density) { (starSize.sizeDp / 2f).dp.toPx() }
            val themeColor = getStarThemeColor(index, starSize)
            
            // 6. Draw with physical scaling.
            //    Stars scale up out of nothing as they enter, and the most
            //    recently earned one keeps a slow breathing aura so your eye
            //    is drawn to what you just collected.
            drawCinematicStar(
                x = starScreenX,
                y = starScreenY,
                scale = magnification * androidx.compose.ui.util.lerp(0.35f, 1f, entranceEased),
                alpha = alpha,
                baseRadius = baseStarRadius,
                themeColor = themeColor,
                pulseAuraScale = if (index == earnedStars.lastIndex) newestPulse else 1f,
                // Golden-ratio phase offset spreads the shimmer evenly, so the
                // field never pulses as one block.
                twinkle = twinkleClock + index * 0.381966f,
                spikeIntensity = entranceEased
            )
        }
    }
}

// ── Pre-cached Drawing Objects (Prevents GC Thrashing at 60fps) ────────────
//
// Every star is drawn in a *unit* coordinate space and scaled up by the
// transform stack, so each path and gradient below is built exactly once for
// the lifetime of the process. Nothing in the star pipeline allocates per
// frame — critical on a watch, where we render this up to 60 times a second.

/**
 * Radius, in unit space, of the coloured aura.
 *
 * On a magnified star this already covers a large fraction of the watch face,
 * so there is deliberately no second, wider "bloom" layer — stacking one on top
 * washed the black background out to grey once a few stars overlapped.
 */
const val AURA_RADIUS = 4.2f

val cachedSparklePath = Path().apply {
    val r2 = 1f
    val innerR = 0.15f
    moveTo(0f, -r2)
    quadraticTo(0f, -innerR, r2, 0f)
    quadraticTo(0f, innerR, 0f, r2)
    quadraticTo(-innerR, 0f, -r2, 0f)
    quadraticTo(0f, -innerR, 0f, -r2)
    close()
}

val cachedCorePath = Path().apply {
    val r2 = 1f
    val coreR = 0.7f
    val coreInnerR = coreR * 0.12f
    moveTo(0f, -coreR)
    quadraticTo(0f, -coreInnerR, coreR, 0f)
    quadraticTo(0f, coreInnerR, 0f, coreR)
    quadraticTo(-coreInnerR, 0f, -coreR, 0f)
    quadraticTo(0f, -coreInnerR, 0f, -coreR)
    close()
}

/**
 * Diffraction starburst — four long needles on the cardinal axes plus four
 * short diagonals, all baked into a *single* Path.
 *
 * Rendering it as one path means a star costs one `drawPath` instead of
 * eight, which is the difference between this reading as a lens flare and it
 * dropping frames on a watch.
 */
val cachedStarburstPath = Path().apply {
    /** Lays down one lens-shaped needle from the origin out to [length]. */
    fun needle(angleDeg: Float, length: Float, halfWidth: Float) {
        val a = Math.toRadians(angleDeg.toDouble())
        val dx = kotlin.math.cos(a).toFloat()
        val dy = kotlin.math.sin(a).toFloat()
        // Waist control points sit at the midpoint, pushed out perpendicular.
        val mx = dx * length * 0.5f
        val my = dy * length * 0.5f
        val px = -dy * halfWidth
        val py = dx * halfWidth
        moveTo(0f, 0f)
        quadraticTo(mx + px, my + py, dx * length, dy * length)
        quadraticTo(mx - px, my - py, 0f, 0f)
        close()
    }

    // Long cardinal spikes
    needle(0f, 1f, 0.075f)
    needle(90f, 1f, 0.075f)
    needle(180f, 1f, 0.075f)
    needle(270f, 1f, 0.075f)
    // Short diagonal spikes
    needle(45f, 0.42f, 0.05f)
    needle(135f, 0.42f, 0.05f)
    needle(225f, 0.42f, 0.05f)
    needle(315f, 0.42f, 0.05f)
}

private val starAuraCache = HashMap<Color, Brush>()

/**
 * Returns the cached aura gradient for [themeColor], building it on first use.
 *
 * Deliberately avoids `getOrPut` — its lambda captures [themeColor] and would
 * allocate a closure on every star, every frame. The palette is a fixed handful
 * of colours, so this map never grows beyond single digits.
 */
fun starAuraBrush(themeColor: Color): Brush {
    starAuraCache[themeColor]?.let { return it }
    // Weighted hard toward the core. A magnified star's aura spans a large
    // part of the watch face, so a gentle falloff makes ten of them pool into
    // grey fog and erase the night sky behind them. Concentrating the energy
    // in the inner ~30% keeps the background truly black and, by raising
    // contrast, makes each star read as brighter rather than dimmer.
    val created = Brush.radialGradient(
        0.0f to Color.White.copy(alpha = 0.50f),
        0.12f to themeColor.copy(alpha = 0.42f),
        0.30f to themeColor.copy(alpha = 0.12f),
        0.60f to themeColor.copy(alpha = 0.03f),
        1.0f to Color.Transparent,
        center = Offset.Zero,
        radius = AURA_RADIUS
    )
    starAuraCache[themeColor] = created
    return created
}

// ── Parallax Dust Field ────────────────────────────────────────────────────
// A drifting layer of faint motes behind the galaxy. Each mote scrolls at its
// own fraction of the pan offset, so panning reads as depth rather than as a
// flat sheet of stars sliding around.

data class DustMote(
    val x: Float,          // normalised position in the wrap field
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val parallax: Float    // 0 = pinned to the screen, 1 = moves with the stars
)

val GALAXY_DUST: List<DustMote> = run {
    val random = Random(7)
    List(48) {
        DustMote(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radius = 0.4f + random.nextFloat() * 1.3f,
            alpha = 0.10f + random.nextFloat() * 0.35f,
            parallax = 0.18f + random.nextFloat() * 0.42f
        )
    }
}

/**
 * Draws the parallax dust field for a given [pan].
 *
 * Positions wrap modulo a field slightly larger than the screen, which gives an
 * effectively infinite starfield for the cost of 48 tiny circles.
 */
fun DrawScope.drawGalaxyDust(pan: Offset, alpha: Float, twinkle: Float) {
    if (alpha <= 0.01f) return
    val fieldW = size.width * 2.2f
    val fieldH = size.height * 2.2f
    val insetX = (fieldW - size.width) / 2f
    val insetY = (fieldH - size.height) / 2f

    GALAXY_DUST.forEachIndexed { i, mote ->
        var mx = (mote.x * fieldW + pan.x * mote.parallax) % fieldW
        if (mx < 0f) mx += fieldW
        var my = (mote.y * fieldH + pan.y * mote.parallax) % fieldH
        if (my < 0f) my += fieldH

        val shimmer = 0.55f + 0.45f * kotlin.math.sin((twinkle + i * 0.137f) * 2f * Math.PI.toFloat())
        drawCircle(
            color = Color.White,
            radius = mote.radius,
            center = Offset(mx - insetX, my - insetY),
            alpha = mote.alpha * shimmer * alpha
        )
    }
}
// ─────────────────────────────────────────────────────────────────────────

/**
 * Renders a single star.
 *
 * Layered outward-in: a wide bloom, a coloured aura, a two-tone diffraction
 * starburst, the sparkle body, and a hot white core. The heavier layers are
 * culled by [scale], so distant stars in the galaxy stay cheap while the one
 * under the fish-eye lens gets the full treatment.
 *
 * @param twinkle        Continuous phase in turns. Offset it per star so no two
 *                       shimmer in lockstep.
 * @param spikeIntensity Scales the diffraction burst; 0 removes it entirely.
 *                       Ramped up during a star's birth so the spikes bloom out.
 */
fun DrawScope.drawCinematicStar(
    x: Float, y: Float,
    scale: Float,
    alpha: Float,
    baseRadius: Float,
    themeColor: Color,
    pulseAuraScale: Float = 1f,
    twinkle: Float = 0f,
    spikeIntensity: Float = 1f
) {
    if (alpha < 0.01f || scale < 0.01f) return

    val phase = twinkle * 2f * Math.PI.toFloat()
    // Three incommensurate frequencies keep the shimmer from looking like a
    // metronome — brightness, spike length and sway never quite line up.
    val shimmer = 0.82f + 0.18f * kotlin.math.sin(phase)
    val spikeSpan = (0.72f + 0.34f * kotlin.math.sin(phase * 1.37f + 1.1f)) * spikeIntensity
    val sway = kotlin.math.sin(phase * 0.63f) * 7f

    val unit = scale * baseRadius

    withTransform({
        translate(left = x, top = y)
        // Scale by baseRadius here so our static paths are sized perfectly
        scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero)
    }) {
        // ── Aura ─────────────────────────────────────────────────────────
        // Pulsing is applied as a nested transform rather than by rebuilding
        // the gradient, which is what lets the brush stay cached.
        withTransform({ scale(pulseAuraScale, pulseAuraScale, Offset.Zero) }) {
            drawCircle(
                brush = starAuraBrush(themeColor),
                center = Offset.Zero,
                radius = AURA_RADIUS,
                alpha = alpha
            )
        }

        // ── Diffraction spikes ───────────────────────────────────────────
        // Drawn twice: a wider coloured pass for the chromatic bleed, then a
        // shorter white pass for the hot centre line.
        //
        // Kept deliberately short. These are multiplied by the lens
        // magnification, so a value that looks reasonable on a distant star
        // spans the whole watch face once it reaches the centre. Reserved for
        // stars the lens has actually magnified, so the outer field stays as
        // clean points of light.
        if (spikeSpan > 0.02f && scale > 0.9f) {
            val spikeFade = ((scale - 0.9f) / 1.2f).coerceIn(0f, 1f)
            withTransform({
                rotate(degrees = sway, pivot = Offset.Zero)
                scale(2.2f * spikeSpan, 2.2f * spikeSpan, Offset.Zero)
            }) {
                drawPath(
                    path = cachedStarburstPath,
                    color = themeColor,
                    alpha = alpha * 0.42f * spikeFade * shimmer,
                    blendMode = BlendMode.Screen
                )
            }
            withTransform({
                rotate(degrees = sway, pivot = Offset.Zero)
                scale(1.3f * spikeSpan, 1.3f * spikeSpan, Offset.Zero)
            }) {
                drawPath(
                    path = cachedStarburstPath,
                    color = Color.White,
                    alpha = alpha * 0.5f * spikeFade,
                    blendMode = BlendMode.Screen
                )
            }
        }

        // ── Sparkle body ─────────────────────────────────────────────────
        drawPath(
            path = cachedSparklePath,
            color = themeColor,
            alpha = alpha * 0.45f
        )

        // ── Core ─────────────────────────────────────────────────────────
        drawPath(
            path = cachedCorePath,
            color = Color.White,
            alpha = alpha * (0.85f + 0.15f * shimmer)
        )

        // ── Hot centre ───────────────────────────────────────────────────
        // Screen-blended so it blows out to pure white against the core.
        if (scale > 0.9f) {
            drawCircle(
                color = Color.White,
                center = Offset.Zero,
                radius = 0.30f,
                alpha = alpha,
                blendMode = BlendMode.Screen
            )
        }
    }
}