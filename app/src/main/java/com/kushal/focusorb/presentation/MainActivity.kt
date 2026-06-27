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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Text
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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val timings = longArrayOf(
                            0, 
                            50, 50, 50, 50, 50, 50, // Inversion building (0-300ms)
                            0, 200, // Burst pop (300-500)
                            300, // wait till 800ms
                            100, 100, 100, 100, 100, 100, 100, 100 // Genesis gentle rumble
                        )
                        val amplitudes = intArrayOf(
                            0,
                            30, 0, 70, 0, 150, 0, // Building up
                            0, 255, // Burst pop
                            0, // pause
                            50, 0, 40, 0, 30, 0, 20, 0 // gentle fade out
                        )
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        val pattern = longArrayOf(0, 50, 50, 50, 50, 50, 50, 0, 200, 300, 100, 100, 100, 100, 100, 100)
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
        Box(modifier = Modifier.fillMaxSize()) {
            GalaxyView(
                earnedStars = uiState.earnedStars,
                initialPan = targetGalaxyPan
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color.DarkGray.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { 
                            isGalaxyView = false
                            if (uiState.sessionState == SessionState.COMPLETED) {
                                viewModel.resetSession()
                            }
                        })
                    }
            ) {
                Text(
                    text = "Close",
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.takeDamage()
                        },
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
                onNextDuration = { viewModel.selectNextDuration() },
                onPrevDuration = { viewModel.selectPreviousDuration() },
                onTransitionToGalaxy = { finalPan -> 
                    targetGalaxyPan = finalPan
                    isGalaxyView = true 
                }
            )
            
            if (uiState.sessionState == SessionState.IDLE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.DarkGray.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { isGalaxyView = true })
                        }
                ) {
                    Text(
                        text = "View Galaxy",
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

data class Particle(val angle: Float, val speed: Float, val sizeMultiplier: Float)
data class BurstParticle(val angle: Float, val speed: Float, val radius: Float, val alphaDecay: Float)
data class Fracture(val mainPath: Path, val branches: List<Path>)

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
    timeRemainingMs: Long = 40 * 60 * 1000L, 
    sessionState: SessionState = SessionState.IDLE, 
    orbHealth: Int = 3,
    currentDuration: SessionDuration = SessionDuration(45, StarSize.LARGE),
    earnedStars: List<StarSize> = emptyList(),
    onNextDuration: () -> Unit = {},
    onPrevDuration: () -> Unit = {},
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
        List(18) {
            BurstParticle(
                angle = random.nextFloat() * 2 * Math.PI.toFloat(),
                speed = 20f + random.nextFloat() * 60f,
                radius = 2f + random.nextFloat() * 6f,
                alphaDecay = 1.0f + random.nextFloat() * 2.0f
            )
        }
    }
    
    // Star Genesis Scale Physics (delayed to start at 0.2f, peaks at 3.5f for Majestic Bask)
    val starScale by animateFloatAsState(
        targetValue = if (isCompleted && supernovaTime.value >= 0.2f) 3.5f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
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
                val coreScale = if (!isCompleted) {
                    breathingAnim.value
                } else {
                    androidx.compose.ui.util.lerp(breathingAnim.value, 0.2f, collapseProgress)
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
                
                // Stage 2: The Burst Particle Emitter (0.2f to 0.8f)
                if (isCompleted && t >= 0.2f && t < 0.8f) {
                    val pProgress = ((t - 0.2f) / 0.6f).coerceIn(0f, 1f)
                    val easedProgress = FastOutSlowInEasing.transform(pProgress)
                    val targetStarIndex = kotlin.math.max(0, earnedStars.size - 1)
                    val targetStarColor = getStarThemeColor(targetStarIndex, currentDuration.starSize)
                    
                    burstParticles.forEach { particle ->
                        // Particles rapidly decelerate and fade
                        val currentAlpha = (1f - (pProgress * particle.alphaDecay)).coerceIn(0f, 1f)
                        
                        if (currentAlpha > 0f) {
                            val distance = baseRadius * 0.2f + particle.speed * easedProgress * 3f
                            val px = center.x + kotlin.math.cos(particle.angle.toDouble()).toFloat() * distance
                            val py = center.y + kotlin.math.sin(particle.angle.toDouble()).toFloat() * distance
                            
                            // Color transition from hot white to target star color as they expand
                            val pColor = lerp(Color.White, targetStarColor, pProgress)
                            
                            drawCircle(
                                color = pColor.copy(alpha = currentAlpha),
                                radius = particle.radius,
                                center = Offset(px, py)
                            )
                        }
                    }
                }
                
                // Stage 3 & 4: The Bask (0.4f to 0.6f) & The Voyage & Reveal (0.6f to 1.0f)
                if (isCompleted && starScale > 0.01f) {
                    val targetStarIndex = kotlin.math.max(0, earnedStars.size - 1)
                    val targetStarColor = getStarThemeColor(targetStarIndex, currentDuration.starSize)
                    val baseStarRadius = with(density) { (currentDuration.starSize.sizeDp / 2).dp.toPx() }
                    
                    val voyageProgress = ((t - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    val easedVoyage = FastOutSlowInEasing.transform(voyageProgress)
                    
                    // Hex Math for target slot (already calculated in finalGalaxyPan, but let's calculate current pan)
                    val hexSpacing = size.width * 0.085f
                    val currentPanX = androidx.compose.ui.util.lerp(0f, finalGalaxyPan.x, easedVoyage)
                    val currentPanY = androidx.compose.ui.util.lerp(0f, finalGalaxyPan.y, easedVoyage)
                    
                    val maxRadius = kotlin.math.max(size.width, size.height)
                    val lensRadius = maxRadius
                    
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
                            
                            drawCinematicStar(
                                x = hFinalX, y = hFinalY,
                                scale = hMag,
                                alpha = hTargetAlpha * voyageProgress, // Fade in mapped to voyage
                                baseRadius = hBaseRadius,
                                themeColor = hColor,
                                pulseAuraScale = 1f
                            )
                        }
                    }
                    
                    // Draw the New Star
                    // It is perfectly centered on screen, so it always sits at the peak of the fisheye lens
                    val maxBonusScale = 3.2f
                    val baseTargetScale = 0.35f
                    val targetMagnification = baseTargetScale + maxBonusScale * 1f // steepCurve is 1 at center
                    val currentScale = androidx.compose.ui.util.lerp(starScale, targetMagnification, easedVoyage)
                    
                    drawCinematicStar(
                        x = center.x, y = center.y,
                        scale = currentScale,
                        alpha = 1f, // The new star is always fully visible
                        baseRadius = baseStarRadius,
                        themeColor = targetStarColor,
                        pulseAuraScale = auraPulseScale
                    )
                }
                
                // Draw Crystalline Fractures if damaged
                if (orbHealth < 3 && fractures != null) {
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
                color = Color.White.copy(alpha = progressVal),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (isIdle) {
            // Duration Picker
            var dragAccumulator by remember { mutableStateOf(0f) }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { dragAccumulator = 0f },
                            onDragCancel = { dragAccumulator = 0f }
                        ) { change, dragAmount -> 
                            change.consume()
                            dragAccumulator += dragAmount
                            if (dragAccumulator > 80f) {
                                onPrevDuration()
                                dragAccumulator = 0f
                            } else if (dragAccumulator < -80f) {
                                onNextDuration()
                                dragAccumulator = 0f
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.animation.Crossfade(targetState = currentDuration.minutes, animationSpec = tween(300)) { minutes ->
                    Text(
                        text = "$minutes Min",
                        color = Color.White.copy(alpha = 0.9f),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    )
                }
                Text(
                    text = "Swipe to change • Tap to begin",
                    color = Color.White.copy(alpha = 0.5f),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light,
                    fontSize = 10.sp,
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
                color = Color.White.copy(alpha = 0.7f),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                fontSize = 18.sp,
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
fun GalaxyView(earnedStars: List<StarSize>, initialPan: Offset = Offset.Zero) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    var pan by remember { mutableStateOf(initialPan) }
    val flingAnimatable = remember { Animatable(initialPan, Offset.VectorConverter) }
    var flingJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }
    
    val coordinates = remember(earnedStars.size) { generateGalaxyLayout(earnedStars.size) }

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
                                pan += change // Synchronous update! Perfectly smooth 60fps.
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
                                pan = this.value
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
        
        earnedStars.forEachIndexed { index, starSize ->
            val (q, r) = coordinates[index]
            
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
            val alpha = (1f - normalizedDist).coerceIn(0.3f, 1f)
            
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
            
            // 6. Draw with physical scaling
            drawCinematicStar(
                x = starScreenX,
                y = starScreenY,
                scale = magnification,
                alpha = alpha,
                baseRadius = baseStarRadius,
                themeColor = themeColor
            )
        }
    }
}

// ── Pre-cached Drawing Objects (Prevents GC Thrashing at 60fps) ────────────
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

val cachedAuraBrushes = mutableMapOf<Color, Brush>()
fun getAuraBrush(themeColor: Color, radius: Float): Brush {
    // We only cache the standard GalaxyView radius to prevent memory leaks from the breathing animation
    if (radius == 4.2f) {
        return cachedAuraBrushes.getOrPut(themeColor) {
            Brush.radialGradient(
                0.0f to themeColor.copy(alpha = 0.4f),
                0.5f to themeColor.copy(alpha = 0.15f),
                1.0f to Color.Transparent,
                center = Offset.Zero,
                radius = 4.2f
            )
        }
    }
    
    // For the main breathing star, we create it dynamically (only 1 per frame, so it's cheap)
    return Brush.radialGradient(
        0.0f to themeColor.copy(alpha = 0.4f),
        0.5f to themeColor.copy(alpha = 0.15f),
        1.0f to Color.Transparent,
        center = Offset.Zero,
        radius = radius
    )
}
// ─────────────────────────────────────────────────────────────────────────

fun DrawScope.drawCinematicStar(
    x: Float, y: Float,
    scale: Float,
    alpha: Float,
    baseRadius: Float,
    themeColor: Color,
    pulseAuraScale: Float = 1f
) {
    if (alpha < 0.01f || scale < 0.01f) return
    withTransform({
        translate(left = x, top = y)
        // Scale by baseRadius here so our static paths are sized perfectly
        scale(scaleX = scale * baseRadius, scaleY = scale * baseRadius, pivot = Offset.Zero)
    }) {
        // Aura
        val auraRadius = 4.2f * pulseAuraScale
        drawCircle(
            brush = getAuraBrush(themeColor, auraRadius),
            center = Offset.Zero,
            radius = auraRadius,
            alpha = alpha
        )
        
        // Sparkle
        drawPath(
            path = cachedSparklePath,
            color = themeColor,
            alpha = alpha * 0.4f
        )
        
        // Core
        drawPath(
            path = cachedCorePath,
            color = Color.White,
            alpha = alpha
        )
    }
}