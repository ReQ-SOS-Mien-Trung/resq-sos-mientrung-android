package com.resq.resq_sos_mientrung_android.bridgefy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

// Wrapper classes for Bridgefy SDK
data class User(
    val userId: String,
    val displayName: String = "" // Tên hiển thị thân thiện
) {
    // Helper để lấy tên hiển thị, fallback về userId rút gọn nếu không có
    fun getDisplayNameOrShortId(): String {
        return if (displayName.isNotBlank()) {
            displayName
        } else {
            // Rút gọn userId để hiển thị
            if (userId.length > 8) "${userId.take(8)}..." else userId
        }
    }
    
    // So sánh dựa trên userId
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return userId == other.userId
    }
    
    override fun hashCode(): Int = userId.hashCode()
}

data class Message(val messageId: String, val content: String)

// Helper object để lấy tên thiết bị
object DeviceNameHelper {
    private var cachedDeviceName: String? = null
    private val bluetoothDeviceCache = mutableMapOf<String, String>()
    
    fun getDeviceName(context: Context?): String {
        cachedDeviceName?.let { return it }
        
        val name = try {
            // Thử lấy tên Bluetooth trước
            val bluetoothName = if (context != null) {
                try {
                    Settings.Secure.getString(context.contentResolver, "bluetooth_name")
                } catch (e: Exception) { null }
            } else null
            
            // Nếu không có, dùng tên thiết bị
            bluetoothName ?: Build.MODEL ?: "Thiết bị ${Build.MANUFACTURER}"
        } catch (e: Exception) {
            "Thiết bị Android"
        }
        
        cachedDeviceName = name
        return name
    }
    
    /**
     * Lấy tên Bluetooth của thiết bị từ userId (có thể là MAC address hoặc ID)
     */
    fun getBluetoothDeviceName(context: Context?, userId: String): String {
        // Kiểm tra cache trước
        bluetoothDeviceCache[userId]?.let { return it }
        
        val name = try {
            if (context == null) {
                return userId.take(12) // Fallback: hiển thị 12 ký tự đầu
            }
            
            // Thử lấy từ BluetoothAdapter
            val bluetoothAdapter = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ cần permission
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                    
                    if (hasPermission) {
                        android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    } else {
                        null
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                }
            } catch (e: Exception) {
                Log.w("DeviceNameHelper", "Cannot get BluetoothAdapter", e)
                null
            }
            
            if (bluetoothAdapter != null) {
                try {
                    // Thử parse userId như MAC address
                    val macAddress = if (userId.matches(Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"))) {
                        userId
                    } else if (userId.length >= 12) {
                        // Thử format lại nếu có thể
                        val cleaned = userId.replace(":", "").replace("-", "")
                        if (cleaned.length >= 12) {
                            "${cleaned.substring(0, 2)}:${cleaned.substring(2, 4)}:${cleaned.substring(4, 6)}:${cleaned.substring(6, 8)}:${cleaned.substring(8, 10)}:${cleaned.substring(10, 12)}"
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                    
                    if (macAddress != null) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (hasPermission) {
                                bluetoothAdapter.getRemoteDevice(macAddress)
                            } else {
                                null
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            bluetoothAdapter.getRemoteDevice(macAddress)
                        }
                        
                        if (device != null) {
                            val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                                
                                if (hasPermission) {
                                    device.name
                                } else {
                                    null
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                device.name
                            }
                            
                            if (!deviceName.isNullOrBlank()) {
                                bluetoothDeviceCache[userId] = deviceName
                                return deviceName
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("DeviceNameHelper", "Error getting device name for $userId", e)
                }
            }
            
            // Fallback: tạo tên thân thiện từ userId
            // Nếu userId trông giống MAC address, chỉ lấy phần cuối
            val friendlyName = if (userId.matches(Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"))) {
                // Lấy 2 octet cuối của MAC address
                val parts = userId.split(":", "-")
                if (parts.size >= 2) {
                    "Thiết bị ${parts[parts.size - 2]}${parts[parts.size - 1]}"
                } else {
                    "Thiết bị ${userId.takeLast(5)}"
                }
            } else if (userId.length > 12) {
                // Nếu là UUID hoặc ID dài, lấy phần cuối
                "Thiết bị ${userId.takeLast(8)}"
            } else if (userId.length > 6) {
                "Thiết bị ${userId.takeLast(6)}"
            } else {
                "Thiết bị $userId"
            }
            
            friendlyName
        } catch (e: Exception) {
            Log.e("DeviceNameHelper", "Error in getBluetoothDeviceName", e)
            // Fallback đơn giản
            if (userId.length > 8) {
                "Thiết bị ${userId.takeLast(8)}"
            } else {
                "Thiết bị $userId"
            }
        }
        
        bluetoothDeviceCache[userId] = name
        return name
    }
}

interface BridgefyDelegate {
    fun onMessageReceived(message: Message, user: User)
    fun onMessageSent(messageId: String)
    fun onMessageFailed(messageId: String, error: String)
    fun onUserFound(user: User)
    fun onUserLost(user: User)
}

object Bridgefy {
    private const val TAG = "Bridgefy"
    private var isInitialized = false
    private var delegate: BridgefyDelegate? = null
    private val handler = Handler(Looper.getMainLooper())
    private val nearbyUsers = mutableListOf<User>()
    private var discoveryHandler: Handler? = null
    private var discoveryRunnable: Runnable? = null
    private var isUsingRealSDK = false
    
    fun initialize(config: BridgefyConfig, listener: BridgefyStartListener) {
        Log.d(TAG, "=== Initializing Bridgefy SDK ===")
        Log.d(TAG, "Config API key: ${config.apiKey.take(8)}..., delegate: ${config.delegate != null}, context: ${config.context != null}")
        
        // Try to use real Bridgefy SDK first
        val context = config.context
        Log.d(TAG, "Step 1: Checking if real SDK is available...")
        val sdkAvailable = BridgefySDKWrapper.isRealSDKAvailable()
        Log.d(TAG, "Step 2: SDK available: $sdkAvailable, context: ${context != null}")
        
        if (context != null && sdkAvailable) {
            Log.d(TAG, "Step 3: Both context and SDK available, checking delegate...")
            val configDelegate = config.delegate
            if (configDelegate == null) {
                Log.e(TAG, "❌ Step 4: Delegate is null, cannot initialize SDK")
            } else {
                Log.d(TAG, "Step 4: Delegate OK, attempting to initialize real SDK...")
                Log.d(TAG, "Step 5: Calling BridgefySDKWrapper.initializeRealSDK()...")
                val success = BridgefySDKWrapper.initializeRealSDK(
                    context,
                    config.apiKey,
                    configDelegate,
                    listener
                )
                Log.d(TAG, "Step 6: SDK initialization result: $success")
                if (success) {
                    isInitialized = true
                    isUsingRealSDK = true
                    delegate = configDelegate
                    Log.d(TAG, "✅ Real Bridgefy SDK initialized - devices will discover each other automatically")
                    return
                } else {
                    Log.w(TAG, "⚠️ SDK class found but initialization failed - will use stub")
                    Log.w(TAG, "⚠️ Check logcat above for detailed error messages")
                }
            }
        } else {
            Log.w(TAG, "⚠️ Step 3 FAILED: Cannot use real SDK - context=${context != null}, sdkAvailable=$sdkAvailable")
            Log.w(TAG, "⚠️ This is why we're using stub instead of real SDK!")
        }
        
        // Fallback to stub implementation
        isUsingRealSDK = false
        Log.w(TAG, "⚠️ Real Bridgefy SDK not available, using stub implementation")
        Log.w(TAG, "⚠️ IMPORTANT: Stub cannot discover devices. Please ensure Bridgefy SDK is properly integrated.")
        handler.postDelayed({
            try {
                isInitialized = true
                delegate = config.delegate
                listener.onBridgefyStart()
                Log.d(TAG, "✅ Bridgefy stub initialized (for testing only)")
                
                // Start simulated device discovery (only for testing)
                startSimulatedDiscovery()
            } catch (e: Exception) {
                listener.onBridgefyStartError(e.message ?: "Unknown error")
            }
        }, 1500)
    }
    
    private fun startSimulatedDiscovery() {
        // Note: This is only for testing. Real Bridgefy SDK will handle discovery automatically.
        // In a real scenario, devices discover each other via Bluetooth/WiFi Direct automatically.
        Log.w(TAG, "⚠️ Using stub implementation - devices won't discover each other automatically")
        Log.w(TAG, "💡 To enable real discovery, ensure Bridgefy SDK AAR is properly added to dependencies")
        Log.w(TAG, "💡 Check that 'me.bridgefy:android-sdk:1.2.3' is correctly configured in build.gradle.kts")
    }
    
    fun sendMessage(userId: String, content: String): String {
        if (!isInitialized) {
            throw IllegalStateException("Bridgefy not initialized")
        }
        
        val messageId = UUID.randomUUID().toString()
        
        // Try real SDK first
        if (BridgefySDKWrapper.isRealSDKAvailable()) {
            val realMessageId = BridgefySDKWrapper.sendMessageRealSDK(userId, content)
            if (realMessageId != null) {
                return realMessageId
            }
        }
        
        // Fallback to stub
        Log.d(TAG, "Using stub sendMessage")
        handler.postDelayed({
            delegate?.onMessageSent(messageId)
        }, 500)
        return messageId
    }
    
    fun getNearbyUsers(): List<User> {
        // Try to get users from real SDK first
        if (isUsingRealSDK && BridgefySDKWrapper.isRealSDKAvailable()) {
            val realUsers = BridgefySDKWrapper.getNearbyUsersRealSDK()
            if (realUsers.isNotEmpty()) {
                return realUsers
            }
        }
        
        // Fallback to stub list
        return nearbyUsers.toList()
    }
    
    fun isUsingRealSDK(): Boolean = isUsingRealSDK
    
    fun addUser(user: User) {
        if (!nearbyUsers.contains(user)) {
            nearbyUsers.add(user)
            delegate?.onUserFound(user)
            Log.d(TAG, "User added: ${user.userId}")
        }
    }
    
    fun removeUser(user: User) {
        if (nearbyUsers.remove(user)) {
            delegate?.onUserLost(user)
            Log.d(TAG, "User removed: ${user.userId}")
        }
    }
}

class BridgefyConfig internal constructor(
    val apiKey: String, 
    val delegate: BridgefyDelegate?,
    val context: Context?
) {
    class Builder {
        private var apiKey: String = ""
        private var delegate: BridgefyDelegate? = null
        private var context: Context? = null
        
        fun setApiKey(key: String): Builder {
            this.apiKey = key
            return this
        }
        
        fun setBridgefyDelegate(delegate: BridgefyDelegate): Builder {
            this.delegate = delegate
            return this
        }
        
        fun setContext(context: Context): Builder {
            this.context = context
            return this
        }
        
        fun build(): BridgefyConfig {
            return BridgefyConfig(apiKey, delegate, context)
        }
    }
}

interface BridgefyStartListener {
    fun onBridgefyStart()
    fun onBridgefyStartError(error: String)
}
