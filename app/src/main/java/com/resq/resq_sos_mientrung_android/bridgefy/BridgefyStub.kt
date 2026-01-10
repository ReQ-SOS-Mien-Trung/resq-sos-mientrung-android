package com.resq.resq_sos_mientrung_android.bridgefy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

// Wrapper classes for Bridgefy SDK
data class User(val userId: String)
data class Message(val messageId: String, val content: String)

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
