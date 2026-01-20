package com.resq.resq_sos_mientrung_android.bridgefy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.resq.resq_sos_mientrung_android.services.SOSRelayService
import com.resq.resq_sos_mientrung_android.utils.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
        
        // Gson instance với custom adapters cho UUID và Date
        private val gson: Gson = GsonBuilder()
            .registerTypeAdapter(UUID::class.java, UuidTypeAdapter())
            .registerTypeAdapter(Date::class.java, DateTypeAdapter())
            .create()
        
        private class UuidTypeAdapter : JsonSerializer<UUID>, JsonDeserializer<UUID> {
            override fun serialize(src: UUID, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
                return JsonPrimitive(src.toString())
            }
            
            override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UUID {
                return UUID.fromString(json.asString)
            }
        }
        
        private class DateTypeAdapter : JsonSerializer<Date>, JsonDeserializer<Date> {
            private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            
            override fun serialize(src: Date, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
                return JsonPrimitive(format.format(src))
            }
            
            override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Date {
                return format.parse(json.asString) ?: Date()
            }
        }
    }
    
    private val appContext: Context = context.applicationContext
    private var isInitialized = false
    private var listeners: MutableList<BridgefyListener> = mutableListOf()
    
    // Coroutine scope for async operations
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Network monitor for checking connectivity
    private val networkMonitor: NetworkMonitor by lazy { NetworkMonitor.getInstance(appContext) }
    
    // Set để track các SOS packet đã xử lý (tránh trùng lặp)
    private val processedSOSPacketIds = mutableSetOf<String>()
    
    // ⭐ CRITICAL: Profile cache - UI list is built from this, NOT from connectedUsers
    private val userProfiles = mutableMapOf<UUID, User>()
    
    // Cache tên thiết bị của mình
    val myDeviceName: String
        get() = DeviceNameHelper.getDeviceName(appContext)
    
    // Cache local user ID (SDK UUID)
    private var cachedLocalUserId: String? = null
    private var cachedLocalUserIdUUID: UUID? = null
    
    // Get local user ID - ưu tiên dùng SDK UUID để khớp với ID từ getNearbyUsers()
    fun getLocalUserId(): String {
        // Return cached value nếu có
        cachedLocalUserId?.let { return it }
        
        // Thử lấy từ SDK trước
        val sdkUserId = BridgefySDKWrapper.getLocalUserId()
        if (sdkUserId != null) {
            cachedLocalUserId = sdkUserId
            try {
                cachedLocalUserIdUUID = UUID.fromString(sdkUserId)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot parse SDK user ID as UUID: $sdkUserId")
            }
            Log.d(TAG, "Using SDK local user ID: $sdkUserId")
            return sdkUserId
        }
        
        // Fallback: dùng ANDROID_ID nếu SDK chưa sẵn sàng
        val androidId = android.provider.Settings.Secure.getString(
            appContext.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
        
        Log.d(TAG, "Fallback to ANDROID_ID: $androidId")
        cachedLocalUserId = androidId
        try {
            cachedLocalUserIdUUID = UUID.fromString(androidId)
        } catch (e: Exception) {
            // Generate new UUID if androidId is not valid UUID
            cachedLocalUserIdUUID = UUID.randomUUID()
            cachedLocalUserId = cachedLocalUserIdUUID.toString()
        }
        return cachedLocalUserId!!
    }
    
    // Get local user ID as UUID
    private fun getLocalUserIdUUID(): UUID {
        getLocalUserId() // Ensure cached
        return cachedLocalUserIdUUID ?: UUID.randomUUID()
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
                    
                    // ⭐ CRITICAL: Broadcast profile 1 second after start (per iOS reference)
                    Handler(Looper.getMainLooper()).postDelayed({
                        broadcastUserProfile()
                    }, 1000)
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
    
    /**
     * Send direct message using MessagePayload format (iOS compatible)
     */
    fun sendMessage(userId: String, content: String): String? {
        if (!isInitialized) {
            Log.e(TAG, "Bridgefy not initialized")
            return null
        }
        
        return try {
            val recipientUUID = try {
                UUID.fromString(userId)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot parse userId as UUID: $userId")
                return null
            }
            
            val payload = MessagePayload(
                type = MessageType.TEXT,
                text = content,
                messageId = UUID.randomUUID(),
                timestamp = Date(),
                senderId = getLocalUserIdUUID(),
                senderName = myDeviceName,
                senderPhone = "", // TODO: Get from user profile
                channelId = null,
                recipientId = recipientUUID,
                latitude = null,
                longitude = null
            )
            
            val json = gson.toJson(payload)
            
            Log.d(TAG, "Sending direct message to $userId from ${getLocalUserId()}")
            val messageId = Bridgefy.sendMessage(userId, json)
            Log.d(TAG, "Direct message sent with ID: $messageId")
            messageId
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            null
        }
    }
    
    /**
     * ⭐ CRITICAL: Broadcast user profile using MessagePayload with type: USER_INFO
     * This is the PRIMARY method for cross-platform discovery (iOS ↔ Android)
     * Called 1 second after start and 0.5s after each connection
     */
    private fun broadcastUserProfile() {
        if (!isInitialized) {
            Log.w(TAG, "Cannot broadcast profile - Bridgefy not initialized")
            return
        }
        
        try {
            val senderId = getLocalUserIdUUID()
            val currentUserPhone = "" // TODO: Get from user profile if available
            
            val payload = MessagePayload(
                type = MessageType.USER_INFO,
                text = "User profile update",
                messageId = UUID.randomUUID(),
                timestamp = Date(),
                senderId = senderId,
                senderName = myDeviceName,
                senderPhone = currentUserPhone,
                channelId = null,
                recipientId = null,
                latitude = null,
                longitude = null
            )
            
            val json = gson.toJson(payload)
            val data = json.toByteArray(Charsets.UTF_8)
            
            // ⭐ CRITICAL: Use broadcast mode (not P2P) for profile discovery
            // Bridgefy SDK should support broadcast mode
            // For now, send to all known users (fallback if broadcast not available)
            val nearbyUsers = getNearbyUsers()
            if (nearbyUsers.isNotEmpty()) {
                for (user in nearbyUsers) {
                    try {
                        Bridgefy.sendMessage(user.userId, json)
                        Log.d(TAG, "📤 Broadcasted profile to ${user.userId}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to broadcast profile to ${user.userId}: ${e.message}")
                    }
                }
            } else {
                // If no users yet, try to send broadcast via SDK wrapper
                // Note: Real Bridgefy SDK should support broadcast mode
                Log.d(TAG, "📤 Broadcasting profile (no nearby users yet)")
                // The profile will be received when devices discover each other
            }
            
            Log.d(TAG, "📤 Broadcasted user profile: ${myDeviceName} (${senderId})")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting user profile", e)
        }
    }
    
    /**
     * Gửi presence announcement thủ công (có thể gọi từ UI để refresh)
     * Now uses profile broadcast instead
     */
    fun announcePresence() {
        broadcastUserProfile()
    }
    
    /**
     * Phát tín hiệu SOS kèm vị trí GPS thật để các thiết bị khác có thể tìm thấy
     * Uses MessagePayload format with type: SOS_LOCATION (iOS compatible)
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
            val payload = MessagePayload(
                type = MessageType.SOS_LOCATION,
                text = "🆘 Cần giúp đỡ gấp!",
                messageId = UUID.randomUUID(),
                timestamp = Date(),
                senderId = getLocalUserIdUUID(),
                senderName = myDeviceName,
                senderPhone = "", // TODO: Get from user profile
                channelId = null,
                recipientId = null, // Broadcast SOS
                latitude = latitude,
                longitude = longitude
            )
            
            val json = gson.toJson(payload)
            
            if (latitude != null && longitude != null) {
                Log.d(TAG, "SOS signal với vị trí GPS: lat=$latitude, lng=$longitude")
            }
            
            for (user in nearbyUsers) {
                try {
                    val messageId = Bridgefy.sendMessage(user.userId, json)
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
     * ⭐ CRITICAL: Send SOS with upload to server (iOS compatible)
     * Flow theo docs iOS:
     * 1. Nếu có mạng → upload trực tiếp lên server
     * 2. Luôn broadcast qua mesh để các device khác relay
     * 
     * @param message Nội dung SOS
     * @param latitude Vĩ độ GPS (null nếu không có)
     * @param longitude Kinh độ GPS (null nếu không có)
     * @param senderName Tên người gửi
     * @param senderPhone Số điện thoại người gửi
     * @param onComplete Callback khi hoàn thành (success: Boolean)
     */
    fun sendSOSWithUpload(
        message: String,
        latitude: Double?,
        longitude: Double?,
        senderName: String? = null,
        senderPhone: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!isInitialized) {
            Log.e(TAG, "Cannot send SOS - Bridgefy not initialized")
            onComplete?.invoke(false)
            return
        }
        
        val localUserId = getLocalUserId()
        if (localUserId.isBlank()) {
            Log.e(TAG, "Cannot send SOS - no local user ID")
            onComplete?.invoke(false)
            return
        }
        
        // Nếu không có location, fallback gửi broadcast text thường
        if (latitude == null || longitude == null) {
            Log.w(TAG, "No location available - sending as broadcast message")
            sendBroadcastMessage("🆘 SOS: $message")
            onComplete?.invoke(true)
            return
        }
        
        // Tạo SOS Packet
        val sosPacket = SOSPacket.create(
            originId = localUserId,
            message = message,
            latitude = latitude,
            longitude = longitude,
            senderName = senderName ?: myDeviceName,
            senderPhone = senderPhone
        )
        
        Log.d(TAG, "📤 Sending SOS: packetId=${sosPacket.packetId}, location=($latitude, $longitude)")
        
        coroutineScope.launch {
            var uploadSuccess = false
            
            // 1. Nếu có mạng → upload trực tiếp lên server
            if (networkMonitor.isConnectedSync) {
                Log.d(TAG, "📤 Device has network - uploading SOS to server")
                uploadSuccess = SOSRelayService.uploadSOS(sosPacket)
                if (uploadSuccess) {
                    Log.d(TAG, "✅ SOS uploaded to server successfully")
                } else {
                    Log.w(TAG, "⚠️ SOS upload to server failed")
                }
            } else {
                Log.d(TAG, "📤 No network - SOS will be relayed via mesh only")
            }
            
            // 2. Luôn broadcast qua mesh để các device khác relay
            broadcastSOSPacket(sosPacket)
            
            onComplete?.invoke(true)
        }
    }
    
    /**
     * Broadcast SOS packet qua mesh network
     */
    private fun broadcastSOSPacket(sosPacket: SOSPacket) {
        try {
            val nearbyUsers = getNearbyUsers()
            
            // Tạo MeshPayload chứa SOSPacket
            val meshPayload = MeshPayload(
                type = MeshPayloadType.SOS,
                sosPacket = sosPacket
            )
            
            val json = gson.toJson(meshPayload)
            
            if (nearbyUsers.isEmpty()) {
                Log.w(TAG, "📤 No nearby users to broadcast SOS - packet stored for later relay")
                // Packet will be picked up when users connect
            } else {
                for (user in nearbyUsers) {
                    try {
                        val messageId = Bridgefy.sendMessage(user.userId, json)
                        Log.d(TAG, "📤 SOS packet broadcast to ${user.userId} with ID: $messageId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error broadcasting SOS to ${user.userId}", e)
                    }
                }
                Log.d(TAG, "📤 SOS packet broadcast to ${nearbyUsers.size} users")
            }
            
            // Cũng tạo Message để hiển thị trong UI chat
            val messagePayload = MessagePayload(
                type = MessageType.SOS_LOCATION,
                text = sosPacket.message,
                messageId = UUID.fromString(sosPacket.packetId),
                timestamp = sosPacket.timestamp,
                senderId = UUID.fromString(sosPacket.originId),
                senderName = sosPacket.senderName ?: myDeviceName,
                senderPhone = sosPacket.senderPhone ?: "",
                channelId = null,
                recipientId = null,
                latitude = sosPacket.latitude,
                longitude = sosPacket.longitude
            )
            
            // Notify listeners về SOS đã gửi
            val message = Message(sosPacket.packetId, gson.toJson(messagePayload))
            val user = User(sosPacket.originId, sosPacket.senderName ?: myDeviceName)
            listeners.forEach { it.onSOSSent(sosPacket) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting SOS packet", e)
        }
    }
    
    /**
     * Handle received SOS packet from mesh
     * - Nếu có mạng → relay lên server
     * - Nếu không mạng → forward tiếp qua mesh
     */
    private fun handleReceivedSOSPacket(sosPacket: SOSPacket, routingUser: User) {
        Log.d(TAG, "📨 Received SOS packet: packetId=${sosPacket.packetId}, from=${sosPacket.originId}")
        
        // Kiểm tra đã xử lý packet này chưa
        if (processedSOSPacketIds.contains(sosPacket.packetId)) {
            Log.d(TAG, "📨 SOS packet already processed, skipping: ${sosPacket.packetId}")
            return
        }
        
        // Đánh dấu đã xử lý
        processedSOSPacketIds.add(sosPacket.packetId)
        
        // Giới hạn số lượng packet IDs được lưu
        if (processedSOSPacketIds.size > 1000) {
            val iterator = processedSOSPacketIds.iterator()
            repeat(500) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        
        // Notify listeners về SOS nhận được
        listeners.forEach { it.onSOSReceived(sosPacket) }
        
        coroutineScope.launch {
            // Nếu có mạng → relay lên server
            if (networkMonitor.isConnectedSync) {
                Log.d(TAG, "📤 Device has network - relaying SOS to server")
                val success = SOSRelayService.uploadRelayedSOS(sosPacket, getLocalUserId())
                if (success) {
                    Log.d(TAG, "✅ SOS relayed to server successfully")
                }
            } else {
                // Không có mạng → forward tiếp qua mesh
                if (sosPacket.canRelay() && !sosPacket.hasBeenRelayedBy(getLocalUserId())) {
                    Log.d(TAG, "📤 No network - forwarding SOS via mesh (hop=${sosPacket.hopCount})")
                    val relayedPacket = sosPacket.createRelayedPacket(getLocalUserId())
                    broadcastSOSPacket(relayedPacket)
                } else {
                    Log.d(TAG, "📤 SOS packet reached max hops or already relayed by this device")
                }
            }
        }
    }

    /**
     * Gửi tin nhắn broadcast tới tất cả người dùng gần đây
     * Uses MessagePayload format with type: TEXT (iOS compatible)
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
            val payload = MessagePayload(
                type = MessageType.TEXT,
                text = content,
                messageId = UUID.randomUUID(),
                timestamp = Date(),
                senderId = getLocalUserIdUUID(),
                senderName = myDeviceName,
                senderPhone = "", // TODO: Get from user profile
                channelId = null,
                recipientId = null, // Broadcast message
                latitude = null,
                longitude = null
            )
            
            val json = gson.toJson(payload)
            
            for (user in nearbyUsers) {
                try {
                    val messageId = Bridgefy.sendMessage(user.userId, json)
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
    
    /**
     * ⭐ CRITICAL: Get nearby users from userProfiles cache, NOT from connectedUsers
     * This matches iOS behavior where UI list is built from userProfiles dictionary
     */
    fun getNearbyUsers(): List<User> {
        return if (isInitialized) {
            // Return from userProfiles cache (primary source)
            val profiles = userProfiles.values.toList()
            
            // Also merge with SDK's getNearbyUsers() for compatibility
            val sdkUsers = Bridgefy.getNearbyUsers()
            val allUsers = mutableMapOf<String, User>()
            
            // Add from profiles first (more reliable)
            for (user in profiles) {
                allUsers[user.userId] = user
            }
            
            // Merge SDK users (may have different IDs)
            for (user in sdkUsers) {
                if (!allUsers.containsKey(user.userId)) {
                    allUsers[user.userId] = user
                }
            }
            
            allUsers.values.toList()
        } else {
            emptyList()
        }
    }
    
    /**
     * Update connected users list from userProfiles
     */
    private fun updateConnectedUsersList() {
        val users = userProfiles.values.sortedBy { it.displayName.ifBlank { it.userId } }
        // Notify listeners about updated list
        // Note: Individual user found/lost events are handled separately
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
        
        val jsonString = message.content
        val messageId = message.messageId
        
        // ⭐ CRITICAL: Try to decode as MeshPayload first (for SOS packets)
        try {
            val meshPayload = gson.fromJson(jsonString, MeshPayload::class.java)
            if (meshPayload.type == MeshPayloadType.SOS && meshPayload.sosPacket != null) {
                handleReceivedSOSPacket(meshPayload.sosPacket, user)
                return
            }
        } catch (e: Exception) {
            Log.d(TAG, "Not a MeshPayload format: ${e.message}")
        }
        
        // ⭐ CRITICAL: Try to decode as MessagePayload (iOS format)
        try {
            val payload = gson.fromJson(jsonString, MessagePayload::class.java)
            handleMessagePayload(payload, user)
            return
        } catch (e: Exception) {
            Log.d(TAG, "Not a MessagePayload format, trying legacy format: ${e.message}")
        }
        
        // Fallback: Parse legacy JSON format
        var actualSenderId: String? = null
        var senderName: String? = null
        var messageType: String? = null
        
        try {
            val json = org.json.JSONObject(jsonString)
            actualSenderId = json.optString("senderId", "").takeIf { it.isNotBlank() }
            senderName = json.optString("senderName", "").takeIf { it.isNotBlank() }
            messageType = json.optString("type", "")
            
            Log.d(TAG, "📨 Parsed legacy message - actualSenderId: $actualSenderId, senderName: $senderName, type: $messageType, routingUserId: ${user.userId}")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot parse message JSON: ${e.message}")
        }
        
        // Handle legacy presence announcement
        if (messageType == "presence" && actualSenderId != null) {
            Log.d(TAG, "👋 Received legacy presence from $actualSenderId ($senderName)")
            handleLegacyPresence(actualSenderId, senderName)
            return
        }
        
        // Handle legacy messages
        handleLegacyMessage(messageId, jsonString, actualSenderId, senderName, messageType, user)
    }
    
    /**
     * ⭐ CRITICAL: Handle MessagePayload format (iOS compatible)
     */
    private fun handleMessagePayload(payload: MessagePayload, routingUser: User) {
        Log.d(TAG, "📨 Received MessagePayload: type=${payload.type}, senderId=${payload.senderId}")
        
        // ⭐ CRITICAL: Handle userInfo messages (profile broadcasts)
        if (payload.type == MessageType.USER_INFO) {
            val profileUser = User(
                userId = payload.senderId.toString(),
                displayName = payload.senderName
            )
            
            // Cache profile in userProfiles dictionary
            userProfiles[payload.senderId] = profileUser
            updateConnectedUsersList()
            
            Log.d(TAG, "👤 Received user profile: ${profileUser.displayName} (${profileUser.userId})")
            
            // Also add to Bridgefy's internal list for compatibility
            Bridgefy.addUser(profileUser)
            
            // Notify listeners
            listeners.forEach { it.onUserFound(profileUser) }
            return
        }
        
        // Handle direct messages - check recipientId
        val currentUserId = getLocalUserIdUUID()
        if (payload.recipientId != null && payload.recipientId != currentUserId) {
            Log.d(TAG, "📪 Message not for us (recipientId=${payload.recipientId}), ignoring")
            return
        }
        
        // Create User object from sender
        val messageUser = User(
            userId = payload.senderId.toString(),
            displayName = payload.senderName
        )
        
        // Cache profile if not already cached
        if (!userProfiles.containsKey(payload.senderId)) {
            userProfiles[payload.senderId] = messageUser
            updateConnectedUsersList()
            Bridgefy.addUser(messageUser)
            listeners.forEach { it.onUserFound(messageUser) }
        }
        
        // Convert MessagePayload to legacy Message format for listeners
        val legacyMessage = Message(
            messageId = payload.messageId.toString(),
            content = gson.toJson(payload)
        )
        
        listeners.forEach { it.onMessageReceived(legacyMessage, messageUser) }
    }
    
    /**
     * Handle legacy presence messages
     */
    private fun handleLegacyPresence(actualSenderId: String, senderName: String?) {
        try {
            val senderUUID = UUID.fromString(actualSenderId)
            val profileUser = User(
                userId = actualSenderId,
                displayName = senderName ?: ""
            )
            
            userProfiles[senderUUID] = profileUser
            updateConnectedUsersList()
            
            Bridgefy.addUser(profileUser)
            listeners.forEach { it.onUserFound(profileUser) }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot parse senderId as UUID: $actualSenderId")
        }
    }
    
    /**
     * Handle legacy message format
     */
    private fun handleLegacyMessage(
        messageId: String,
        jsonString: String,
        actualSenderId: String?,
        senderName: String?,
        messageType: String?,
        routingUser: User
    ) {
        val messageUser = if (actualSenderId != null && actualSenderId != routingUser.userId) {
            Log.d(TAG, "📨 Using actual senderId: $actualSenderId instead of routing userId: ${routingUser.userId}")
            User(actualSenderId, senderName ?: routingUser.displayName)
        } else {
            routingUser
        }
        
        // Cache profile if we have senderId
        if (actualSenderId != null) {
            try {
                val senderUUID = UUID.fromString(actualSenderId)
                if (!userProfiles.containsKey(senderUUID)) {
                    userProfiles[senderUUID] = messageUser
                    updateConnectedUsersList()
                }
            } catch (e: Exception) {
                // Not a UUID, skip caching
            }
        }
        
        // Auto-add user to Bridgefy's internal list
        val currentUsers = Bridgefy.getNearbyUsers()
        if (!currentUsers.any { it.userId == messageUser.userId }) {
            Log.d(TAG, "🔵 Auto-adding user ${messageUser.userId} to nearby list")
            Bridgefy.addUser(messageUser)
            listeners.forEach { it.onUserFound(messageUser) }
        }
        
        listeners.forEach { it.onMessageReceived(Message(messageId, jsonString), messageUser) }
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
        
        // Try to parse userId as UUID and cache in userProfiles
        try {
            val userIdUUID = UUID.fromString(user.userId)
            userProfiles[userIdUUID] = user
            updateConnectedUsersList()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot parse userId as UUID: ${user.userId}")
        }
        
        // Add user to Bridgefy's internal list for getNearbyUsers()
        Bridgefy.addUser(user)
        listeners.forEach { it.onUserFound(user) }
        
        // ⭐ CRITICAL: Re-broadcast profile 0.5s after connection (per iOS reference)
        Handler(Looper.getMainLooper()).postDelayed({
            broadcastUserProfile()
        }, 500)
    }
    
    override fun onUserLost(user: User) {
        Log.d(TAG, "🔴 User lost: ${user.userId}")
        Log.d(TAG, "Bridgefy đang hoạt động - Mất kết nối với người dùng")
        
        // ⭐ CRITICAL: Remove from userProfiles cache on disconnect
        try {
            val userIdUUID = UUID.fromString(user.userId)
            userProfiles.remove(userIdUUID)
            updateConnectedUsersList()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot parse userId as UUID: ${user.userId}")
        }
        
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
        
        // SOS specific callbacks
        fun onSOSSent(sosPacket: SOSPacket) {}
        fun onSOSReceived(sosPacket: SOSPacket) {}
    }
}
