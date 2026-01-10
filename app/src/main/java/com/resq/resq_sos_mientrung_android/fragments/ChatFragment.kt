package com.resq.resq_sos_mientrung_android.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    
    // null = broadcast mode, non-null = private chat with specific user
    private var selectedUserId: String? = null
    private var selectedUserDisplayName: String? = null
    private var isBroadcastMode: Boolean = true
    
    // Cache mapping userId -> displayName
    private val userDisplayNames = mutableMapOf<String, String>()
    
    data class ChatMessage(
        val id: String,
        val content: String,
        val userId: String, // "broadcast" for broadcast messages, or specific userId
        val senderName: String, // Tên hiển thị của người gửi
        val timestamp: Long,
        val isSent: Boolean,
        val isBroadcast: Boolean = false
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
        setupChatModeUI()
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
                    updateUserCount()
                }
            }
        }
        handler.post(statusChecker)
        
        // Periodically update user count
        val userCountChecker = object : Runnable {
            override fun run() {
                if (isAdded && _binding != null) {
                    updateUserCount()
                    handler.postDelayed(this, 2000)
                }
            }
        }
        handler.postDelayed(userCountChecker, 2000)
        
        updateMessagesList()
    }
    
    private fun setupChatModeUI() {
        // Default to broadcast mode
        setBroadcastMode()
        
        // Click on chat mode layout to toggle or show options
        binding.layoutChatMode.setOnClickListener {
            if (!isBroadcastMode) {
                // Currently in private mode, switch to broadcast
                setBroadcastMode()
            }
            // If already in broadcast mode, do nothing (users click from UsersFragment to go private)
        }
        
        // Clear private chat button
        binding.buttonClearPrivateChat.setOnClickListener {
            setBroadcastMode()
        }
    }
    
    private fun setBroadcastMode() {
        isBroadcastMode = true
        selectedUserId = null
        selectedUserDisplayName = null
        
        binding.iconChatMode.setImageResource(R.drawable.ic_broadcast)
        binding.textChatMode.text = "Broadcast - Gửi tới tất cả"
        binding.buttonClearPrivateChat.visibility = View.GONE
        
        updateUserCount()
        updateMessagesList()
        
        android.util.Log.d("ChatFragment", "Switched to BROADCAST mode")
    }
    
    fun setPrivateChatMode(userId: String, displayName: String? = null) {
        isBroadcastMode = false
        selectedUserId = userId
        selectedUserDisplayName = displayName ?: userDisplayNames[userId]
        
        binding.iconChatMode.setImageResource(R.drawable.ic_person)
        
        // Hiển thị tên thân thiện thay vì userId
        val friendlyName = selectedUserDisplayName ?: run {
            if (userId.length > 8) "${userId.take(8)}..." else userId
        }
        binding.textChatMode.text = "Chat riêng với: $friendlyName"
        binding.buttonClearPrivateChat.visibility = View.VISIBLE
        binding.textUserCount.text = "1 người"
        
        updateMessagesList()
        
        android.util.Log.d("ChatFragment", "Switched to PRIVATE mode with user: $userId ($friendlyName)")
    }
    
    private fun updateUserCount() {
        if (!isAdded || _binding == null) return
        
        if (isBroadcastMode) {
            val nearbyUsers = bridgefyManager.getNearbyUsers()
            binding.textUserCount.text = "${nearbyUsers.size} người"
        }
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
            if (messageText.isEmpty()) return@setOnClickListener
            
            if (isBroadcastMode) {
                // BROADCAST MODE - send to all nearby users
                sendBroadcastMessage(messageText)
            } else {
                // PRIVATE MODE - send to specific user
                sendPrivateMessage(messageText)
            }
        }
    }
    
    private fun sendBroadcastMessage(messageText: String) {
        val nearbyUsers = bridgefyManager.getNearbyUsers()
        
        if (nearbyUsers.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Chưa có người dùng nào gần đây để gửi broadcast.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        val messageIds = bridgefyManager.sendBroadcastMessage(messageText)
        
        if (messageIds.isNotEmpty()) {
            // Add broadcast message to local list
            val chatMessage = ChatMessage(
                id = messageIds.first(), // Use first ID as reference
                content = messageText,
                userId = "broadcast",
                senderName = bridgefyManager.myDeviceName, // Tên của mình
                timestamp = System.currentTimeMillis(),
                isSent = true,
                isBroadcast = true
            )
            messages.add(chatMessage)
            updateMessagesList()
            binding.editTextMessage.text?.clear()
            
            Toast.makeText(
                requireContext(),
                "Đã gửi tới ${nearbyUsers.size} người",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                requireContext(),
                "Không thể gửi tin nhắn broadcast. Vui lòng thử lại.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun sendPrivateMessage(messageText: String) {
        if (selectedUserId == null) {
            Toast.makeText(
                requireContext(),
                "Chưa chọn người dùng để nhắn tin.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        val messageId = bridgefyManager.sendMessage(selectedUserId!!, messageText)
        
        if (messageId != null) {
            // Add message to local list optimistically
            val chatMessage = ChatMessage(
                id = messageId,
                content = messageText,
                userId = selectedUserId!!,
                senderName = bridgefyManager.myDeviceName, // Tên của mình
                timestamp = System.currentTimeMillis(),
                isSent = true,
                isBroadcast = false
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
    }
    
    private fun updateMessagesList() {
        // Filter messages based on current mode
        val filteredMessages = when {
            isBroadcastMode -> {
                // In broadcast mode, show all messages (both broadcast and private)
                messages.toList()
            }
            selectedUserId != null -> {
                // In private mode, show only messages from/to this user
                messages.filter { 
                    it.userId == selectedUserId || 
                    (it.isSent && !it.isBroadcast && it.userId == selectedUserId)
                }
            }
            else -> {
                messages.toList()
            }
        }
        
        if (filteredMessages.isEmpty()) {
            binding.layoutEmptyStateChat.visibility = View.VISIBLE
            binding.recyclerViewMessages.visibility = View.GONE
            
            // Update empty state text based on mode
            if (isBroadcastMode) {
                binding.textNoMessages.text = "Chưa có tin nhắn"
                binding.textEmptySubtitleChat.text = "Gửi tin nhắn broadcast tới tất cả người dùng gần đây"
            } else {
                binding.textNoMessages.text = "Chưa có tin nhắn"
                binding.textEmptySubtitleChat.text = "Bắt đầu cuộc trò chuyện với người dùng này"
            }
        } else {
            binding.layoutEmptyStateChat.visibility = View.GONE
            binding.recyclerViewMessages.visibility = View.VISIBLE
            messagesAdapter.submitList(filteredMessages)
            binding.recyclerViewMessages.scrollToPosition(filteredMessages.size - 1)
        }
    }
    
    // BridgefyListener callbacks
    override fun onBridgefyStart() {
        android.util.Log.d("ChatFragment", "Bridgefy đã khởi động thành công!")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            updateBridgefyStatus()
            updateUserCount()
            Toast.makeText(
                requireContext(),
                "Bridgefy đã sẵn sàng! Có thể nhắn tin offline.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onBridgefyStartError(error: String) {
        android.util.Log.e("ChatFragment", "Lỗi khởi động Bridgefy: $error")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
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
    }
    
    override fun onUserFound(user: User) {
        android.util.Log.d("ChatFragment", "Tìm thấy người dùng: ${user.userId}")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            updateUserCount()
        }
    }
    
    override fun onUserLost(user: User) {
        android.util.Log.d("ChatFragment", "Mất kết nối với người dùng: ${user.userId}")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            updateUserCount()
            
            // If we're in private chat with this user, notify
            if (!isBroadcastMode && selectedUserId == user.userId) {
                Toast.makeText(
                    requireContext(),
                    "Người dùng đã ngắt kết nối",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onMessageReceived(message: Message, user: User) {
        android.util.Log.d("ChatFragment", "Nhận tin nhắn từ ${user.userId}: ${message.content}")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            try {
                val messageData = JSONObject(message.content)
                val content = messageData.getString("content")
                val timestamp = messageData.optLong("timestamp", System.currentTimeMillis())
                val type = messageData.optString("type", "private")
                val senderName = messageData.optString("senderName", "")
                
                // Lưu mapping userId -> displayName
                if (senderName.isNotBlank()) {
                    userDisplayNames[user.userId] = senderName
                }
                
                val displayName = senderName.ifBlank { 
                    user.displayName.ifBlank { 
                        if (user.userId.length > 8) "${user.userId.take(8)}..." else user.userId
                    }
                }
                
                val chatMessage = ChatMessage(
                    id = message.messageId,
                    content = content,
                    userId = user.userId,
                    senderName = displayName,
                    timestamp = timestamp,
                    isSent = false,
                    isBroadcast = type == "broadcast"
                )
                
                messages.add(chatMessage)
                updateMessagesList()
                
                // Show notification if not currently viewing this conversation
                if (isBroadcastMode || selectedUserId != user.userId) {
                    Toast.makeText(
                        requireContext(),
                        "Tin nhắn mới từ $displayName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                // If not JSON, treat as plain text
                val displayName = user.displayName.ifBlank { 
                    if (user.userId.length > 8) "${user.userId.take(8)}..." else user.userId
                }
                val chatMessage = ChatMessage(
                    id = message.messageId,
                    content = message.content,
                    userId = user.userId,
                    senderName = displayName,
                    timestamp = System.currentTimeMillis(),
                    isSent = false,
                    isBroadcast = false
                )
                messages.add(chatMessage)
                updateMessagesList()
            }
        }
    }
    
    override fun onMessageSent(messageId: String) {
        android.util.Log.d("ChatFragment", "Tin nhắn đã gửi thành công: $messageId")
        // Message already added optimistically
    }
    
    override fun onMessageFailed(messageId: String, error: String) {
        android.util.Log.e("ChatFragment", "Gửi tin nhắn thất bại: $messageId, lỗi: $error")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            // Remove failed message from list
            messages.removeAll { it.id == messageId }
            updateMessagesList()
            Toast.makeText(
                requireContext(),
                "Gửi tin nhắn thất bại: $error",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bridgefyManager.removeListener(this)
        _binding = null
    }
    
    /**
     * Set the selected user ID for private chat
     * This method can be called from outside to start a private chat with a specific user
     */
    fun setSelectedUser(userId: String, displayName: String? = null) {
        setPrivateChatMode(userId, displayName)
    }
    
    /**
     * Switch to broadcast mode
     * This method can be called from outside
     */
    fun switchToBroadcast() {
        setBroadcastMode()
    }
    
    // Adapter for messages list
    private class MessagesAdapter : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {
        private val messages = mutableListOf<ChatMessage>()
        
        fun submitList(newMessages: List<ChatMessage>) {
            messages.clear()
            messages.addAll(newMessages)
            notifyDataSetChanged()
        }
        
        override fun getItemViewType(position: Int): Int {
            return if (messages[position].isSent) 0 else 1
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val layoutId = if (viewType == 0) R.layout.item_message else R.layout.item_message_received
            val view = LayoutInflater.from(parent.context)
                .inflate(layoutId, parent, false)
            return MessageViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messages[position]
            holder.bind(message)
        }
        
        override fun getItemCount() = messages.size
        
        class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textMessageContent = itemView.findViewById<TextView>(R.id.textMessageContent)
            private val textMessageTime = itemView.findViewById<TextView>(R.id.textMessageTime)
            private val textSenderName = itemView.findViewById<TextView>(R.id.textSenderName)
            
            fun bind(message: ChatMessage) {
                textMessageContent?.text = message.content
                textMessageTime?.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(message.timestamp))
                
                // Show sender info for received messages
                if (!message.isSent && textSenderName != null) {
                    textSenderName.visibility = View.VISIBLE
                    // Hiển thị tên thân thiện thay vì userId
                    val displayName = message.senderName
                    textSenderName.text = if (message.isBroadcast) "📢 $displayName" else displayName
                } else {
                    textSenderName?.visibility = View.GONE
                }
            }
        }
    }
}
