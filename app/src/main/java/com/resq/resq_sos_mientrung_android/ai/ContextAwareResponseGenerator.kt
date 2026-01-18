package com.resq.resq_sos_mientrung_android.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced AI Response Generator with better context understanding
 * Uses semantic embeddings and context-aware responses
 */
class ContextAwareResponseGenerator(private val context: Context) {
    
    private val knowledgeBase = FloodKnowledgeBase.getInstance(context)
    private val textEmbedding = TextEmbedding()
    private val conversationContext = mutableListOf<Pair<String, String>>() // (userMessage, aiResponse)
    private val conversationHistory = mutableListOf<String>() // Keep last 5 messages for context
    
    /**
     * Generate response with better context understanding
     */
    suspend fun generateResponse(userMessage: String): String = withContext(Dispatchers.Default) {
        val normalizedMessage = normalizeText(userMessage)
        
        // Add to conversation history
        conversationHistory.add(normalizedMessage)
        if (conversationHistory.size > 5) {
            conversationHistory.removeAt(0)
        }
        
        // Build context from conversation history
        val contextText = conversationHistory.takeLast(3).joinToString(" ")
        
        // Try multiple strategies in order of preference
        val strategies = listOf(
            { findExactMatch(normalizedMessage) },
            { findSemanticMatch(normalizedMessage, contextText) },
            { findKeywordMatch(normalizedMessage) },
            { findContextualMatch(normalizedMessage, contextText) },
            { generateContextualResponse(normalizedMessage, contextText) }
        )
        
        for (strategy in strategies) {
            val result = strategy()
            if (result != null) {
                // Add to conversation context
                conversationContext.add(Pair(userMessage, result))
                if (conversationContext.size > 5) {
                    conversationContext.removeAt(0)
                }
                return@withContext result
            }
        }
        
        // Final fallback
        knowledgeBase.getRandomFallbackResponse()
    }
    
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
        
        // Combine message and context for better understanding
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
        val words = message.split(" ").filter { it.length > 2 }
        val matchingCategories = mutableListOf<Pair<KnowledgeCategory, Double>>()
        
        knowledgeBase.getAllCategories().forEach { category ->
            var matchScore = 0.0
            val categoryEmbedding = textEmbedding.embed(category.name + " " + category.keywords.joinToString(" "))
            val messageEmbedding = textEmbedding.embed(message)
            val similarity = textEmbedding.cosineSimilarity(messageEmbedding, categoryEmbedding)
            
            matchScore = similarity * 10 // Scale up
            
            if (matchScore > 3.0) {
                matchingCategories.add(Pair(category, matchScore))
            }
        }
        
        if (matchingCategories.isNotEmpty()) {
            val bestCategory = matchingCategories.maxByOrNull { it.second }?.first
            if (bestCategory != null && bestCategory.answers.isNotEmpty()) {
                // Find best answer in category using semantic similarity
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
    
    private fun findContextualMatch(message: String, context: String): String? {
        // Use conversation context to better understand follow-up questions
        if (conversationContext.isEmpty()) return null
        
        val lastResponse = conversationContext.last().second
        val combinedQuery = "$context $message"
        
        val allAnswers = knowledgeBase.getAllAnswers()
        val queryEmbedding = textEmbedding.embed(combinedQuery)
        val lastResponseEmbedding = textEmbedding.embed(lastResponse)
        
        var bestMatch: KnowledgeAnswer? = null
        var bestScore = 0.0
        
        allAnswers.forEach { answer ->
            val answerEmbedding = textEmbedding.embed(answer.question + " " + answer.answer)
            val similarity = textEmbedding.cosineSimilarity(queryEmbedding, answerEmbedding)
            
            // Boost score if related to last response
            val contextSimilarity = textEmbedding.cosineSimilarity(lastResponseEmbedding, answerEmbedding)
            val finalScore = similarity * 0.7 + contextSimilarity * 0.3
            
            if (finalScore > 0.5 && finalScore > bestScore) {
                bestScore = finalScore
                bestMatch = answer
            }
        }
        
        return bestMatch?.answer
    }
    
    private fun generateContextualResponse(message: String, context: String): String? {
        // Generate a more contextual response based on the conversation
        val messageLower = message.lowercase()
        
        // Check for follow-up questions
        val followUpPatterns = listOf(
            "còn", "thêm", "nữa", "khác", "nào", "gì", "sao", "thế nào", "làm sao"
        )
        
        val isFollowUp = followUpPatterns.any { messageLower.contains(it) }
        
        if (isFollowUp && conversationContext.isNotEmpty()) {
            // This is a follow-up question, provide related information
            val lastTopic = conversationContext.last().second
            val relatedAnswers = findRelatedAnswers(lastTopic, message)
            
            if (relatedAnswers.isNotEmpty()) {
                return relatedAnswers.first().answer
            }
        }
        
        // Check for clarification requests
        val clarificationPatterns = listOf(
            "cụ thể", "chi tiết", "rõ hơn", "giải thích", "nghĩa là"
        )
        
        if (clarificationPatterns.any { messageLower.contains(it) } && conversationContext.isNotEmpty()) {
            val lastResponse = conversationContext.last().second
            // Return a more detailed version if available
            return findDetailedAnswer(lastResponse, message)
        }
        
        return null
    }
    
    private fun findRelatedAnswers(topic: String, query: String): List<KnowledgeAnswer> {
        val topicEmbedding = textEmbedding.embed(topic)
        val queryEmbedding = textEmbedding.embed(query)
        val combinedEmbedding = combineEmbeddings(topicEmbedding, queryEmbedding, 0.6, 0.4)
        
        val allAnswers = knowledgeBase.getAllAnswers()
        val related = mutableListOf<Pair<KnowledgeAnswer, Double>>()
        
        allAnswers.forEach { answer ->
            val answerEmbedding = textEmbedding.embed(answer.question + " " + answer.answer)
            val similarity = textEmbedding.cosineSimilarity(combinedEmbedding, answerEmbedding)
            if (similarity > 0.4) {
                related.add(Pair(answer, similarity))
            }
        }
        
        return related.sortedByDescending { it.second }.take(3).map { it.first }
    }
    
    private fun findDetailedAnswer(topic: String, query: String): String? {
        val topicEmbedding = textEmbedding.embed(topic)
        val queryEmbedding = textEmbedding.embed(query)
        val combinedEmbedding = combineEmbeddings(topicEmbedding, queryEmbedding, 0.5, 0.5)
        
        val allAnswers = knowledgeBase.getAllAnswers()
        var bestAnswer: KnowledgeAnswer? = null
        var bestScore = 0.0
        
        allAnswers.forEach { answer ->
            val answerEmbedding = textEmbedding.embed(answer.answer)
            val similarity = textEmbedding.cosineSimilarity(combinedEmbedding, answerEmbedding)
            
            // Prefer longer, more detailed answers
            val lengthBonus = (answer.answer.length / 100.0).coerceAtMost(0.2)
            val finalScore = similarity + lengthBonus
            
            if (finalScore > bestScore) {
                bestScore = finalScore
                bestAnswer = answer
            }
        }
        
        return bestAnswer?.answer
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
