package com.resq.resq_sos_mientrung_android.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Network Monitor - Singleton class to monitor network connectivity
 * Similar to iOS NetworkMonitor.swift
 * 
 * Usage:
 * - NetworkMonitor.getInstance(context).isConnected -> StateFlow<Boolean>
 * - NetworkMonitor.getInstance(context).connectionType -> StateFlow<ConnectionType>
 */
class NetworkMonitor private constructor(context: Context) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
        
        @Volatile
        private var instance: NetworkMonitor? = null
        
        fun getInstance(context: Context): NetworkMonitor {
            return instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context.applicationContext).also { instance = it }
            }
        }
    }
    
    enum class ConnectionType {
        WIFI,
        CELLULAR,
        ETHERNET,
        UNKNOWN,
        NONE
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isConnected = MutableStateFlow(checkCurrentConnection())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectionType = MutableStateFlow(getCurrentConnectionType())
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()
    
    // Synchronous check for current connection status
    val isConnectedSync: Boolean
        get() = _isConnected.value
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            updateConnectionStatus()
        }
        
        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            updateConnectionStatus()
        }
        
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            Log.d(TAG, "Network capabilities changed")
            updateConnectionStatus()
        }
        
        override fun onUnavailable() {
            Log.d(TAG, "Network unavailable")
            _isConnected.value = false
            _connectionType.value = ConnectionType.NONE
        }
    }
    
    init {
        startMonitoring()
    }
    
    private fun startMonitoring() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Log.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting network monitoring: ${e.message}", e)
        }
    }
    
    private fun updateConnectionStatus() {
        _isConnected.value = checkCurrentConnection()
        _connectionType.value = getCurrentConnectionType()
        Log.d(TAG, "Connection status updated: connected=${_isConnected.value}, type=${_connectionType.value}")
    }
    
    private fun checkCurrentConnection(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection: ${e.message}", e)
            false
        }
    }
    
    private fun getCurrentConnectionType(): ConnectionType {
        return try {
            val network = connectivityManager.activeNetwork ?: return ConnectionType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.NONE
            
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                else -> ConnectionType.UNKNOWN
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connection type: ${e.message}", e)
            ConnectionType.NONE
        }
    }
    
    /**
     * Get network status as Flow for reactive UI updates
     */
    fun observeNetworkStatus(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        // Send initial state
        trySend(checkCurrentConnection())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "Network monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping network monitoring: ${e.message}", e)
        }
    }
}
