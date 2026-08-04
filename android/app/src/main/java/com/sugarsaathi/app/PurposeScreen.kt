package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PurposeScreen(onPurposeSelected: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.purpose_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.purpose_subtitle),
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        PurposeCard(
            emoji = "👤",
            title = stringResource(R.string.purpose_patient),
            desc = stringResource(R.string.purpose_patient_desc),
            onClick = { onPurposeSelected("patient") }
        )
        PurposeCard(
            emoji = "🎓",
            title = stringResource(R.string.purpose_educational),
            desc = stringResource(R.string.purpose_educational_desc),
            onClick = { onPurposeSelected("educational") }
        )
        PurposeCard(
            emoji = "👨‍⚕️",
            title = stringResource(R.string.purpose_professional),
            desc = stringResource(R.string.purpose_professional_desc),
            onClick = { onPurposeSelected("professional") }
        )
        PurposeCard(
            emoji = "👨‍👩‍👧",
            title = stringResource(R.string.purpose_caregiver),
            desc = stringResource(R.string.purpose_caregiver_desc),
            onClick = { onPurposeSelected("caregiver") }
        )
    }
}

@Composable
private fun PurposeCard(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5EE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D5A44))
                Text(desc, fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}