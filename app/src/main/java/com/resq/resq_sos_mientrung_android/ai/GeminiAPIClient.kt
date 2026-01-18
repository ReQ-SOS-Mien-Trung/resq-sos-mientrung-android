package com.resq.resq_sos_mientrung_android.ai

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini API Client
 * Handles communication with Google Gemini API
 * 
 * Note: Requires API key to be set in build config or resources
 */
class GeminiAPIClient private constructor(private val context: Context) {
    
    private var generativeModel: GenerativeModel? = null
    private var chat: Chat? = null
    private var apiKey: String? = null
    
    companion object {
        private const val TAG = "GeminiAPIClient"
        private const val DEFAULT_MODEL = "gemini-3-flash-preview" // Latest preview model
        private const val MAX_OUTPUT_TOKENS = 500
        private const val TEMPERATURE = 0.7f
        
        @Volatile
        private var INSTANCE: GeminiAPIClient? = null
        
        fun getInstance(context: Context): GeminiAPIClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GeminiAPIClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Initialize Gemini API with API key
     * @param apiKey Gemini API key (get from https://aistudio.google.com/app/apikey)
     */
    fun initialize(apiKey: String) {
        this.apiKey = apiKey
        try {
            val config = generationConfig {
                temperature = TEMPERATURE
                maxOutputTokens = MAX_OUTPUT_TOKENS
                topP = 0.95f
            }
            
            generativeModel = GenerativeModel(
                modelName = DEFAULT_MODEL,
                apiKey = apiKey,
                generationConfig = config
            )
            
            chat = generativeModel?.startChat()
            Log.d(TAG, "Gemini API initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Gemini API: ${e.message}", e)
            generativeModel = null
            chat = null
        }
    }
    
    /**
     * Check if Gemini API is available
     */
    fun isAvailable(): Boolean {
        return apiKey != null && generativeModel != null && chat != null
    }
    
    /**
     * Generate response using Gemini API
     * @param userMessage User's message
     * @param context Additional context (conversation history, etc.)
     * @return Generated response or null if error
     */
    suspend fun generateResponse(
        userMessage: String,
        context: String = ""
    ): String? = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            Log.w(TAG, "Gemini API not available")
            return@withContext null
        }
        
        return@withContext try {
            val chat = this@GeminiAPIClient.chat ?: return@withContext null
            
            // Build prompt with context
            val prompt = buildPrompt(userMessage, context)
            
            Log.d(TAG, "Sending request to Gemini API: ${prompt.take(100)}...")
            
            // Send message and get response
            val response = chat.sendMessage(prompt)
            val responseText = response.text ?: ""
            
            if (responseText.isNotEmpty()) {
                Log.d(TAG, "Received response from Gemini API: ${responseText.take(100)}...")
            } else {
                Log.w(TAG, "Empty response from Gemini API")
            }
            
            responseText.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response from Gemini API: ${e.message}", e)
            null
        }
    }
    
    /**
     * Build prompt with context for flood-related questions
     */
    private fun buildPrompt(userMessage: String, context: String): String {
        val systemPrompt = """
            Bạn là một trợ lý AI chuyên về lũ lụt và cứu trợ khẩn cấp tại miền Trung Việt Nam.
            Nhiệm vụ của bạn là cung cấp thông tin hữu ích, chính xác và an toàn về:
            - Sơ cứu và y tế khẩn cấp
            - Di dời và sơ tán an toàn
            - Phòng chống lũ lụt
            - Thông tin cứu trợ và số điện thoại khẩn cấp
            - An toàn trong và sau lũ
            
            Hãy trả lời ngắn gọn, rõ ràng và tập trung vào thông tin thực tế, hữu ích.
            Nếu không chắc chắn, hãy khuyên người dùng liên hệ số khẩn cấp 115.
        """.trimIndent()
        
        val contextPart = if (context.isNotEmpty()) {
            "\n\nNgữ cảnh cuộc trò chuyện trước:\n$context"
        } else {
            ""
        }
        
        return "$systemPrompt$contextPart\n\nNgười dùng hỏi: $userMessage\n\nTrả lời:"
    }
    
    /**
     * Reset chat history
     */
    fun resetChat() {
        try {
            chat = generativeModel?.startChat()
            Log.d(TAG, "Chat history reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting chat: ${e.message}", e)
        }
    }
}
