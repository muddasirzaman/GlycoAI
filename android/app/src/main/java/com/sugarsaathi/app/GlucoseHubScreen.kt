package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    onViewReadings: () -> Unit
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
        }
    }
}