package com.resq.resq_sos_mientrung_android.services

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.resq.resq_sos_mientrung_android.bridgefy.SOSPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SOS Relay Service - Handles uploading SOS packets to server
 * Similar to iOS SOSRelayService
 * 
 * Responsibilities:
 * - Upload SOS packets to server when device has network
 * - Relay SOS packets from other devices
 */
object SOSRelayService {
    private const val TAG = "SOSRelayService"
    
    // TODO: Replace with actual server URL when available
    private const val BASE_URL = "https://api.resq-sos.vn"
    private const val SOS_ENDPOINT = "/api/v1/sos"
    
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .create()
    
    /**
     * Upload SOS packet to server
     * @param sosPacket The SOS packet to upload
     * @return true if upload successful, false otherwise
     */
    suspend fun uploadSOS(sosPacket: SOSPacket): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uploading SOS to server: packetId=${sosPacket.packetId}")
            
            val url = URL("$BASE_URL$SOS_ENDPOINT")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            // Convert SOSPacket to JSON
            val jsonBody = gson.toJson(sosPacket)
            Log.d(TAG, "SOS JSON payload: $jsonBody")
            
            // Write request body
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }
            
            // Read response
            val responseCode = connection.responseCode
            Log.d(TAG, "Server response code: $responseCode")
            
            if (responseCode in 200..299) {
                Log.d(TAG, "✅ SOS uploaded successfully: packetId=${sosPacket.packetId}")
                return@withContext true
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "❌ SOS upload failed: $responseCode - $errorStream")
                return@withContext false
            }
            
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Server unreachable (no network or server down): ${e.message}")
            return@withContext false
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "Connection refused: ${e.message}")
            return@withContext false
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Connection timeout: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading SOS: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Upload SOS packet from relay (when receiving from mesh and have network)
     * Adds relay information to the request
     */
    suspend fun uploadRelayedSOS(sosPacket: SOSPacket, relayerId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uploading relayed SOS to server: packetId=${sosPacket.packetId}, relayedBy=$relayerId")
            
            val url = URL("$BASE_URL$SOS_ENDPOINT/relay")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Relayed-By", relayerId)
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            // Convert SOSPacket to JSON
            val jsonBody = gson.toJson(sosPacket)
            
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode in 200..299) {
                Log.d(TAG, "✅ Relayed SOS uploaded successfully: packetId=${sosPacket.packetId}")
                return@withContext true
            } else {
                Log.e(TAG, "❌ Relayed SOS upload failed: $responseCode")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading relayed SOS: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Fetch SOS list from server (for map display)
     * @param latitude Center latitude for search
     * @param longitude Center longitude for search
     * @param radiusKm Search radius in kilometers
     */
    suspend fun fetchNearbySOS(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 50.0
    ): List<SOSPacket> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching nearby SOS: lat=$latitude, lng=$longitude, radius=$radiusKm km")
            
            val url = URL("$BASE_URL$SOS_ENDPOINT/nearby?lat=$latitude&lng=$longitude&radius=$radiusKm")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().readText()
                val sosArray = gson.fromJson(responseBody, Array<SOSPacket>::class.java)
                Log.d(TAG, "Fetched ${sosArray.size} nearby SOS packets")
                return@withContext sosArray.toList()
            } else {
                Log.e(TAG, "Failed to fetch SOS: $responseCode")
                return@withContext emptyList()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SOS: ${e.message}", e)
            return@withContext emptyList()
        }
    }
}
