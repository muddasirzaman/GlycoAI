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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onVerified: () -> Unit
) {
    val state by authViewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSignedIn) {
        if (state.isSignedIn) onVerified()
    }

    if (state.needsEmailVerification) {
        EmailVerificationScreen(authViewModel = authViewModel, state = state)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("📧", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))

        Text(
            if (isSignUpMode) "Create Your Account" else "Welcome Back",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isSignUpMode) "Sign up to save your data across devices" else "Log in to continue",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                if (isSignUpMode) authViewModel.signUp(email, password)
                else authViewModel.signIn(email, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
            enabled = email.isNotBlank() && password.length >= 6 && !state.isLoading
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text(if (isSignUpMode) "Create Account" else "Log In", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = {
            isSignUpMode = !isSignUpMode
            authViewModel.clearError()
        }) {
            Text(
                if (isSignUpMode) "Already have an account? Log in" else "Don't have an account? Sign up",
                color = TealGreen
            )
        }
    }
}

@Composable
fun EmailVerificationScreen(authViewModel: AuthViewModel, state: AuthUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✉️", fontSize = 56.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            "Check Your Email",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "We've sent a verification link to your email. Please tap it, then come back and continue.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        if (state.verificationEmailSent) {
            Spacer(Modifier.height(8.dp))
            Text("✓ Email sent", fontSize = 13.sp, color = TealGreen)
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { authViewModel.checkIfVerified() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
            enabled = !state.isLoading
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("I've Verified — Continue", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { authViewModel.resendVerificationEmail() }) {
            Text("Resend Email", color = TealGreen)
        }
    }
}