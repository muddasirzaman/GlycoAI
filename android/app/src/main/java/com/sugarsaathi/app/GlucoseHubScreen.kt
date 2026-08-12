package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseHubScreen(
    onBack: () -> Unit,
    onAddReading: () -> Unit,
    onViewReadings: () -> Unit,
    onReminders: () -> Unit,
    // Opens the structured-medications list. Handled in MainActivity, wired
    // through MainTabScreen, and surfaced as the outlined button below.
    onMedications: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.tracker_title), fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TealGreen,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🩸", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.track_blood_sugar),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.log_and_progress),
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onAddReading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    stringResource(R.string.add_reading),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D5A44)
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onViewReadings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    stringResource(R.string.view_my_readings),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))

            // Outlined rather than filled: reminders are set up occasionally,
            // whereas the two buttons above are used every day. It should be
            // easy to find without competing with them.
            OutlinedButton(
                onClick = onReminders,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TealGreen),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, TealGreen)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.reminders_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            // My Medicines. Same outlined treatment as reminders - it's set up
            // occasionally, not every day, so it sits alongside reminders
            // without competing with the two daily-use buttons above.
            OutlinedButton(
                onClick = onMedications,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TealGreen),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, TealGreen)
            ) {
                Icon(Icons.Default.MedicalServices, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.meds_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}