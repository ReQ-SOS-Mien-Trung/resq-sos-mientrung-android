package com.resq.resq_sos_mientrung_android.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.resq.resq_sos_mientrung_android.R
import com.resq.resq_sos_mientrung_android.BuildConfig
import com.resq.resq_sos_mientrung_android.ai.HybridLLMResponseGenerator
import com.resq.resq_sos_mientrung_android.databinding.FragmentAiChatbotBinding
import com.resq.resq_sos_mientrung_android.utils.ConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AIChatbotFragment : Fragment() {
    private var _binding: FragmentAiChatbotBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var aiResponseGenerator: HybridLLMResponseGenerator
    private val messagesAdapter = MessagesAdapter()
    private val suggestionsAdapter = SuggestionsAdapter()
    private val messages = mutableListOf<ChatMessage>()
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    enum class ResponseSource {
        GEMINI_API,      // Dùng Gemini API
        RULE_BASED,      // Dùng rule-based
        UNKNOWN          // Không xác định (cho user messages)
    }
    
    data class ChatMessage(
        val id: String,
        val content: String,
        val isFromUser: Boolean,
        val timestamp: Long,
        val responseSource: ResponseSource = ResponseSource.UNKNOWN
    )
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiChatbotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        aiResponseGenerator = HybridLLMResponseGenerator(requireContext())
        
        // Initialize Gemini API with API key from BuildConfig (loaded from local.properties)
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotEmpty()) {
            aiResponseGenerator.initializeGemini(apiKey)
        } else {
            // Fallback: Try to get from SharedPreferences (if user set it manually)
            ConfigManager.getGeminiApiKey(requireContext())?.let { savedApiKey ->
                if (savedApiKey.isNotEmpty()) {
                    aiResponseGenerator.initializeGemini(savedApiKey)
                }
            }
        }
        
        setupRecyclerView()
        setupSuggestionsRecyclerView()
        setupSendButton()
        setupKeyboardListener()
        setupWindowInsets()
        
        // Show welcome message
        showWelcomeMessage()
        updateEmptyState()
    }
    
    private fun showWelcomeMessage() {
        // Only show welcome message if there are no messages yet
        if (messages.isEmpty()) {
            val welcomeMessage = ChatMessage(
                id = "welcome_${System.currentTimeMillis()}",
                content = "👋 Xin chào! Tôi là Chat Bot AI hỗ trợ về lũ lụt.\n\nTôi có thể giúp bạn:\n• Sơ cứu và an toàn\n• Di dời khẩn cấp\n• Phòng chống lũ\n• Thông tin cứu trợ\n• Số điện thoại khẩn cấp\n\nHãy hỏi tôi bất cứ điều gì về lũ lụt!",
                isFromUser = false,
                timestamp = System.currentTimeMillis(),
                responseSource = ResponseSource.UNKNOWN
            )
            messages.add(welcomeMessage)
            messagesAdapter.submitList(messages.toList())
        }
    }
    
    private fun setupRecyclerView() {
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.recyclerViewMessages.adapter = messagesAdapter
    }
    
    private fun setupSuggestionsRecyclerView() {
        binding.recyclerViewSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewSuggestions.adapter = suggestionsAdapter
        
        val suggestedQuestions = aiResponseGenerator.getSuggestedQuestions()
        suggestionsAdapter.submitList(suggestedQuestions)
        suggestionsAdapter.setOnSuggestionClickListener { question ->
            binding.editTextMessage.setText(question)
            binding.editTextMessage.requestFocus()
            sendMessage(question)
        }
    }
    
    private fun setupSendButton() {
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }
        
        binding.editTextMessage.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }
    
    private fun setupKeyboardListener() {
        binding.editTextMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                handler.postDelayed({
                    scrollToBottom()
                }, 300)
            }
        }
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutMessageInput) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val layoutParams = v.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            
            if (imeInsets.bottom > 0) {
                val smallMargin = (4 * resources.displayMetrics.density).toInt()
                layoutParams?.bottomMargin = smallMargin
                v.layoutParams = layoutParams
                
                handler.postDelayed({
                    scrollToBottom()
                }, 200)
            } else {
                layoutParams?.bottomMargin = 0
                v.layoutParams = layoutParams
            }
            
            insets
        }
    }
    
    private fun sendMessage(userMessage: String? = null) {
        val messageText = userMessage ?: binding.editTextMessage.text.toString().trim()
        if (messageText.isEmpty()) return
        
        // Clear input first
        binding.editTextMessage.text?.clear()
        
        // Add user message immediately
        val userMessageObj = ChatMessage(
            id = "user_${System.currentTimeMillis()}",
            content = messageText,
            isFromUser = true,
            timestamp = System.currentTimeMillis()
        )
        messages.add(userMessageObj)
        messagesAdapter.submitList(messages.toList())
        scrollToBottom()
        updateEmptyState()
        
        // Show loading/typing indicator
        binding.progressBarAI.visibility = View.VISIBLE
        
        // Generate AI response using coroutines for async processing
        coroutineScope.launch {
            try {
                // Calculate minimum thinking time based on message complexity
                val minThinkingTime = calculateThinkingTime(messageText)
                
                // Generate response in background
                val aiResponse = withContext(Dispatchers.Default) {
                    // Simulate AI "thinking" - minimum delay
                    kotlinx.coroutines.delay(minThinkingTime)
                    aiResponseGenerator.generateResponse(messageText)
                }
                
                // Additional delay based on response length (more realistic)
                val responseDelay = (aiResponse.content.length / 20).coerceAtMost(2000).toLong()
                kotlinx.coroutines.delay(responseDelay)
                
                val aiMessageObj = ChatMessage(
                    id = "ai_${System.currentTimeMillis()}",
                    content = aiResponse.content,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis(),
                    responseSource = aiResponse.source // Lưu source
                )
                messages.add(aiMessageObj)
                messagesAdapter.submitList(messages.toList())
                scrollToBottom()
            } catch (e: Exception) {
                android.util.Log.e("AIChatbotFragment", "Error generating response: ${e.message}", e)
                Toast.makeText(
                    requireContext(),
                    "Có lỗi xảy ra. Vui lòng thử lại.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBarAI.visibility = View.GONE
            }
        }
    }
    
    /**
     * Calculate minimum thinking time based on message complexity
     * Makes AI feel more natural and "thinking"
     */
    private fun calculateThinkingTime(message: String): Long {
        val baseDelay = 800L // Minimum 800ms
        val lengthFactor = (message.length / 10).coerceAtMost(15) * 100L // Up to 1.5s for long messages
        val complexityFactor = if (message.contains("?")) 300L else 0L // Extra for questions
        val wordCount = message.split(" ").size
        val wordFactor = (wordCount / 5).coerceAtMost(10) * 50L // Up to 500ms for many words
        
        return baseDelay + lengthFactor + complexityFactor + wordFactor
    }
    
    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            binding.recyclerViewMessages.post {
                val itemCount = messagesAdapter.itemCount
                if (itemCount > 0) {
                    binding.recyclerViewMessages.smoothScrollToPosition(itemCount - 1)
                }
            }
        }
    }
    
    private fun updateEmptyState() {
        // Hide empty state once user sends first message
        val hasUserMessages = messages.any { it.isFromUser }
        if (hasUserMessages) {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerViewMessages.visibility = View.VISIBLE
        } else {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerViewMessages.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        coroutineScope.cancel()
        _binding = null
    }
    
    // Adapter for messages
    private class MessagesAdapter : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {
        private val messages = mutableListOf<ChatMessage>()
        
        fun submitList(newMessages: List<ChatMessage>) {
            messages.clear()
            messages.addAll(newMessages)
            notifyDataSetChanged()
        }
        
        override fun getItemViewType(position: Int): Int {
            return if (messages[position].isFromUser) 0 else 1
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val layoutId = if (viewType == 0) R.layout.item_message else R.layout.item_message_received
            val view = LayoutInflater.from(parent.context)
                .inflate(layoutId, parent, false)
            return MessageViewHolder(view, viewType == 0)
        }
        
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messages[position]
            holder.bind(message)
        }
        
        override fun getItemCount() = messages.size
        
        class MessageViewHolder(itemView: View, private val isSent: Boolean) : RecyclerView.ViewHolder(itemView) {
            private val textMessageContent = itemView.findViewById<TextView>(R.id.textMessageContent)
            private val textMessageTime = itemView.findViewById<TextView>(R.id.textMessageTime)
            private val textSenderName = itemView.findViewById<TextView>(R.id.textSenderName)
            
            fun bind(message: ChatMessage) {
                textMessageContent?.text = message.content
                textMessageTime?.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(message.timestamp))
                
                if (!isSent && textSenderName != null) {
                    textSenderName.visibility = View.VISIBLE
                    
                    // Hiển thị badge dựa trên source
                    when (message.responseSource) {
                        ResponseSource.GEMINI_API -> {
                            textSenderName.text = "🤖 AI (Gemini)"
                            textSenderName.setTextColor(
                                ContextCompat.getColor(itemView.context, android.R.color.holo_blue_dark)
                            )
                        }
                        ResponseSource.RULE_BASED -> {
                            textSenderName.text = "🤖 AI (Local)"
                            textSenderName.setTextColor(
                                ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
                            )
                        }
                        else -> {
                            textSenderName.text = "🤖 AI Assistant"
                            textSenderName.setTextColor(
                                ContextCompat.getColor(itemView.context, R.color.orange_primary)
                            )
                        }
                    }
                } else {
                    textSenderName?.visibility = View.GONE
                }
            }
        }
    }
    
    // Adapter for suggested questions
    private class SuggestionsAdapter : RecyclerView.Adapter<SuggestionsAdapter.SuggestionViewHolder>() {
        private val suggestions = mutableListOf<String>()
        private var onSuggestionClickListener: ((String) -> Unit)? = null
        
        fun submitList(newSuggestions: List<String>) {
            suggestions.clear()
            suggestions.addAll(newSuggestions)
            notifyDataSetChanged()
        }
        
        fun setOnSuggestionClickListener(listener: (String) -> Unit) {
            onSuggestionClickListener = listener
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_suggestion, parent, false)
            return SuggestionViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
            holder.bind(suggestions[position])
            holder.itemView.setOnClickListener {
                onSuggestionClickListener?.invoke(suggestions[position])
            }
        }
        
        override fun getItemCount() = suggestions.size
        
        class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textSuggestion = itemView.findViewById<TextView>(R.id.textSuggestion)
            
            fun bind(suggestion: String) {
                textSuggestion?.text = suggestion
            }
        }
    }
}
