package com.resq.resq_sos_mientrung_android.bridgefy

import com.google.gson.annotations.SerializedName
import java.util.Date
import java.util.UUID

/**
 * SOS Packet data model - Matches iOS SOSPacket structure for cross-platform compatibility
 * 
 * Used for:
 * - Sending SOS signals via mesh network
 * - Uploading SOS to server
 * - Relay mechanism between devices
 */
data class SOSPacket(
    @SerializedName("packetId")
    val packetId: String,
    
    @SerializedName("originId")
    val originId: String,      // Original sender's device ID
    
    @SerializedName("timestamp")
    val timestamp: Date,
    
    @SerializedName("latitude")
    val latitude: Double?,
    
    @SerializedName("longitude")
    val longitude: Double?,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("hopCount")
    val hopCount: Int = 0,      // Number of mesh hops (for relay limiting)
    
    @SerializedName("path")
    val path: List<String> = emptyList(),   // List of device IDs that relayed this packet
    
    @SerializedName("senderName")
    val senderName: String? = null,
    
    @SerializedName("senderPhone")
    val senderPhone: String? = null
) {
    companion object {
        const val MAX_HOP_COUNT = 10  // Limit to prevent infinite relay loops
        
        /**
         * Create a new SOS packet with auto-generated ID and timestamp
         */
        fun create(
            originId: String,
            message: String,
            latitude: Double?,
            longitude: Double?,
            senderName: String? = null,
            senderPhone: String? = null
        ): SOSPacket {
            return SOSPacket(
                packetId = UUID.randomUUID().toString(),
                originId = originId,
                timestamp = Date(),
                latitude = latitude,
                longitude = longitude,
                message = message,
                hopCount = 0,
                path = listOf(originId),
                senderName = senderName,
                senderPhone = senderPhone
            )
        }
    }
    
    /**
     * Create a relayed version of this packet with incremented hop count
     */
    fun createRelayedPacket(relayerId: String): SOSPacket {
        return this.copy(
            hopCount = this.hopCount + 1,
            path = this.path + relayerId
        )
    }
    
    /**
     * Check if this packet can still be relayed
     */
    fun canRelay(): Boolean {
        return hopCount < MAX_HOP_COUNT
    }
    
    /**
     * Check if a device has already relayed this packet (prevent loops)
     */
    fun hasBeenRelayedBy(deviceId: String): Boolean {
        return path.contains(deviceId)
    }
    
    /**
     * Get location string for display
     */
    fun getLocationString(): String? {
        return if (latitude != null && longitude != null) {
            String.format("%.6f, %.6f", latitude, longitude)
        } else {
            null
        }
    }
}

/**
 * Mesh Payload wrapper - Contains different types of mesh messages
 * Matches iOS MeshPayload structure
 */
data class MeshPayload(
    @SerializedName("type")
    val type: MeshPayloadType,
    
    @SerializedName("sosPacket")
    val sosPacket: SOSPacket? = null,
    
    @SerializedName("messagePayload")
    val messagePayload: MessagePayload? = null
)

/**
 * Types of mesh payloads
 */
enum class MeshPayloadType {
    @SerializedName("sos")
    SOS,
    
    @SerializedName("message")
    MESSAGE,
    
    @SerializedName("userInfo")
    USER_INFO
}
