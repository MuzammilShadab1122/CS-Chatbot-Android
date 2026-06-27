package com.example.data.repository

import com.example.BuildConfig
import com.example.data.api.RetrofitClient
import com.example.data.db.MessageDao
import com.example.data.db.MessageEntity
import com.example.data.model.Content
import com.example.data.model.GenerateContentRequest
import com.example.data.model.GenerationConfig
import com.example.data.model.Part
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val messageDao: MessageDao) {

    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessages()

    suspend fun insertMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
    }

    suspend fun clearChat() {
        messageDao.clearChat()
    }

    suspend fun getGeminiResponse(chatHistory: List<MessageEntity>): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "API Key is missing or invalid. Please configure your GEMINI_API_KEY in the AI Studio Secrets panel."
        }

        val systemInstruction = Content(
            parts = listOf(
                Part(
                    text = """
                        You are a helpful assistant specialized in Computer Science.
                        
                        Primary Rule: You must only answer queries related to computer science, software engineering, programming, coding, data structures, algorithms, databases, computer networking, operating systems, and computer architecture.
                        
                        If the user asks any question unrelated to computer science, please reply politely with exactly:
                        "I am sorry, but I can only answer queries related to Computer Science."
                    """.trimIndent()
                )
            )
        )

        // Build conversational history contents list
        val contents = chatHistory.map { message ->
            val role = if (message.sender == "user") "user" else "model"
            Content(
                parts = listOf(Part(text = message.text)),
                role = role
            )
        }

        val request = GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(temperature = 0.2f), // low temperature for precise classification and answers
            systemInstruction = systemInstruction
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I am sorry, but I could not formulate a response. Please try again."
        } catch (e: Exception) {
            "Error: ${e.localizedMessage ?: "Failed to reach Gemini API. Please check your internet connection."}"
        }
    }
}
