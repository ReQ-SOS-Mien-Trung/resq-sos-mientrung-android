package com.resq.resq_sos_mientrung_android.uwb

import android.content.Context
import android.util.Log
import androidx.annotation.RequiresApi
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UWB Manager - Simplified version that uses location-based navigation
 * Note: Full UWB API requires Android 12+ and hardware support
 * This implementation provides a fallback that works on all devices
 */
class UWBManager(private val context: Context) {
    private val _rangingResult = MutableStateFlow<UwbRangingResult?>(null)
    val rangingResult: StateFlow<UwbRangingResult?> = _rangingResult.asStateFlow()
    
    private val _isRanging = MutableStateFlow(false)
    val isRanging: StateFlow<Boolean> = _isRanging.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    suspend fun startRanging(remoteAddress: UwbAddressStub): Boolean {
        return try {
            if (!isUwbAvailable()) {
                _error.value = "UWB không khả dụng trên thiết bị này. Sử dụng chế độ điều hướng dựa trên vị trí."
                // Continue anyway for demo purposes
            }
            
            _isRanging.value = true
            _error.value = null
            
            // Simulate ranging - in production, this would use actual UWB API
            // For now, we'll use location-based navigation
            Log.d(TAG, "UWB ranging started (simulated)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ranging", e)
            _error.value = "Lỗi khởi động định vị: ${e.message}"
            false
        }
    }
    
    fun stopRanging() {
        try {
            _isRanging.value = false
            _rangingResult.value = null
            Log.d(TAG, "UWB ranging stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ranging", e)
        }
    }
    
    fun isUwbAvailable(): Boolean {
        return try {
            // Check if device supports UWB (requires Android 12+ and hardware)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            // In production, also check: context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)
        } catch (e: Exception) {
            false
        }
    }
    
    fun getLocalAddress(): UwbAddressStub? {
        return try {
            // Generate a mock address for demo
            // In production, get from actual UWB hardware
            UwbAddressStub(byteArrayOf(0x01, 0x02))
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Update ranging result (called from ARNavigationActivity based on location/compass)
     */
    fun updateRangingResult(distanceMeters: Float, azimuthRadians: Float, elevationRadians: Float = 0f) {
        _rangingResult.value = UwbRangingResult(
            distanceMeters = distanceMeters,
            azimuthRadians = azimuthRadians,
            elevationRadians = elevationRadians
        )
    }
    
    companion object {
        private const val TAG = "UWBManager"
    }
}

data class UwbRangingResult(
    val distanceMeters: Float,
    val azimuthRadians: Float,
    val elevationRadians: Float
) {
    val distanceFeet: Float get() = distanceMeters * 3.28084f
    val azimuthDegrees: Float get() = Math.toDegrees(azimuthRadians.toDouble()).toFloat()
    val elevationDegrees: Float get() = Math.toDegrees(elevationRadians.toDouble()).toFloat()
}
