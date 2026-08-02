package com.sugarsaathi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TipsUiState(
    val tips: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class TipsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TipsUiState())
    val uiState: StateFlow<TipsUiState> = _uiState

    // Remembers which profile the current tips belong to, so switching
    // tabs back and forth doesn't re-call the AI every single time.
    private var lastProfileSnapshot: UserProfileData? = null

    fun loadTipsIfNeeded(profile: UserProfileData) {
        if (_uiState.value.tips.isNotEmpty() && lastProfileSnapshot == profile) return
        fetchTips(profile)
    }

    fun refreshTips(profile: UserProfileData) {
        fetchTips(profile)
    }

    private fun fetchTips(profile: UserProfileData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // NOTE: this assumes UserProfileData can be converted to the
                // API's ProfileData the same way ChatViewModel.kt already
                // does when it sends chat messages. If your project uses a
                // differently named conversion (e.g. profile.toApiProfile()),
                // swap it in on the line below — everything else stays the same.
                val response = NetworkModule.apiService.getTips(
                    TipsRequest(profile = profile.toApiProfileData())
                )
                lastProfileSnapshot = profile
                _uiState.value = TipsUiState(tips = response.tips, isLoading = false)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Couldn't load tips. Check your connection and try again."
                )
            }
        }
    }
}

data class TipsRequest(
    val profile: ProfileData
)

data class TipsResponse(
    val tips: List<String>
)