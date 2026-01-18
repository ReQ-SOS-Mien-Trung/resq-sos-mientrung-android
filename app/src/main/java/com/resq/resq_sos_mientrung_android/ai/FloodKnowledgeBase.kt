package com.resq.resq_sos_mientrung_android.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

data class KnowledgeCategory(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val answers: List<KnowledgeAnswer>
)

data class KnowledgeAnswer(
    val question: String,
    val answer: String
)

class FloodKnowledgeBase private constructor(context: Context) {
    
    private val categories = mutableListOf<KnowledgeCategory>()
    private val fallbackResponses = mutableListOf<String>()
    
    init {
        loadKnowledgeBase(context)
    }
    
    companion object {
        @Volatile
        private var INSTANCE: FloodKnowledgeBase? = null
        
        fun getInstance(context: Context): FloodKnowledgeBase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FloodKnowledgeBase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private fun loadKnowledgeBase(context: Context) {
        try {
            val resourceId = context.resources.getIdentifier("flood_knowledge", "raw", context.packageName)
            if (resourceId == 0) {
                android.util.Log.e("FloodKnowledgeBase", "flood_knowledge.json not found in raw resources")
                return
            }
            val inputStream: InputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            // Load categories
            val categoriesArray = jsonObject.getJSONArray("categories")
            for (i in 0 until categoriesArray.length()) {
                val categoryObj = categoriesArray.getJSONObject(i)
                val keywordsArray = categoryObj.getJSONArray("keywords")
                val keywords = mutableListOf<String>()
                for (j in 0 until keywordsArray.length()) {
                    keywords.add(keywordsArray.getString(j).lowercase())
                }
                
                val answersArray = categoryObj.getJSONArray("answers")
                val answers = mutableListOf<KnowledgeAnswer>()
                for (j in 0 until answersArray.length()) {
                    val answerObj = answersArray.getJSONObject(j)
                    answers.add(
                        KnowledgeAnswer(
                            question = answerObj.getString("question"),
                            answer = answerObj.getString("answer")
                        )
                    )
                }
                
                categories.add(
                    KnowledgeCategory(
                        id = categoryObj.getString("id"),
                        name = categoryObj.getString("name"),
                        keywords = keywords,
                        answers = answers
                    )
                )
            }
            
            // Load fallback responses
            val fallbackArray = jsonObject.getJSONArray("fallback_responses")
            for (i in 0 until fallbackArray.length()) {
                fallbackResponses.add(fallbackArray.getString(i))
            }
            
        } catch (e: Exception) {
            android.util.Log.e("FloodKnowledgeBase", "Error loading knowledge base: ${e.message}", e)
        }
    }
    
    fun getAllCategories(): List<KnowledgeCategory> = categories.toList()
    
    fun getCategoryById(id: String): KnowledgeCategory? {
        return categories.find { it.id == id }
    }
    
    fun getCategoriesByKeyword(keyword: String): List<KnowledgeCategory> {
        val lowerKeyword = keyword.lowercase()
        return categories.filter { category ->
            category.keywords.any { it.contains(lowerKeyword) || lowerKeyword.contains(it) }
        }
    }
    
    fun getAllAnswers(): List<KnowledgeAnswer> {
        return categories.flatMap { it.answers }
    }
    
    fun getAnswersByCategory(categoryId: String): List<KnowledgeAnswer> {
        return getCategoryById(categoryId)?.answers ?: emptyList()
    }
    
    fun getRandomFallbackResponse(): String {
        return if (fallbackResponses.isNotEmpty()) {
            fallbackResponses.random()
        } else {
            "Xin lỗi, tôi chưa hiểu câu hỏi của bạn. Hãy hỏi về lũ lụt, sơ cứu, hoặc di dời."
        }
    }
    
    fun searchAnswers(query: String): List<KnowledgeAnswer> {
        val lowerQuery = query.lowercase()
        val matchingAnswers = mutableListOf<Pair<KnowledgeAnswer, Int>>()
        
        // Search in questions and answers
        categories.forEach { category ->
            category.answers.forEach { answer ->
                var score = 0
                val questionLower = answer.question.lowercase()
                val answerLower = answer.answer.lowercase()
                
                // Check if query matches question
                if (questionLower.contains(lowerQuery) || lowerQuery.contains(questionLower)) {
                    score += 10
                }
                
                // Check if query matches answer
                if (answerLower.contains(lowerQuery)) {
                    score += 5
                }
                
                // Check keyword matches
                category.keywords.forEach { keyword ->
                    if (lowerQuery.contains(keyword) || keyword.contains(lowerQuery)) {
                        score += 3
                    }
                }
                
                if (score > 0) {
                    matchingAnswers.add(Pair(answer, score))
                }
            }
        }
        
        // Sort by score and return answers
        return matchingAnswers
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
