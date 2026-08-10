package com.sugarsaathi.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class EmergencySign(
    val emoji: String,
    val titleRes: Int,
    val bodyRes: Int
)

private val EMERGENCY_SIGNS = listOf(
    EmergencySign("🍬", R.string.em_low_title, R.string.em_low_body),
    EmergencySign("💧", R.string.em_high_title, R.string.em_high_body),
    EmergencySign("🌀", R.string.em_confusion_title, R.string.em_confusion_body),
    EmergencySign("😴", R.string.em_unconscious_title, R.string.em_unconscious_body),
    EmergencySign("😮‍💨", R.string.em_breathing_title, R.string.em_breathing_body),
    EmergencySign("🥵", R.string.em_weakness_title, R.string.em_weakness_body),
    EmergencySign("🩹", R.string.em_other_title, R.string.em_other_body),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen() {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.emergency_title), fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFFD32F2F),
                titleContentColor = Color.White
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Call button always at the very top - the one action that matters most.
            item {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1122"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.emergency_call_button),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.emergency_intro),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                Text(
                    stringResource(R.string.emergency_when_heading),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            items(EMERGENCY_SIGNS) { sign ->
                EmergencySignCard(sign)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF6C00))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.emergency_disclaimer),
                            fontSize = 12.sp,
                            color = Color(0xFF5D4037),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencySignCard(sign: EmergencySign) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Text(sign.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    stringResource(sign.titleRes),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB71C1C)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(sign.bodyRes),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}