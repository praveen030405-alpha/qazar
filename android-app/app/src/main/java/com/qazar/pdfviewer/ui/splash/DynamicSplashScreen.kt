package com.qazar.pdfviewer.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun DynamicSplashScreen(onAnimationComplete: () -> Unit) {
    val pathProgress = remember { Animatable(0f) }
    val shimmerAnim = remember { Animatable(-350f) }

    LaunchedEffect(Unit) {
        // 1. Kinetic liquid trace of the Q outline (400ms)
        this.launch {
            pathProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
        delay(220)

        // 2. First complete shimmer sweep seamlessly from left to right inside the words
        shimmerAnim.animateTo(
            targetValue = 1200f,
            animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
        )

        // 3. Reset to left and perform second complete left-to-right shimmer sweep
        shimmerAnim.snapTo(-350f)
        shimmerAnim.animateTo(
            targetValue = 1200f,
            animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
        )

        delay(120)
        // 4. Smoothly transition to HomeScreen right after second shimmer completes
        onAnimationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // Pure black background
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onAnimationComplete // Tap-to-skip
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2 - 100f)
            
            // Get thick font outline path for "Q"
            val textPaint = android.graphics.Paint().apply {
                textSize = 500f
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val androidPath = android.graphics.Path()
            textPaint.getTextPath("Q", 0, 1, center.x, center.y + (textPaint.textSize / 3f), androidPath)
            
            // Stretch horizontally so it's not vertical and narrow
            val matrix = android.graphics.Matrix()
            matrix.postScale(1.25f, 1.0f, center.x, center.y)
            androidPath.transform(matrix)
            
            val qPath = androidPath.asComposePath()

            val pathMeasure = PathMeasure()
            pathMeasure.setPath(qPath, false)
            val pathLength = pathMeasure.length
            val p = pathProgress.value

            val strokeStyle = Stroke(
                width = 4f, // Slimmer base line
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    intervals = floatArrayOf(pathLength, pathLength),
                    phase = pathLength * (1f - p)
                )
            )

            // Core Clean Line (No glow, crisp and sharp)
            drawPath(
                path = qPath,
                color = Color(0xFFEF4444), // Solid Precision Crimson Red
                style = strokeStyle
            )
        }
        
        // PDF Workspace and Tagline right below the Q
        androidx.compose.animation.AnimatedVisibility(
            visible = pathProgress.value > 0.5f,
            enter = androidx.compose.animation.slideInHorizontally(
                initialOffsetX = { -50 },
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            ) + androidx.compose.animation.fadeIn(tween(400)),
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.Center)
                .padding(top = 200.dp)
        ) {
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0x55FFFFFF),
                    Color(0x88FFFFFF),
                    Color(0xFFFFFFFF),
                    Color(0x88FFFFFF),
                    Color(0x55FFFFFF)
                ),
                start = Offset(shimmerAnim.value, 0f),
                end = Offset(shimmerAnim.value + 260f, 0f)
            )

            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Text(
                    text = "PDF Workspace",
                    style = androidx.compose.ui.text.TextStyle(
                        brush = shimmerBrush,
                        fontSize = 18.sp,
                        fontFamily = com.qazar.pdfviewer.theme.GoogleSansFamily,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                androidx.compose.material3.Text(
                    text = "Private, Secure & Fully Offline",
                    style = androidx.compose.ui.text.TextStyle(
                        brush = shimmerBrush,
                        fontSize = 13.sp,
                        fontFamily = com.qazar.pdfviewer.theme.GoogleSansTextFamily,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }

        // Powered by Qazar subtly rising from bottom
        androidx.compose.animation.AnimatedVisibility(
            visible = pathProgress.value > 0.5f,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { 50 },
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            ) + androidx.compose.animation.fadeIn(tween(400)),
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        ) {
            androidx.compose.material3.Text(
                text = "Powered by Qazar",
                color = Color(0x66FFFFFF), // Tiny but visible
                fontSize = 12.sp,
                fontFamily = com.qazar.pdfviewer.theme.GoogleSansTextFamily
            )
        }
    }
}
