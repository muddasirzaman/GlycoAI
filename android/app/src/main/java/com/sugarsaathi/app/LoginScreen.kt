package com.sugarsaathi.app

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onVerified: () -> Unit
) {
    val state by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }

    // Move on once Firebase confirms the sign-in
    LaunchedEffect(state.isVerified) {
        if (state.isVerified) onVerified()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("📱", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))

        if (!state.otpSent) {
            // ── Phone number entry ──
            Text(
                stringResource(R.string.login_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.login_subtitle),
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
                label = { Text(stringResource(R.string.phone_number)) },
                placeholder = { Text(stringResource(R.string.phone_hint)) },
                prefix = { Text("+92 ") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            state.errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    activity?.let {
                        authViewModel.sendOtp(it, "+92$phone")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                enabled = phone.length >= 10 && !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(stringResource(R.string.send_code), fontSize = 16.sp)
                }
            }

        } else {
            // ── OTP entry ──
            Text(
                stringResource(R.string.otp_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.otp_subtitle, state.phoneNumber),
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.otp_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            state.errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { authViewModel.verifyOtp(otp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                enabled = otp.length == 6 && !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(stringResource(R.string.verify), fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { authViewModel.changeNumber() }) {
                Text(stringResource(R.string.change_number), color = TealGreen)
            }
        }
    }
}