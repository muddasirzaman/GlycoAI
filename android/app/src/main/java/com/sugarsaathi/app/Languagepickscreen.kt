package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LanguagePickScreen(onLanguageChosen: (String) -> Unit) {
    // Track the tap so the button reflects the choice. Previously both buttons
    // passed isSelected = false, so neither ever highlighted - the screen gave
    // no feedback at all before navigating away.
    var picked by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Renders outside the Scaffold, so with enableEdgeToEdge() it gets
            // no automatic insets.
            .systemBarsPadding()
            .padding(28.dp),
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

        SelectableButton(
            text = stringResource(R.string.english),
            isSelected = picked == "en"
        ) {
            picked = "en"
            onLanguageChosen("en")
        }
        Spacer(Modifier.height(12.dp))
        SelectableButton(
            text = "اردو",
            isSelected = picked == "ur"
        ) {
            picked = "ur"
            onLanguageChosen("ur")
        }

        Spacer(Modifier.weight(1f))
        OrganizationLogos()
    }
}