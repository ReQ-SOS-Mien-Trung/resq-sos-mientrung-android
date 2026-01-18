package com.resq.resq_sos_mientrung_android.ai

import android.content.Context
import kotlin.math.max

class AIResponseGenerator(private val context: Context) {
    
    private val knowledgeBase = FloodKnowledgeBase.getInstance(context)
    private val conversationContext = mutableListOf<String>()
    
    fun generateResponse(userMessage: String): String {
        val normalizedMessage = normalizeText(userMessage)
        
        // Add to conversation context (keep last 3 messages)
        conversationContext.add(normalizedMessage)
        if (conversationContext.size > 3) {
            conversationContext.removeAt(0)
        }
        
        // Try to find exact match first
        val exactMatch = findExactMatch(normalizedMessage)
        if (exactMatch != null) {
            return exactMatch
        }
        
        // Try keyword matching
        val keywordMatch = findKeywordMatch(normalizedMessage)
        if (keywordMatch != null) {
            return keywordMatch
        }
        
        // Try text similarity search
        val similarityMatch = findSimilarityMatch(normalizedMessage)
        if (similarityMatch != null) {
            return similarityMatch
        }
        
        // Fallback response
        return knowledgeBase.getRandomFallbackResponse()
    }
    
    private fun normalizeText(text: String): String {
        return text.lowercase()
            .trim()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ") // Remove special chars except letters, numbers, spaces
            .replace(Regex("\\s+"), " ") // Multiple spaces to single space
    }
    
    private fun findExactMatch(message: String): String? {
        val allAnswers = knowledgeBase.getAllAnswers()
        
        // Check if message matches any question exactly (with some flexibility)
        for (answer in allAnswers) {
            val normalizedQuestion = normalizeText(answer.question)
            if (calculateSimilarity(message, normalizedQuestion) > 0.8) {
                return answer.answer
            }
        }
        
        return null
    }
    
    private fun findKeywordMatch(message: String): String? {
        val words = message.split(" ").filter { it.length > 2 } // Filter out short words
        
        // Find categories that match keywords
        val matchingCategories = mutableListOf<Pair<KnowledgeCategory, Int>>()
        
        knowledgeBase.getAllCategories().forEach { category ->
            var matchScore = 0
            category.keywords.forEach { keyword ->
                words.forEach { word ->
                    if (word.contains(keyword) || keyword.contains(word)) {
                        matchScore += 2
                    }
                }
                if (message.contains(keyword)) {
                    matchScore += 3
                }
            }
            if (matchScore > 0) {
                matchingCategories.add(Pair(category, matchScore))
            }
        }
        
        // Sort by score and get best match
        if (matchingCategories.isNotEmpty()) {
            val bestCategory = matchingCategories.maxByOrNull { it.second }?.first
            if (bestCategory != null && bestCategory.answers.isNotEmpty()) {
                // Return the first answer from the best matching category
                // or find the best matching answer within the category
                val bestAnswer = findBestAnswerInCategory(bestCategory, message)
                return bestAnswer?.answer ?: bestCategory.answers.first().answer
            }
        }
        
        return null
    }
    
    private fun findBestAnswerInCategory(
        category: KnowledgeCategory,
        message: String
    ): KnowledgeAnswer? {
        var bestAnswer: KnowledgeAnswer? = null
        var bestScore = 0.0
        
        category.answers.forEach { answer ->
            val questionSimilarity = calculateSimilarity(message, normalizeText(answer.question))
            val answerSimilarity = calculateSimilarity(message, normalizeText(answer.answer))
            val score = max(questionSimilarity, answerSimilarity * 0.7) // Question match is more important
            
            if (score > bestScore) {
                bestScore = score
                bestAnswer = answer
            }
        }
        
        return if (bestScore > 0.3) bestAnswer else null
    }
    
    private fun findSimilarityMatch(message: String): String? {
        val searchResults = knowledgeBase.searchAnswers(message)
        
        if (searchResults.isNotEmpty()) {
            // Find the best match by similarity
            var bestAnswer: KnowledgeAnswer? = null
            var bestScore = 0.0
            
            searchResults.forEach { answer ->
                val questionSimilarity = calculateSimilarity(message, normalizeText(answer.question))
                val answerSimilarity = calculateSimilarity(message, normalizeText(answer.answer))
                val score = max(questionSimilarity, answerSimilarity * 0.6)
                
                if (score > bestScore) {
                    bestScore = score
                    bestAnswer = answer
                }
            }
            
            if (bestScore > 0.4 && bestAnswer != null) {
                return bestAnswer.answer
            }
        }
        
        return null
    }
    
    private fun calculateSimilarity(text1: String, text2: String): Double {
        if (text1 == text2) return 1.0
        if (text1.isEmpty() || text2.isEmpty()) return 0.0
        
        // Check if one contains the other
        if (text1.contains(text2) || text2.contains(text1)) {
            val shorter = minOf(text1.length, text2.length)
            val longer = maxOf(text1.length, text2.length)
            return shorter.toDouble() / longer.toDouble() * 0.9
        }
        
        // Calculate word overlap
        val words1 = text1.split(" ").toSet()
        val words2 = text2.split(" ").toSet()
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        if (union == 0) return 0.0
        
        // Jaccard similarity
        val jaccard = intersection.toDouble() / union.toDouble()
        
        // Also check for substring matches
        var substringScore = 0.0
        words1.forEach { word1 ->
            words2.forEach { word2 ->
                if (word1.length > 3 && word2.length > 3) {
                    if (word1.contains(word2) || word2.contains(word1)) {
                        substringScore += 0.1
                    }
                }
            }
        }
        
        return minOf(1.0, jaccard + substringScore)
    }
    
    fun clearContext() {
        conversationContext.clear()
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
