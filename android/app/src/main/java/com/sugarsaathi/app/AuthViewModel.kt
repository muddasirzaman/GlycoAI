package com.sugarsaathi.app

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

data class AuthUiState(
    val phoneNumber: String = "",
    val otpSent: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isVerified: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private var verificationId: String? = null

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun isAlreadySignedIn(): Boolean = auth.currentUser != null

    fun sendOtp(activity: Activity, phone: String) {
        if (phone.isBlank()) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
            phoneNumber = phone
        )

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            // Some devices auto-read the SMS and sign in without user typing
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                android.util.Log.e("GLYCOAUTH", "FAILED: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "DEBUG: ${e.message}"
                )
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = id
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    otpSent = true,
                    errorMessage = null
                )
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        android.util.Log.d("GLYCOAUTH", "Sending OTP to: $phone")
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(code: String) {
        val id = verificationId
        if (id == null || code.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        val credential = PhoneAuthProvider.getCredential(id, code)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isVerified = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "That code did not match. Please try again."
                    )
                }
            }
    }

    fun changeNumber() {
        verificationId = null
        _uiState.value = AuthUiState()
    }

    fun signOut() {
        auth.signOut()
        _uiState.value = AuthUiState()
    }

    // Not used while debugging - restore after OTP works
    @Suppress("unused")
    private fun friendlyError(raw: String?): String = when {
        raw?.contains("invalid", ignoreCase = true) == true ->
            "That phone number doesn't look right. Please check and try again."
        raw?.contains("network", ignoreCase = true) == true ->
            "No internet connection. Please check and try again."
        raw?.contains("quota", ignoreCase = true) == true ->
            "Too many attempts. Please try again later."
        else -> "Couldn't send the code. Please try again."
    }
}