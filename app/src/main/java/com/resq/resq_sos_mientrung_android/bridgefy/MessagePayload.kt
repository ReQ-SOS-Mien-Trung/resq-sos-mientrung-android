package com.resq.resq_sos_mientrung_android.bridgefy

import com.google.gson.annotations.SerializedName
import java.util.Date
import java.util.UUID

/**
 * MessageType enum matching iOS implementation
 * CRITICAL: Field names must match iOS exactly for cross-platform compatibility
 */
enum class MessageType {
    @SerializedName("text")
    TEXT,
    
    @SerializedName("sosLocation")
    SOS_LOCATION,
    
    @SerializedName("userInfo")
    USER_INFO
}

/**
 * MessagePayload data class matching iOS JSON format
 * CRITICAL: Field names must match iOS exactly (camelCase)
 * Used for profile broadcasts, chat messages, and SOS signals
 */
data class MessagePayload(
    @SerializedName("type")
    val type: MessageType,
    
    @SerializedName("text")
    val text: String,
    
    @SerializedName("messageId")
    val messageId: UUID,
    
    @SerializedName("timestamp")
    val timestamp: Date,
    
    @SerializedName("senderId")
    val senderId: UUID,
    
    @SerializedName("senderName")
    val senderName: String,
    
    @SerializedName("senderPhone")
    val senderPhone: String,
    
    @SerializedName("channelId")
    val channelId: UUID? = null,
    
    @SerializedName("recipientId")
    val recipientId: UUID? = null,
    
    @SerializedName("latitude")
    val latitude: Double? = null,
    
    @SerializedName("longitude")
    val longitude: Double? = null
)
