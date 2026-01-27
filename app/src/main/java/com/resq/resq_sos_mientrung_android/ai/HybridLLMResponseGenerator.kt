package com.resq.resq_sos_mientrung_android.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.resq.resq_sos_mientrung_android.utils.NetworkUtils
import com.resq.resq_sos_mientrung_android.fragments.AIChatbotFragment.ResponseSource

/**
 * AI Response with source information
 */
data class AIResponse(
    val content: String,
    val source: ResponseSource
)

/**
 * Hybrid LLM Response Generator
 * Combines rule-based matching (fast, offline) with Gemini API (online, context-aware)
 * 
 * Strategy:
 * 1. Try rule-based first (fast, accurate for common questions, works offline)
 * 2. If no good match and online, use Gemini API (better context understanding)
 * 3. Fallback to rule-based if Gemini API fails or offline
 */
class HybridLLMResponseGenerator(private val context: Context) {
    
    private val knowledgeBase = FloodKnowledgeBase.getInstance(context)
    private val textEmbedding = TextEmbedding()
    private val geminiClient = GeminiAPIClient.getInstance(context)
    private val conversationContext = mutableListOf<Pair<String, String>>()
    private val conversationHistory = mutableListOf<String>()
    
    /**
     * Initialize Gemini API with API key
     * @param apiKey Gemini API key (get from https://aistudio.google.com/app/apikey)
     */
    fun initializeGemini(apiKey: String) {
        geminiClient.initialize(apiKey)
        Log.d("HybridLLM", "Gemini API initialized")
    }
    
    /**
     * Generate response using Gemini API only
     * Returns AIResponse with content and source information
     * 
     * Strategy:
     * - If online: Use Gemini API
     * - If offline or Gemini fails: Return error message
     */
    suspend fun generateResponse(userMessage: String): AIResponse = withContext(Dispatchers.Default) {
        val normalizedMessage = normalizeText(userMessage)
        
        // Add to conversation history
        conversationHistory.add(normalizedMessage)
        if (conversationHistory.size > 5) {
            conversationHistory.removeAt(0)
        }
        
        val contextText = conversationHistory.takeLast(3).joinToString(" ")
        
        // Check network and Gemini availability
        if (!NetworkUtils.isNetworkAvailable(context)) {
            val errorMessage = "⚠️ Không có kết nối mạng. Vui lòng kiểm tra kết nối internet và thử lại.\n\n" +
                    "Bạn có thể liên hệ số khẩn cấp 115 để được hỗ trợ ngay lập tức."
            return@withContext AIResponse(errorMessage, ResponseSource.GEMINI_API)
        }
        
        if (!geminiClient.isAvailable()) {
            val errorMessage = "⚠️ Gemini API chưa được khởi tạo. Vui lòng kiểm tra cấu hình.\n\n" +
                    "Bạn có thể liên hệ số khẩn cấp 115 để được hỗ trợ ngay lập tức."
            return@withContext AIResponse(errorMessage, ResponseSource.GEMINI_API)
        }
        
        // Use Gemini API
        val geminiResponse = tryGeminiGeneration(normalizedMessage, contextText)
        if (geminiResponse != null && geminiResponse.isNotEmpty()) {
            addToContext(userMessage, geminiResponse)
            return@withContext AIResponse(geminiResponse, ResponseSource.GEMINI_API)
        } else {
            // Gemini failed
            val errorMessage = "⚠️ Không thể kết nối đến Gemini API. Vui lòng thử lại sau.\n\n" +
                    "Bạn có thể liên hệ số khẩn cấp 115 để được hỗ trợ ngay lập tức."
            return@withContext AIResponse(errorMessage, ResponseSource.GEMINI_API)
        }
    }
    
    /**
     * Try rule-based matching first
     */
    private fun tryRuleBased(message: String, context: String): String? {
        // Try exact match
        val exactMatch = findExactMatch(message)
        if (exactMatch != null) return exactMatch
        
        // Try semantic match
        val semanticMatch = findSemanticMatch(message, context)
        if (semanticMatch != null) return semanticMatch
        
        // Try keyword match
        val keywordMatch = findKeywordMatch(message)
        if (keywordMatch != null) return keywordMatch
        
        return null
    }
    
    /**
     * Try Gemini API generation
     */
    private suspend fun tryGeminiGeneration(message: String, context: String): String? {
        return try {
            // Build context from conversation
            val conversationContextText = conversationContext.takeLast(2)
                .joinToString("\n") { "User: ${it.first}\nAI: ${it.second}" }
            
            val fullContext = if (conversationContextText.isNotEmpty()) {
                "$context\n$conversationContextText"
            } else {
                context
            }
            
            val response = geminiClient.generateResponse(
                userMessage = message,
                context = fullContext
            )
            
            // Post-process Gemini response
            response?.let { postProcessLLMResponse(it) }
        } catch (e: Exception) {
            Log.e("HybridLLM", "Error in Gemini API generation: ${e.message}", e)
            null
        }
    }
    
    /**
     * Post-process LLM response to ensure quality
     */
    private fun postProcessLLMResponse(response: String): String {
        var processed = response.trim()
        
        // Remove incomplete sentences at the end
        if (processed.isNotEmpty() && !processed.endsWith(".") && 
            !processed.endsWith("!") && !processed.endsWith("?") && 
            !processed.endsWith("。") && !processed.endsWith("！") && !processed.endsWith("？")) {
            val lastSentenceEnd = maxOf(
                processed.lastIndexOf("."),
                processed.lastIndexOf("!"),
                processed.lastIndexOf("?")
            )
            if (lastSentenceEnd > processed.length / 2) {
                processed = processed.substring(0, lastSentenceEnd + 1)
            }
        }
        
        // Ensure minimum length
        if (processed.length < 20) {
            return processed + " Vui lòng liên hệ số khẩn cấp 115 để được hỗ trợ."
        }
        
        return processed
    }
    
    /**
     * Check if rule-based match is good enough
     */
    private fun isGoodMatch(message: String, response: String): Boolean {
        val messageEmbedding = textEmbedding.embed(message)
        val responseEmbedding = textEmbedding.embed(response)
        val similarity = textEmbedding.cosineSimilarity(messageEmbedding, responseEmbedding)
        
        // Consider it a good match if similarity > 0.5
        return similarity > 0.5
    }
    
    // Rule-based matching methods (similar to ContextAwareResponseGenerator)
    
    private fun findExactMatch(message: String): String? {
        val allAnswers = knowledgeBase.getAllAnswers()
        val messageEmbedding = textEmbedding.embed(message)
        
        var bestMatch: KnowledgeAnswer? = null
        var bestScore = 0.0
        
        allAnswers.forEach { answer ->
            val questionEmbedding = textEmbedding.embed(answer.question)
            val similarity = textEmbedding.cosineSimilarity(messageEmbedding, questionEmbedding)
            
            if (similarity > 0.85 && similarity > bestScore) {
                bestScore = similarity
                bestMatch = answer
            }
        }
        
        return bestMatch?.answer
    }
    
    private fun findSemanticMatch(message: String, context: String): String? {
        val allAnswers = knowledgeBase.getAllAnswers()
        val messageEmbedding = textEmbedding.embed(message)
        val contextEmbedding = textEmbedding.embed(context)
        
        val combinedEmbedding = combineEmbeddings(messageEmbedding, contextEmbedding, 0.7, 0.3)
        
        var bestMatch: KnowledgeAnswer? = null
        var bestScore = 0.0
        
        allAnswers.forEach { answer ->
            val answerEmbedding = textEmbedding.embed(answer.question + " " + answer.answer)
            val similarity = textEmbedding.cosineSimilarity(combinedEmbedding, answerEmbedding)
            
            if (similarity > 0.6 && similarity > bestScore) {
                bestScore = similarity
                bestMatch = answer
            }
        }
        
        return bestMatch?.answer
    }
    
    private fun findKeywordMatch(message: String): String? {
        val matchingCategories = mutableListOf<Pair<KnowledgeCategory, Double>>()
        
        knowledgeBase.getAllCategories().forEach { category ->
            val categoryEmbedding = textEmbedding.embed(category.name + " " + category.keywords.joinToString(" "))
            val messageEmbedding = textEmbedding.embed(message)
            val similarity = textEmbedding.cosineSimilarity(messageEmbedding, categoryEmbedding)
            
            if (similarity > 0.3) {
                matchingCategories.add(Pair(category, similarity * 10))
            }
        }
        
        if (matchingCategories.isNotEmpty()) {
            val bestCategory = matchingCategories.maxByOrNull { it.second }?.first
            if (bestCategory != null && bestCategory.answers.isNotEmpty()) {
                val messageEmbedding = textEmbedding.embed(message)
                var bestAnswer: KnowledgeAnswer? = null
                var bestScore = 0.0
                
                bestCategory.answers.forEach { answer ->
                    val answerEmbedding = textEmbedding.embed(answer.question)
                    val similarity = textEmbedding.cosineSimilarity(messageEmbedding, answerEmbedding)
                    if (similarity > bestScore) {
                        bestScore = similarity
                        bestAnswer = answer
                    }
                }
                
                return bestAnswer?.answer ?: bestCategory.answers.first().answer
            }
        }
        
        return null
    }
    
    private fun combineEmbeddings(
        emb1: Map<String, Double>,
        emb2: Map<String, Double>,
        weight1: Double,
        weight2: Double
    ): Map<String, Double> {
        val combined = mutableMapOf<String, Double>()
        val allWords = (emb1.keys + emb2.keys).toSet()
        
        allWords.forEach { word ->
            val val1 = emb1[word] ?: 0.0
            val val2 = emb2[word] ?: 0.0
            combined[word] = val1 * weight1 + val2 * weight2
        }
        
        return combined
    }
    
    private fun addToContext(userMessage: String, aiResponse: String) {
        conversationContext.add(Pair(userMessage, aiResponse))
        if (conversationContext.size > 5) {
            conversationContext.removeAt(0)
        }
    }
    
    private fun normalizeText(text: String): String {
        return text.lowercase()
            .trim()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
    }
    
    fun clearContext() {
        conversationContext.clear()
        conversationHistory.clear()
    }
    
    fun isLLMEnabled(): Boolean = NetworkUtils.isNetworkAvailable(context) && geminiClient.isAvailable()
    
    fun getSuggestedQuestions(): List<String> {
        return listOf(
            "Làm gì khi bị thương trong lũ?",
            "Cách di dời an toàn khi có lũ?",
            "Số điện thoại khẩn cấp là gì?",
            "Dấu hiệu nào cho thấy sắp có lũ?",
            "Cách chuẩn bị trước khi có lũ?"
        )
    }
}
