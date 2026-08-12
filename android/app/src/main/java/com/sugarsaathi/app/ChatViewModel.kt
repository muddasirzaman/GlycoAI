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
    val errorMessage: String? = null,
    // Tappable options shown under the last AI message. When the assistant is
    // asking a clarifying question these are likely answers; otherwise they are
    // suggested follow-up questions. Cleared as soon as the user sends anything.
    val quickReplies: List<String> = emptyList(),
    // True when the assistant asked instead of advising - lets the UI hint that
    // an answer is expected.
    val awaitingContext: Boolean = false
)


class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var historyRepo: ChatHistoryRepository? = null

    private var profileRepo: ProfileRepository? = null

    // Held so performSend can pull the structured-medication summary out of the
    // Room database without the UI having to know medications exist. Set in
    // initProfileRepo, which MainActivity already calls once on startup.
    private var appContext: Context? = null

    fun initHistory(context: Context) {
        historyRepo = ChatHistoryRepository(context)
    }

    fun initProfileRepo(context: Context) {
        profileRepo = ProfileRepository(context)
        // applicationContext: this outlives any single screen and must not
        // retain an Activity. AppDatabase.getInstance already stores the
        // application context, so this is consistent with the rest of the app.
        appContext = context.applicationContext
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
        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            isLoading = true,
            errorMessage = null,
            // Options belong to the message that offered them - drop them the
            // moment the user sends anything, tapped or typed.
            quickReplies = emptyList(),
            awaitingContext = false
        )
        performSend(
            currentMessages, userText, profile, glucoseSummary,
            imageBase64, imageMimeType, documentBase64, documentMimeType, documentName
        )
    }

    private fun performSend(
        currentMessages: List<Message>, userText: String, profile: UserProfileData,
        glucoseSummary: String?, imageBase64: String?, imageMimeType: String?,
        documentBase64: String?, documentMimeType: String?, documentName: String?
    ) {
        viewModelScope.launch {
            try {
                // Structured medication summary. Fetched here, inside the
                // coroutine, because MedicationContext.buildSummary is a suspend
                // DB read. Returns null when the patient has entered no
                // structured medicines - in which case nothing is added to the
                // request and the backend behaves exactly as before. The plain
                // name list on `profile.medications` is sent regardless, so this
                // only ever ADDS detail, never removes what was already there.
                val medicationDetail = appContext?.let { ctx ->
                    try {
                        MedicationContext.buildSummary(ctx)
                    } catch (e: Exception) {
                        // A failure to read medicines must never block a chat
                        // message. Fall back to sending none - the name list
                        // still goes through the profile as always.
                        println("Medication summary failed: ${e.message}")
                        null
                    }
                }

                val history = currentMessages.dropLast(1)
                    .map { mapOf("role" to it.role, "content" to it.content) }
                val profileData = profile.toApiProfileData(glucoseSummary, medicationDetail)
                val request = ChatRequest(
                    message = userText, profile = profileData, conversation_history = history,
                    image_data = imageBase64, image_type = imageMimeType,
                    document_data = documentBase64, document_type = documentMimeType,
                    document_name = documentName
                )
                val response = NetworkModule.apiService.sendMessage(request)
                val aiMessage = Message(
                    role = "assistant",
                    content = response.response,
                    tier = response.tier
                )
                val updatedMessages = currentMessages + aiMessage
                lastFailedRetry = null
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages,
                    isLoading = false,
                    // Accessors here tolerate an older backend that omits the
                    // new fields entirely - both simply come back empty/false.
                    quickReplies = response.quickReplies,
                    awaitingContext = response.needsContext
                )
                historyRepo?.saveSession(updatedMessages)
            } catch (e: Exception) {
                e.printStackTrace()
                lastFailedRetry = {
                    performSend(
                        currentMessages, userText, profile, glucoseSummary,
                        imageBase64, imageMimeType, documentBase64, documentMimeType, documentName
                    )
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = friendlyNetworkMessage(e)
                )
            }
        }
    }
}