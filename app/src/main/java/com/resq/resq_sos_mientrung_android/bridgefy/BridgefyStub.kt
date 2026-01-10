package com.resq.resq_sos_mientrung_android.bridgefy

import android.os.Handler
import android.os.Looper
import java.util.UUID

// Stub classes for Bridgefy SDK until it's properly configured
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
    private var isInitialized = false
    private var delegate: BridgefyDelegate? = null
    private val handler = Handler(Looper.getMainLooper())
    private val nearbyUsers = mutableListOf<User>()
    
    fun initialize(config: BridgefyConfig, listener: BridgefyStartListener) {
        // Simulate initialization delay
        handler.postDelayed({
            try {
                isInitialized = true
                delegate = config.delegate
                listener.onBridgefyStart()
                // Note: Real users will be found through onUserFound callback from actual Bridgefy SDK
            } catch (e: Exception) {
                listener.onBridgefyStartError(e.message ?: "Unknown error")
            }
        }, 1500) // Simulate 1.5 second initialization
    }
    
    fun sendMessage(userId: String, content: String): String {
        val messageId = UUID.randomUUID().toString()
        // Simulate message being sent
        handler.postDelayed({
            delegate?.onMessageSent(messageId)
        }, 500)
        return messageId
    }
    
    fun getNearbyUsers(): List<User> {
        // Return only real users found through Bridgefy SDK
        return nearbyUsers.toList()
    }
    
    // Helper method to add users when found (called by real Bridgefy SDK)
    fun addUser(user: User) {
        if (!nearbyUsers.contains(user)) {
            nearbyUsers.add(user)
        }
    }
    
    // Helper method to remove users when lost (called by real Bridgefy SDK)
    fun removeUser(user: User) {
        nearbyUsers.remove(user)
    }
}

class BridgefyConfig private constructor(val apiKey: String, val delegate: BridgefyDelegate?) {
    class Builder {
        private var apiKey: String = ""
        private var delegate: BridgefyDelegate? = null
        
        fun setApiKey(key: String): Builder {
            this.apiKey = key
            return this
        }
        
        fun setBridgefyDelegate(delegate: BridgefyDelegate): Builder {
            this.delegate = delegate
            return this
        }
        
        fun build(): BridgefyConfig {
            return BridgefyConfig(apiKey, delegate)
        }
    }
}

interface BridgefyStartListener {
    fun onBridgefyStart()
    fun onBridgefyStartError(error: String)
}
