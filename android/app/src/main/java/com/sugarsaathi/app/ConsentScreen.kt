package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/**
 * Informed-consent screen, shown once during onboarding.
 *
 * Sits after language selection so it is read in the user's own language, and
 * before any medical questions so nothing personal is collected before the user
 * knows what this app is.
 *
 * The checkbox is deliberate: a button alone records only that someone tapped
 * past a screen. A separate tick is a clearer record that the user affirmed
 * they understood, which matters for a health app.
 */
@Composable
fun ConsentScreen(
    onAccept: () -> Unit,
    onBack: () -> Unit
) {
    var accepted by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text("🩺", fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.consent_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.consent_intro),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5EE)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ConsentPoint("📚", stringResource(R.string.consent_point_education))
                    ConsentPoint("🚫", stringResource(R.string.consent_point_diagnose))
                    ConsentPoint("💊", stringResource(R.string.consent_point_doses))
                    ConsentPoint("👨‍⚕️", stringResource(R.string.consent_point_doctor))
                    ConsentPoint("🚨", stringResource(R.string.consent_point_emergency))
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.consent_privacy_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.consent_privacy_body),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF616161)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = { accepted = it },
                    colors = CheckboxDefaults.colors(checkedColor = TealGreen)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.consent_checkbox),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.back_button)) }

            Button(
                onClick = onAccept,
                modifier = Modifier.weight(2f),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                // Cannot proceed without ticking. There is no skip.
                enabled = accepted
            ) { Text(stringResource(R.string.consent_agree_button)) }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ConsentPoint(emoji: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(emoji, fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = Color(0xFF1B4B3C)
        )
    }
}