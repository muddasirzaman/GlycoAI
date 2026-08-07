package com.sugarsaathi.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {

    val alpha = remember { Animatable(0f) }
    val loadingProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(800))
        loadingProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
        )
        delay(300)
        onFinished()
    }

    val bgBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0A7AFF),
            Color(0xFF1D9E75),
            Color(0xFFF5A623)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = bgBrush)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Splash renders outside the Scaffold, so with enableEdgeToEdge()
                // it receives no automatic insets. Without this the title sits
                // under the clock and the footer under the gesture bar.
                .systemBarsPadding()
        ) {
            // The wheel is the only element that can flex, so size it from BOTH
            // dimensions. Capping at 30% of height is what keeps the screen from
            // overflowing on short devices now that the logo strip is here too.
            val wheelSize = minOf(maxWidth * 0.62f, maxHeight * 0.30f)
                .coerceIn(140.dp, 280.dp)

            val compact = maxHeight < 700.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = alpha.value)
                    .padding(horizontal = 24.dp)
            ) {

                Spacer(modifier = Modifier.height(if (compact) 20.dp else 44.dp))

                // ── App name ──
                Text(
                    text = "GlycoAI",
                    fontSize = if (compact) 32.sp else 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "گلائیکو اے آئی",
                    fontSize = if (compact) 16.sp else 18.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // ── Divider ──
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.25f))
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your Daily Diabetes Companion",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color.White.copy(alpha = 0.78f),
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )

                // Flexible gap - absorbs leftover space on tall screens and
                // collapses first on short ones.
                Spacer(modifier = Modifier.weight(1f))

                // ── Wellness Wheel Image ──
                Image(
                    painter = painterResource(id = R.drawable.splash_screen_center),
                    contentDescription = "Daily Wellness Hub Wheel",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(wheelSize)
                )

                Spacer(modifier = Modifier.weight(1f))

                // ── Loading text ──
                Text(
                    text = "LOADING…",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "لوڈ ہو رہا ہے…",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Loading bar ──
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .height(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(loadingProgress.value)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF0A7AFF),
                                        Color(0xFF1D9E75),
                                        Color(0xFFF5A623)
                                    )
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(if (compact) 16.dp else 24.dp))

                // ── Institutional branding ──
                // Sits below the app identity and the loading indicator, so it
                // supports the brand rather than competing with it.
                OrganizationLogos(
                    minLogoSize = 36.dp,
                    maxLogoSize = if (compact) 44.dp else 52.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Footer ──
                Text(
                    text = "Made by Ghulam Mustafa",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}