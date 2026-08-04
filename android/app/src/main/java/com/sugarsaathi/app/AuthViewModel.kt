package com.sugarsaathi.app

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSignedIn: Boolean = false,
    val needsEmailVerification: Boolean = false,
    val verificationEmailSent: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    // Only "already signed in" if the account exists AND the email was verified.
    fun isAlreadySignedIn(): Boolean =
        auth.currentUser != null && auth.currentUser?.isEmailVerified == true

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                needsEmailVerification = true,
                                verificationEmailSent = true
                            )
                        }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = friendlyError(task.exception)
                    )
                }
            }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    if (auth.currentUser?.isEmailVerified == true) {
                        _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            needsEmailVerification = true
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = friendlyError(task.exception)
                    )
                }
            }
    }

    fun resendVerificationEmail() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        auth.currentUser?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isLoading = false, verificationEmailSent = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Couldn't resend the email. Please try again."
                    )
                }
            }
    }

    // Called when the user taps "I've verified" — re-checks the real status with Firebase.
    fun checkIfVerified() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        auth.currentUser?.reload()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful && auth.currentUser?.isEmailVerified == true) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedIn = true,
                        needsEmailVerification = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Still not verified. Please check your email and tap the link first."
                    )
                }
            }
    }

    fun signOut() {
        auth.signOut()
        _uiState.value = AuthUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun friendlyError(e: Exception?): String = when (e) {
        is FirebaseAuthWeakPasswordException ->
            "Password is too weak. Use at least 6 characters."
        is FirebaseAuthInvalidCredentialsException ->
            "That email address doesn't look right. Please check and try again."
        is FirebaseAuthUserCollisionException ->
            "An account with this email already exists. Try logging in instead."
        is FirebaseAuthInvalidUserException ->
            "No account found with this email. Try creating one instead."
        else -> when {
            e?.message?.contains("network", ignoreCase = true) == true ->
                "No internet connection. Please check and try again."
            e?.message?.contains("password is invalid", ignoreCase = true) == true ->
                "Incorrect password. Please try again."
            else -> "Something went wrong. Please try again."
        }
    }
}