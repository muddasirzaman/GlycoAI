package com.sugarsaathi.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var historyRepo: ChatHistoryRepository? = null

    private var profileRepo: ProfileRepository? = null
    fun initHistory(context: Context) {
        historyRepo = ChatHistoryRepository(context)


    }

    fun initProfileRepo(context: Context) {
        profileRepo = ProfileRepository(context)
    }

    fun saveCurrentSession() {
        val messages = _uiState.value.messages
        if (messages.isNotEmpty()) {
            historyRepo?.saveSession(messages)
        }
    }

    fun extractAndSaveFacts(profile: UserProfileData) {
        val messages = _uiState.value.messages
        if (messages.size < 2) return

        viewModelScope.launch {
            try {
                val conversation = messages.map {
                    mapOf("role" to it.role, "content" to it.content)
                }

                val result = NetworkModule.apiService.extractFacts(
                    ExtractRequest(
                        conversation = conversation,
                        existing_facts = profile.knownFacts
                    )
                )

                profileRepo?.saveFacts(result.facts)

            } catch (e: Exception) {
                // Extraction is best-effort; never disrupt the user
                println("Fact extraction failed: ${e.message}")
            }
        }
    }

    private var lastFailedRetry: (() -> Unit)? = null

    fun retryLastMessage() {
        val retry = lastFailedRetry ?: return
        lastFailedRetry = null
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        retry()
    }

    fun sendMessage(
        userText: String,
        profile: UserProfileData,
        glucoseSummary: String? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
        documentBase64: String? = null,
        documentMimeType: String? = null,
        documentName: String? = null
    ) {
        if (userText.isBlank() && imageBase64 == null && documentBase64 == null) return
        val userMessage = Message(role = "user", content = userText)
        val currentMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(messages = currentMessages, isLoading = true, errorMessage = null)
        performSend(currentMessages, userText, profile, glucoseSummary, imageBase64, imageMimeType, documentBase64, documentMimeType, documentName)
    }

    private fun performSend(
        currentMessages: List<Message>, userText: String, profile: UserProfileData,
        glucoseSummary: String?, imageBase64: String?, imageMimeType: String?,
        documentBase64: String?, documentMimeType: String?, documentName: String?
    ) {
        viewModelScope.launch {
            try {
                val history = currentMessages.dropLast(1).map { mapOf("role" to it.role, "content" to it.content) }
                val profileData = profile.toApiProfileData(glucoseSummary)
                val request = ChatRequest(
                    message = userText, profile = profileData, conversation_history = history,
                    image_data = imageBase64, image_type = imageMimeType,
                    document_data = documentBase64, document_type = documentMimeType, document_name = documentName
                )
                val response = NetworkModule.apiService.sendMessage(request)
                val aiMessage = Message(role = "assistant", content = response.response)
                val updatedMessages = currentMessages + aiMessage
                lastFailedRetry = null
                _uiState.value = _uiState.value.copy(messages = updatedMessages, isLoading = false)
                historyRepo?.saveSession(updatedMessages)
            } catch (e: Exception) {
                e.printStackTrace()
                lastFailedRetry = { performSend(currentMessages, userText, profile, glucoseSummary, imageBase64, imageMimeType, documentBase64, documentMimeType, documentName) }
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = friendlyNetworkMessage(e))
            }
        }
    }
}

