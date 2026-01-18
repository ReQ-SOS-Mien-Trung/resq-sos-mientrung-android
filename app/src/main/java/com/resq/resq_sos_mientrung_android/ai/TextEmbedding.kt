package com.resq.resq_sos_mientrung_android.ai

import java.util.*

/**
 * Simple text embedding using TF-IDF and word vectors
 * This provides semantic understanding without requiring a large ML model
 */
class TextEmbedding {
    
    companion object {
        // Vietnamese stop words
        private val stopWords = setOf(
            "và", "của", "cho", "với", "là", "là", "mà", "này", "đó", "có", "không",
            "được", "trong", "về", "từ", "đến", "các", "những", "một", "hai", "ba",
            "theo", "như", "khi", "nếu", "thì", "để", "vì", "do", "bởi", "vào",
            "trên", "dưới", "sau", "trước", "giữa", "bên", "ngoài", "trong", "của",
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "should", "could"
        )
        
        // Flood-related important terms with weights
        private val importantTerms = mapOf(
            "lũ" to 2.0, "lũ lụt" to 3.0, "ngập" to 2.0, "nước" to 1.5,
            "sơ cứu" to 2.5, "cứu hộ" to 2.5, "cấp cứu" to 2.5,
            "di dời" to 2.0, "sơ tán" to 2.0, "di chuyển" to 1.5,
            "phòng chống" to 2.0, "chuẩn bị" to 1.5,
            "khẩn cấp" to 2.0, "nguy hiểm" to 2.0,
            "thương" to 1.5, "bị thương" to 2.0, "đuối nước" to 2.5,
            "số điện thoại" to 1.5, "liên hệ" to 1.5,
            "cứu trợ" to 2.0, "hỗ trợ" to 1.5
        )
    }
    
    /**
     * Create a simple embedding vector for text using TF-IDF-like approach
     */
    fun embed(text: String): Map<String, Double> {
        val normalized = normalizeText(text)
        val words = tokenize(normalized)
        val wordFreq = mutableMapOf<String, Double>()
        
        words.forEach { word ->
            if (word.length > 1 && !stopWords.contains(word)) {
                val weight = importantTerms[word] ?: 1.0
                wordFreq[word] = (wordFreq[word] ?: 0.0) + weight
            }
        }
        
        // Normalize
        val total = wordFreq.values.sum()
        if (total > 0) {
            wordFreq.keys.forEach { word ->
                wordFreq[word] = wordFreq[word]!! / total
            }
        }
        
        return wordFreq
    }
    
    /**
     * Calculate cosine similarity between two embeddings
     */
    fun cosineSimilarity(embedding1: Map<String, Double>, embedding2: Map<String, Double>): Double {
        val allWords = (embedding1.keys + embedding2.keys).toSet()
        
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        
        allWords.forEach { word ->
            val val1 = embedding1[word] ?: 0.0
            val val2 = embedding2[word] ?: 0.0
            dotProduct += val1 * val2
            norm1 += val1 * val1
            norm2 += val2 * val2
        }
        
        val denominator = Math.sqrt(norm1) * Math.sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0.0
    }
    
    private fun normalizeText(text: String): String {
        return text.lowercase(Locale.getDefault())
            .trim()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
    }
    
    private fun tokenize(text: String): List<String> {
        // Simple tokenization - split by spaces and handle Vietnamese words
        return text.split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { word ->
                // Handle compound words (e.g., "lũ lụt" -> ["lũ", "lụt", "lũ lụt"])
                val tokens = mutableListOf<String>()
                tokens.add(word)
                
                // Add bigrams for important terms
                if (word.length > 3) {
                    // Check for common Vietnamese compound words
                    val compounds = listOf("lũ lụt", "sơ cứu", "di dời", "phòng chống", "cứu hộ", "cấp cứu")
                    compounds.forEach { compound ->
                        if (text.contains(compound)) {
                            tokens.add(compound)
                        }
                    }
                }
                
                tokens
            }
    }
    
    /**
     * Find semantic similarity between two texts
     */
    fun semanticSimilarity(text1: String, text2: String): Double {
        val emb1 = embed(text1)
        val emb2 = embed(text2)
        return cosineSimilarity(emb1, emb2)
    }
}
