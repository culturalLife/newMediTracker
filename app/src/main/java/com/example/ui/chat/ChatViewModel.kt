package com.example.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.MistralRepository
import com.example.ai.model.ChatMessage
import com.example.ai.model.MedicineInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Drives the AI Pharmacist chat experience. A single instance is reused across
 * bottom-sheet open/close cycles so conversation history is preserved.
 */
class ChatViewModel(
    private val repository: MistralRepository = MistralRepository()
) : ViewModel() {

    /** A message rendered in the chat. May carry a structured info card (assistant only). */
    data class UiMessage(
        val id: String = UUID.randomUUID().toString(),
        val role: Role,
        val text: String,
        val card: MedicineInfo? = null
    ) {
        enum class Role { USER, ASSISTANT, ERROR }
    }

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Surfaced to the UI to display a permanent banner if no key was injected. */
    val isConfigured: Boolean = repository.isConfigured()

    /** Sends a free-form user question. */
    fun sendUserMessage(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _isLoading.value) return

        appendMessage(UiMessage(role = UiMessage.Role.USER, text = text))
        _isLoading.value = true

        viewModelScope.launch {
            val history = _messages.value
                .filter { it.role != UiMessage.Role.ERROR }
                .map { uiToWire(it) }
            repository.chat(history)
                .onSuccess { reply ->
                    appendMessage(UiMessage(role = UiMessage.Role.ASSISTANT, text = reply))
                }
                .onFailure { err ->
                    appendMessage(
                        UiMessage(
                            role = UiMessage.Role.ERROR,
                            text = err.message ?: "Something went wrong contacting the assistant."
                        )
                    )
                }
            _isLoading.value = false
        }
    }

    /**
     * Asks for a structured info card for the given medicine.
     * Used by the FAB on Home (with empty initial state) and by the
     * "Ask AI about this medicine" buttons on the Medicines screen.
     */
    fun askAboutMedicine(medicineName: String) {
        if (medicineName.isBlank() || _isLoading.value) return
        val prompt = "Tell me about $medicineName"
        appendMessage(UiMessage(role = UiMessage.Role.USER, text = prompt))
        _isLoading.value = true

        viewModelScope.launch {
            repository.getMedicineInfo(medicineName)
                .onSuccess { info ->
                    if (info.isEmpty()) {
                        appendMessage(
                            UiMessage(
                                role = UiMessage.Role.ASSISTANT,
                                text = "I couldn't find reliable information for \"$medicineName\". " +
                                    "Try checking the spelling, or ask your pharmacist."
                            )
                        )
                    } else {
                        appendMessage(
                            UiMessage(
                                role = UiMessage.Role.ASSISTANT,
                                text = info.purpose ?: "Here's what I found:",
                                card = info
                            )
                        )
                    }
                }
                .onFailure { err ->
                    appendMessage(
                        UiMessage(
                            role = UiMessage.Role.ERROR,
                            text = err.message ?: "Couldn't load medicine info."
                        )
                    )
                }
            _isLoading.value = false
        }
    }

    fun clearConversation() {
        _messages.value = emptyList()
    }

    private fun appendMessage(message: UiMessage) {
        _messages.value = _messages.value + message
    }

    private fun uiToWire(ui: UiMessage): ChatMessage = ChatMessage(
        role = when (ui.role) {
            UiMessage.Role.USER -> "user"
            UiMessage.Role.ASSISTANT -> "assistant"
            UiMessage.Role.ERROR -> "user" // never sent — filtered out before this call
        },
        content = ui.text
    )

    class Factory(private val repository: MistralRepository = MistralRepository()) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                "Unknown ViewModel class: $modelClass"
            }
            return ChatViewModel(repository) as T
        }
    }
}
