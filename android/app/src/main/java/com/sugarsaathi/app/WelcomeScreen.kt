package com.sugarsaathi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource

@Composable
fun WelcomeScreen(
    profile: UserProfileData,
    onChangeInfo: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onGlucoseHub: () -> Unit = {},
    chatViewModel: ChatViewModel = viewModel()
) {
    var goToChat by remember { mutableStateOf(false) }

    if (goToChat) {
        ChatScreen(
            userProfile = profile,
            onHistoryClick = onHistoryClick,
            chatViewModel = chatViewModel
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D7A5F),
                        Color(0xFF1D9E75),
                        Color(0xFF1A8A6B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Text(text = "👋", fontSize = 56.sp)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.welcome_back),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
            Text(
                text = profile.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "GlycoAI: Your Daily Diabetes Companion",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Continue button
            Button(
                onClick = { goToChat = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Continue →",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D9E75)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Change information button
            OutlinedButton(
                onClick = onChangeInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "Change Information",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Blood sugar tracker button
            Button(
                onClick = onGlucoseHub,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "🩸  Blood Sugar Tracker",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D5A44)
                )
            }
        }
    }
}