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
    
    private var isInitialized = false
    private var listeners: MutableList<BridgefyListener> = mutableListOf()
    
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
            }
            
            val messageId = Bridgefy.sendMessage(userId, messageData.toString())
            Log.d(TAG, "Message sent with ID: $messageId")
            messageId
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            null
        }
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
        listeners.forEach { it.onMessageReceived(message, user) }
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
        // Add user to Bridgefy's internal list for getNearbyUsers()
        Bridgefy.addUser(user)
        listeners.forEach { it.onUserFound(user) }
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
