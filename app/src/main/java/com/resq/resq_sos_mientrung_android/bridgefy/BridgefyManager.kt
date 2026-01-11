package com.resq.resq_sos_mientrung_android.bridgefy

import android.content.Context
import android.util.Log
import org.json.JSONObject

class BridgefyManager private constructor(context: Context) : BridgefyDelegate {
    
    companion object {
        private const val TAG = "BridgefyManager"
        private var instance: BridgefyManager? = null
        
        fun getInstance(context: Context): BridgefyManager {
            if (instance == null) {
                instance = BridgefyManager(context.applicationContext)
            }
            return instance!!
        }
    }
    
    private val appContext: Context = context.applicationContext
    private var isInitialized = false
    private var listeners: MutableList<BridgefyListener> = mutableListOf()
    
    // Cache tên thiết bị của mình
    val myDeviceName: String
        get() = DeviceNameHelper.getDeviceName(appContext)
    
    // Cache local user ID (SDK UUID)
    private var cachedLocalUserId: String? = null
    
    // Get local user ID - ưu tiên dùng SDK UUID để khớp với ID từ getNearbyUsers()
    fun getLocalUserId(): String {
        // Return cached value nếu có
        cachedLocalUserId?.let { return it }
        
        // Thử lấy từ SDK trước
        val sdkUserId = BridgefySDKWrapper.getLocalUserId()
        if (sdkUserId != null) {
            cachedLocalUserId = sdkUserId
            Log.d(TAG, "Using SDK local user ID: $sdkUserId")
            return sdkUserId
        }
        
        // Fallback: dùng ANDROID_ID nếu SDK chưa sẵn sàng
        val androidId = android.provider.Settings.Secure.getString(
            appContext.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: java.util.UUID.randomUUID().toString()
        
        Log.d(TAG, "Fallback to ANDROID_ID: $androidId")
        return androidId
    }
    
    fun addListener(listener: BridgefyListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    fun removeListener(listener: BridgefyListener) {
        listeners.remove(listener)
    }
    
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Bridgefy already initialized")
            return
        }
        
        try {
            val config = BridgefyConfig.Builder()
                .setApiKey("5a369f96-13d3-40df-8d41-805bf150cac0")
                .setBridgefyDelegate(this)
                .setContext(context)
                .build()
            
            Log.d(TAG, "Đang khởi động Bridgefy SDK...")
            Log.d(TAG, "Kiểm tra SDK thật có sẵn: ${BridgefySDKWrapper.isRealSDKAvailable()}")
            
            Bridgefy.initialize(config, object : BridgefyStartListener {
                override fun onBridgefyStart() {
                    Log.d(TAG, "✅ Bridgefy đã khởi động thành công!")
                    
                    // Kiểm tra xem có đang dùng SDK thật không
                    // Note: Bridgefy.isUsingRealSDK() sẽ được gọi từ Bridgefy object
                    // Tạm thời kiểm tra qua việc xem có log "Real Bridgefy SDK initialized" không
                    // Hoặc có thể thêm flag vào BridgefyManager
                    
                    if (BridgefySDKWrapper.isRealSDKAvailable()) {
                        // SDK class tồn tại, nhưng cần kiểm tra xem đã khởi tạo thành công chưa
                        // Log sẽ cho biết chi tiết hơn
                        Log.d(TAG, "✅ Bridgefy SDK class found - checking initialization status...")
                        Log.d(TAG, "✅ Đang sử dụng Bridgefy SDK THẬT - thiết bị sẽ tự động tìm thấy nhau")
                        Log.d(TAG, "💡 Đảm bảo cả hai thiết bị đều:")
                        Log.d(TAG, "   - Bật Bluetooth")
                        Log.d(TAG, "   - Bật WiFi (hoặc WiFi Direct)")
                        Log.d(TAG, "   - Ở gần nhau (trong phạm vi ~100m)")
                        Log.d(TAG, "   - Đã cấp đủ quyền (Bluetooth, Location)")
                    } else {
                        Log.w(TAG, "⚠️ Đang sử dụng STUB - thiết bị KHÔNG THỂ tìm thấy nhau!")
                        Log.w(TAG, "⚠️ SDK class được tìm thấy nhưng khởi tạo thất bại")
                        Log.w(TAG, "⚠️ Vui lòng kiểm tra logcat để xem lỗi khởi tạo")
                    }
                    isInitialized = true
                    notifyListeners { it.onBridgefyStart() }
                    
                    // Gửi presence announcement sau khi khởi động
                    // Để các thiết bị khác biết mình đang online
                    sendPresenceAnnouncement()
                }
                
                override fun onBridgefyStartError(error: String) {
                    Log.e(TAG, "❌ Bridgefy start error: $error")
                    Log.e(TAG, "Bridgefy SDK failed to initialize")
                    notifyListeners { it.onBridgefyStartError(error) }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Bridgefy", e)
        }
    }
    
    fun sendMessage(userId: String, content: String): String? {
        if (!isInitialized) {
            Log.e(TAG, "Bridgefy not initialized")
            return null
        }
        
        return try {
            val messageData = JSONObject().apply {
                put("content", content)
                put("timestamp", System.currentTimeMillis())
                put("type", "private") // Mark as private message
                put("senderName", myDeviceName) // Thêm tên người gửi
                put("senderId", getLocalUserId()) // Thêm ID người gửi để người nhận biết ai gửi
                put("targetUserId", userId) // ID người nhận để filter đúng
            }
            
            Log.d(TAG, "Sending private message to $userId from ${getLocalUserId()}")
            val messageId = Bridgefy.sendMessage(userId, messageData.toString())
            Log.d(TAG, "Private message sent with ID: $messageId")
            messageId
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            null
        }
    }
    
    /**
     * Gửi presence announcement để các thiết bị khác biết mình đang online
     * Giải quyết vấn đề "one-way discovery"
     */
    private fun sendPresenceAnnouncement() {
        // Delay một chút để SDK ổn định
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isInitialized) return@postDelayed
            
            Log.d(TAG, "👋 Sending presence announcement...")
            
            try {
                val presenceData = JSONObject().apply {
                    put("type", "presence")
                    put("timestamp", System.currentTimeMillis())
                    put("senderName", myDeviceName)
                    put("senderId", getLocalUserId())
                }
                
                // Gửi presence tới tất cả nearby users đã biết
                val nearbyUsers = getNearbyUsers()
                for (user in nearbyUsers) {
                    try {
                        Bridgefy.sendMessage(user.userId, presenceData.toString())
                        Log.d(TAG, "👋 Presence sent to ${user.userId}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send presence to ${user.userId}: ${e.message}")
                    }
                }
                
                // Nếu chưa có user nào, gửi broadcast presence
                // SDK sẽ tự động discover và gửi tới các thiết bị mới tìm thấy
                if (nearbyUsers.isEmpty()) {
                    Log.d(TAG, "👋 No nearby users yet, will announce when users are found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending presence announcement", e)
            }
        }, 2000) // Delay 2 giây
    }
    
    /**
     * Gửi presence announcement thủ công (có thể gọi từ UI để refresh)
     */
    fun announcePresence() {
        sendPresenceAnnouncement()
    }
    
    /**
     * Phát tín hiệu SOS kèm vị trí GPS thật để các thiết bị khác có thể tìm thấy
     * @param latitude Vĩ độ GPS
     * @param longitude Kinh độ GPS
     * @return List of message IDs for each user
     */
    fun sendSOSSignal(latitude: Double? = null, longitude: Double? = null): List<String> {
        if (!isInitialized) {
            Log.e(TAG, "Bridgefy not initialized")
            return emptyList()
        }
        
        val nearbyUsers = getNearbyUsers()
        if (nearbyUsers.isEmpty()) {
            Log.w(TAG, "No nearby users to send SOS to")
            return emptyList()
        }
        
        val messageIds = mutableListOf<String>()
        
        try {
            val messageData = org.json.JSONObject().apply {
                put("type", "SOS")
                put("timestamp", System.currentTimeMillis())
                put("senderName", myDeviceName)
                put("senderId", getLocalUserId())
                // Thêm vị trí GPS nếu có
                if (latitude != null && longitude != null) {
                    put("latitude", latitude)
                    put("longitude", longitude)
                    Log.d(TAG, "SOS signal với vị trí GPS: lat=$latitude, lng=$longitude")
                }
            }
            
            for (user in nearbyUsers) {
                try {
                    val messageId = Bridgefy.sendMessage(user.userId, messageData.toString())
                    Log.d(TAG, "SOS signal sent to ${user.userId} with ID: $messageId")
                    messageIds.add(messageId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending SOS to ${user.userId}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating SOS message", e)
        }
        
        return messageIds
    }
    
    /**
     * Gửi tin nhắn broadcast tới tất cả người dùng gần đây
     * @return List of message IDs for each user
     */
    fun sendBroadcastMessage(content: String): List<String> {
        if (!isInitialized) {
            Log.e(TAG, "Bridgefy not initialized")
            return emptyList()
        }
        
        val nearbyUsers = getNearbyUsers()
        if (nearbyUsers.isEmpty()) {
            Log.w(TAG, "No nearby users to broadcast to")
            return emptyList()
        }
        
        val messageIds = mutableListOf<String>()
        
        try {
            val messageData = JSONObject().apply {
                put("content", content)
                put("timestamp", System.currentTimeMillis())
                put("type", "broadcast") // Mark as broadcast message
                put("senderName", myDeviceName) // Thêm tên người gửi
            }
            
            for (user in nearbyUsers) {
                try {
                    val messageId = Bridgefy.sendMessage(user.userId, messageData.toString())
                    Log.d(TAG, "Broadcast message sent to ${user.userId} with ID: $messageId")
                    messageIds.add(messageId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending broadcast to ${user.userId}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating broadcast message", e)
        }
        
        return messageIds
    }
    
    fun getNearbyUsers(): List<User> {
        return if (isInitialized) {
            Bridgefy.getNearbyUsers()
        } else {
            emptyList()
        }
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Thử start SDK nếu chưa start.
     * Nên gọi sau khi permissions đã được grant.
     */
    fun tryStartSDK(): Boolean {
        if (!isInitialized) {
            Log.d(TAG, "Cannot start SDK - not initialized yet")
            return false
        }
        
        // Gọi startBridgefySDK() - nó sẽ tự check permissions và isStarted
        val started = BridgefySDKWrapper.startBridgefySDK()
        if (started) {
            Log.d(TAG, "✅ SDK started successfully!")
        } else {
            Log.d(TAG, "⚠️ SDK not started - may be missing permissions or already started")
        }
        return started
    }
    
    /**
     * Kiểm tra SDK đã bắt đầu scan chưa
     */
    fun isStarted(): Boolean = BridgefySDKWrapper.isStarted()
    
    // BridgefyDelegate callbacks
    override fun onMessageReceived(message: Message, user: User) {
        Log.d(TAG, "📨 Message received from ${user.userId}: ${message.content}")
        Log.d(TAG, "Bridgefy đang hoạt động - Nhận tin nhắn thành công")
        
        // Parse JSON để lấy senderId thực sự
        // QUAN TRỌNG: user.userId từ SDK callback có thể là routing node (mesh network)
        // Không phải sender thực sự! Phải dùng senderId từ JSON.
        var actualSenderId: String? = null
        var senderName: String? = null
        var messageType: String? = null
        
        try {
            val json = org.json.JSONObject(message.content)
            actualSenderId = json.optString("senderId", "").takeIf { it.isNotBlank() }
            senderName = json.optString("senderName", "").takeIf { it.isNotBlank() }
            messageType = json.optString("type", "")
            
            Log.d(TAG, "📨 Parsed message - actualSenderId: $actualSenderId, senderName: $senderName, type: $messageType, routingUserId: ${user.userId}")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot parse message JSON: ${e.message}")
        }
        
        // Xử lý presence announcement
        if (messageType == "presence" && actualSenderId != null) {
            Log.d(TAG, "👋 Received presence from $actualSenderId ($senderName)")
            
            // Thêm user với senderId thực sự (không phải routing node)
            val currentUsers = Bridgefy.getNearbyUsers()
            if (!currentUsers.any { it.userId == actualSenderId }) {
                val newUser = User(actualSenderId, senderName ?: "")
                Log.d(TAG, "🔵 Auto-adding actual sender $actualSenderId to nearby list")
                Bridgefy.addUser(newUser)
                listeners.forEach { it.onUserFound(newUser) }
            }
            
            return // Không forward presence messages tới UI
        }
        
        // Với tin nhắn thường, tạo user với senderId thực sự nếu có
        val messageUser = if (actualSenderId != null && actualSenderId != user.userId) {
            Log.d(TAG, "📨 Using actual senderId: $actualSenderId instead of routing userId: ${user.userId}")
            User(actualSenderId, senderName ?: user.displayName)
        } else {
            user
        }
        
        // Auto-add user nếu chưa có (dùng ID đúng)
        val currentUsers = Bridgefy.getNearbyUsers()
        if (!currentUsers.any { it.userId == messageUser.userId }) {
            Log.d(TAG, "🔵 Auto-adding user ${messageUser.userId} to nearby list")
            Bridgefy.addUser(messageUser)
            listeners.forEach { it.onUserFound(messageUser) }
        }
        
        listeners.forEach { it.onMessageReceived(message, messageUser) }
    }
    
    override fun onMessageSent(messageId: String) {
        Log.d(TAG, "✅ Message sent successfully: $messageId")
        Log.d(TAG, "Bridgefy đang hoạt động - Gửi tin nhắn thành công")
        listeners.forEach { it.onMessageSent(messageId) }
    }
    
    override fun onMessageFailed(messageId: String, error: String) {
        Log.e(TAG, "❌ Message failed: $messageId, error: $error")
        Log.e(TAG, "Bridgefy đang hoạt động nhưng gửi tin nhắn thất bại")
        listeners.forEach { it.onMessageFailed(messageId, error) }
    }
    
    override fun onUserFound(user: User) {
        Log.d(TAG, "🔵 User found: ${user.userId}")
        Log.d(TAG, "Bridgefy đang hoạt động - Tìm thấy người dùng mới (THẬT)")
        
        // Kiểm tra xem đã có user này chưa để tránh duplicate
        val currentUsers = Bridgefy.getNearbyUsers()
        if (currentUsers.any { it.userId == user.userId }) {
            Log.d(TAG, "🔵 User ${user.userId} already in nearby list, skipping")
            return
        }
        
        // Add user to Bridgefy's internal list for getNearbyUsers()
        Bridgefy.addUser(user)
        listeners.forEach { it.onUserFound(user) }
        
        // Gửi presence announcement cho user mới tìm thấy
        // Để họ cũng biết mình đang online (giải quyết one-way discovery)
        sendPresenceToUser(user.userId)
    }
    
    /**
     * Gửi presence announcement cho một user cụ thể
     */
    private fun sendPresenceToUser(userId: String) {
        try {
            val presenceData = JSONObject().apply {
                put("type", "presence")
                put("timestamp", System.currentTimeMillis())
                put("senderName", myDeviceName)
                put("senderId", getLocalUserId())
            }
            Bridgefy.sendMessage(userId, presenceData.toString())
            Log.d(TAG, "👋 Presence sent to new user: $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send presence to $userId: ${e.message}")
        }
    }
    
    override fun onUserLost(user: User) {
        Log.d(TAG, "🔴 User lost: ${user.userId}")
        Log.d(TAG, "Bridgefy đang hoạt động - Mất kết nối với người dùng")
        // Remove user from Bridgefy's internal list
        Bridgefy.removeUser(user)
        listeners.forEach { it.onUserLost(user) }
    }
    
    private fun notifyListeners(action: (BridgefyListener) -> Unit) {
        listeners.forEach(action)
    }
    
    // Extension for BridgefyListener to handle start events
    interface BridgefyListener {
        fun onBridgefyStart() {}
        fun onBridgefyStartError(error: String) {}
        fun onUserFound(user: User)
        fun onUserLost(user: User)
        fun onMessageReceived(message: Message, user: User)
        fun onMessageSent(messageId: String)
        fun onMessageFailed(messageId: String, error: String)
    }
}
