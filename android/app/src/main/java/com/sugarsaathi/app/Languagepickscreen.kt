package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LanguagePickScreen(onLanguageChosen: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("👋", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onboarding_welcome_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.select_language),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(20.dp))

        SelectableButton("English", false) { onLanguageChosen("en") }
        Spacer(Modifier.height(12.dp))
        SelectableButton("اردو", false) { onLanguageChosen("ur") }
    }
}