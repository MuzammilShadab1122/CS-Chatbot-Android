package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ChatDatabase
import com.example.data.db.MessageEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ChatDatabase.getDatabase(application)
    private val repository = ChatRepository(database.messageDao())

    val messages: StateFlow<List<MessageEntity>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Save user message to database
            val userMsg = MessageEntity(text = text.trim(), sender = "user")
            repository.insertMessage(userMsg)

            _isSending.value = true

            // Fetch answer from Gemini with context
            // Gather all current messages in the db to pass as context
            val currentHistory = messages.value
            val responseText = repository.getGeminiResponse(currentHistory)

            // Save bot response to database
            val botMsg = MessageEntity(text = responseText, sender = "bot")
            repository.insertMessage(botMsg)

            _isSending.value = false
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }
}
