package com.resq.resq_sos_mientrung_android.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.resq.resq_sos_mientrung_android.R
import com.resq.resq_sos_mientrung_android.bridgefy.BridgefyManager
import com.resq.resq_sos_mientrung_android.bridgefy.Message
import com.resq.resq_sos_mientrung_android.bridgefy.User
import com.resq.resq_sos_mientrung_android.databinding.FragmentChatBinding
import org.json.JSONObject

class ChatFragment : Fragment(), BridgefyManager.BridgefyListener {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var bridgefyManager: BridgefyManager
    private val messagesAdapter = MessagesAdapter()
    private val messages = mutableListOf<ChatMessage>()
    private var selectedUserId: String? = null
    
    data class ChatMessage(
        val id: String,
        val content: String,
        val userId: String,
        val timestamp: Long,
        val isSent: Boolean
    )
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bridgefyManager = BridgefyManager.getInstance(requireContext())
        bridgefyManager.addListener(this)
        
        setupRecyclerView()
        setupSendButton()
        updateBridgefyStatus()
        
        // Periodically check status (every 500ms) until initialized
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val statusChecker = object : Runnable {
            override fun run() {
                if (!bridgefyManager.isInitialized()) {
                    updateBridgefyStatus()
                    handler.postDelayed(this, 500)
                } else {
                    updateBridgefyStatus()
                    // Auto-select first nearby user if available
                    val nearbyUsers = bridgefyManager.getNearbyUsers()
                    if (nearbyUsers.isNotEmpty() && selectedUserId == null) {
                        selectedUserId = nearbyUsers[0].userId
                    }
                }
            }
        }
        handler.post(statusChecker)
        
        updateMessagesList()
    }
    
    private fun updateBridgefyStatus() {
        if (bridgefyManager.isInitialized()) {
            binding.statusIndicatorChat.setBackgroundResource(R.drawable.bridgefy_status_indicator_active)
            binding.textBridgefyStatusChat.text = "Đang hoạt động"
            binding.textBridgefyStatusChat.setTextColor(requireContext().getColor(R.color.nav_selected))
        } else {
            binding.statusIndicatorChat.setBackgroundResource(R.drawable.bridgefy_status_indicator)
            binding.textBridgefyStatusChat.text = "Đang khởi động..."
            binding.textBridgefyStatusChat.setTextColor(requireContext().getColor(R.color.nav_unselected))
        }
    }
    
    private fun setupRecyclerView() {
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewMessages.adapter = messagesAdapter
    }
    
    private fun setupSendButton() {
        binding.buttonSend.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                if (selectedUserId != null) {
                    val messageId = bridgefyManager.sendMessage(selectedUserId!!, messageText)
                    if (messageId != null) {
                        // Add message to local list optimistically
                        val chatMessage = ChatMessage(
                            id = messageId,
                            content = messageText,
                            userId = selectedUserId!!,
                            timestamp = System.currentTimeMillis(),
                            isSent = true
                        )
                        messages.add(chatMessage)
                        updateMessagesList()
                        binding.editTextMessage.text?.clear()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Không thể gửi tin nhắn. Vui lòng thử lại.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Chưa có người dùng nào gần đây. Vui lòng đợi...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun updateMessagesList() {
        if (messages.isEmpty()) {
            binding.textNoMessages.visibility = View.VISIBLE
            binding.recyclerViewMessages.visibility = View.GONE
        } else {
            binding.textNoMessages.visibility = View.GONE
            binding.recyclerViewMessages.visibility = View.VISIBLE
            messagesAdapter.submitList(messages.toList())
            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
        }
    }
    
    // BridgefyListener callbacks
    override fun onBridgefyStart() {
        android.util.Log.d("ChatFragment", "Bridgefy đã khởi động thành công!")
        updateBridgefyStatus()
        // Refresh nearby users
        val nearbyUsers = bridgefyManager.getNearbyUsers()
        if (nearbyUsers.isNotEmpty() && selectedUserId == null) {
            selectedUserId = nearbyUsers[0].userId
        }
        Toast.makeText(
            requireContext(),
            "Bridgefy đã sẵn sàng! Có thể nhắn tin offline.",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onBridgefyStartError(error: String) {
        android.util.Log.e("ChatFragment", "Lỗi khởi động Bridgefy: $error")
        updateBridgefyStatus()
        binding.statusIndicatorChat.setBackgroundResource(R.drawable.bridgefy_status_indicator)
        binding.textBridgefyStatusChat.text = "Lỗi: $error"
        binding.textBridgefyStatusChat.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
        Toast.makeText(
            requireContext(),
            "Lỗi khởi động Bridgefy: $error",
            Toast.LENGTH_LONG
        ).show()
    }
    
    override fun onUserFound(user: User) {
        android.util.Log.d("ChatFragment", "Tìm thấy người dùng: ${user.userId}")
        if (selectedUserId == null) {
            selectedUserId = user.userId
            Toast.makeText(
                requireContext(),
                "Đã kết nối với người dùng! Có thể bắt đầu chat.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onUserLost(user: User) {
        android.util.Log.d("ChatFragment", "Mất kết nối với người dùng: ${user.userId}")
        if (selectedUserId == user.userId) {
            selectedUserId = null
            Toast.makeText(
                requireContext(),
                "Người dùng đã ngắt kết nối",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onMessageReceived(message: Message, user: User) {
        android.util.Log.d("ChatFragment", "Nhận tin nhắn từ ${user.userId}: ${message.content}")
        try {
            val messageData = JSONObject(message.content)
            val content = messageData.getString("content")
            val timestamp = messageData.optLong("timestamp", System.currentTimeMillis())
            
            val chatMessage = ChatMessage(
                id = message.messageId,
                content = content,
                userId = user.userId,
                timestamp = timestamp,
                isSent = false
            )
            
            messages.add(chatMessage)
            updateMessagesList()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onMessageSent(messageId: String) {
        android.util.Log.d("ChatFragment", "Tin nhắn đã gửi thành công: $messageId")
        // Message already added optimistically
    }
    
    override fun onMessageFailed(messageId: String, error: String) {
        android.util.Log.e("ChatFragment", "Gửi tin nhắn thất bại: $messageId, lỗi: $error")
        // Remove failed message from list
        messages.removeAll { it.id == messageId }
        updateMessagesList()
        Toast.makeText(
            requireContext(),
            "Gửi tin nhắn thất bại: $error",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bridgefyManager.removeListener(this)
        _binding = null
    }
    
    // Adapter for messages list
    private class MessagesAdapter : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {
        private val messages = mutableListOf<ChatMessage>()
        
        fun submitList(newMessages: List<ChatMessage>) {
            messages.clear()
            messages.addAll(newMessages)
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return MessageViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messages[position]
            holder.bind(message)
        }
        
        override fun getItemCount() = messages.size
        
        class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            fun bind(message: ChatMessage) {
                val text1 = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
                val text2 = itemView.findViewById<android.widget.TextView>(android.R.id.text2)
                
                val prefix = if (message.isSent) "Bạn: " else "Người khác: "
                text1?.text = "$prefix${message.content}"
                text2?.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(message.timestamp))
                
                if (message.isSent) {
                    text1?.setTextColor(itemView.context.getColor(R.color.orange_primary))
                } else {
                    text1?.setTextColor(itemView.context.getColor(R.color.black))
                }
                text2?.setTextColor(itemView.context.getColor(R.color.nav_unselected))
            }
        }
    }
}
