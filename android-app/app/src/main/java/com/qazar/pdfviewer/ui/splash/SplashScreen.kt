package com.qazar.pdfviewer.ui.splash

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.R
import com.qazar.pdfviewer.theme.GoogleSansTextFamily
import kotlinx.coroutines.delay

/**
 * Centered, lightweight, lag-free Splash Screen for Qazar PDF Viewer.
 * - Centered original logo
 * - Flowing crimson-red progress bar
 * - Subtly changing status text below ("Initializing...", "Loading Engine...", "Opening...")
 * - Compact typography and seamless 120fps hardware rendering
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressAnim = remember { Animatable(0f) }
    var statusText by remember { mutableStateOf("Initializing...") }

    val infiniteTransition = rememberInfiniteTransition(label = "SplashTransition")
    val logoPulse by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoPulse"
    )

    LaunchedEffect(Unit) {
        delay(250)
        statusText = "Loading Engine..."
        progressAnim.animateTo(
            targetValue = 0.55f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
        delay(200)
        statusText = "Opening..."
        progressAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 600, easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f))
        )
        delay(120)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C12)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Centered Original Logo
            Image(
                painter = painterResource(id = R.drawable.ic_qazar_logo),
                contentDescription = "Qazar Logo",
                modifier = Modifier
                    .size(92.dp)
                    .scale(logoPulse)
            )

            Spacer(modifier = Modifier.height(26.dp))

            // Flowing Progress Bar
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x30DC2626))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressAnim.value)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFDC2626),
                                    Color(0xFFEF4444),
                                    Color(0xFFFF6B6B)
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtly changing status text below progress bar with smaller elegant font
            AnimatedContent(
                targetState = statusText,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "StatusTextAnim"
            ) { text ->
                Text(
                    text = text,
                    fontSize = 12.sp,
                    fontFamily = GoogleSansTextFamily,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}
