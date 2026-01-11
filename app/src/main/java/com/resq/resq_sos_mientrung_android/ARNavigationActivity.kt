package com.resq.resq_sos_mientrung_android

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.resq.resq_sos_mientrung_android.databinding.ActivityArNavigationBinding
import com.resq.resq_sos_mientrung_android.uwb.UwbRangingResult
import com.resq.resq_sos_mientrung_android.uwb.UWBManager
import com.resq.resq_sos_mientrung_android.uwb.UwbAddressStub
import kotlinx.coroutines.launch
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.location.Location
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.*

class ARNavigationActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var binding: ActivityArNavigationBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private var uwbManager: UWBManager? = null
    private var targetAddress: UwbAddressStub? = null
    private var currentRangingResult: UwbRangingResult? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var targetLocation: Location? = null // Mock target location for demo
    
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var deviceAzimuth: Float = 0f
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startCamera()
        } else {
            Toast.makeText(this, "Cần quyền camera và vị trí để sử dụng tính năng này", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Get target address and location from intent
        val addressBytes = intent.getByteArrayExtra(EXTRA_TARGET_ADDRESS)
        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Thiết bị"
        val hasUWB = intent.getBooleanExtra(EXTRA_HAS_UWB, false)
        val targetLat = intent.getDoubleExtra(EXTRA_TARGET_LATITUDE, Double.NaN)
        val targetLng = intent.getDoubleExtra(EXTRA_TARGET_LONGITUDE, Double.NaN)
        
        if (addressBytes == null || addressBytes.size < 2) {
            Toast.makeText(this, "Không có địa chỉ thiết bị đích", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        if (targetLat.isNaN() || targetLng.isNaN()) {
            Toast.makeText(this, "Không có vị trí GPS của thiết bị đích", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Create UwbAddressStub from bytes
        val addressArray = if (addressBytes.size >= 2) {
            addressBytes.take(2).toByteArray()
        } else {
            ByteArray(2) { if (it < addressBytes.size) addressBytes[it] else 0 }
        }
        targetAddress = UwbAddressStub(addressArray)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Sử dụng vị trí GPS thật từ thiết bị phát SOS
        targetLocation = Location("target").apply {
            latitude = targetLat
            longitude = targetLng
        }
        
        // Show message
        if (!hasUWB) {
            binding.statusText.text = "Sử dụng điều hướng dựa trên GPS và la bàn"
            binding.statusText.visibility = View.VISIBLE
        }
        binding.deviceNameText.text = deviceName
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Initialize sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        // Setup UI
        binding.closeButton.setOnClickListener {
            finish()
        }
        
        // Check permissions
        if (checkPermissions()) {
            startCamera()
            initializeUWB()
        } else {
            requestPermissions()
        }
    }
    
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi khởi động camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        preview = Preview.Builder().build()
        
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview
            )
            
            preview?.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khởi động camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun initializeUWB() {
        uwbManager = UWBManager(this)
        
        targetAddress?.let { address ->
            lifecycleScope.launch {
                val started = uwbManager!!.startRanging(address)
                if (!started) {
                    binding.statusText.text = "Không thể khởi động định vị"
                    binding.statusText.visibility = View.VISIBLE
                }
            }
            
            // Start location-based navigation simulation
            startLocationBasedNavigation()
            
            // Observe ranging results
            lifecycleScope.launch {
                uwbManager!!.rangingResult.collect { result ->
                    result?.let {
                        currentRangingResult = it
                        updateUI(it)
                    }
                }
            }
            
            // Observe errors
            lifecycleScope.launch {
                uwbManager!!.error.collect { error ->
                    error?.let {
                        if (it.isNotEmpty()) {
                            binding.statusText.text = it
                            binding.statusText.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }
    
    private fun startLocationBasedNavigation() {
        // Sử dụng vị trí GPS thật để tính khoảng cách và hướng đến thiết bị đích
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED && targetLocation != null) {
            
            // Lấy vị trí hiện tại và cập nhật định kỳ
            updateLocationAndCalculateDirection()
            
            // Cập nhật định kỳ mỗi 2 giây
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(object : Runnable {
                override fun run() {
                    if (targetLocation != null) {
                        updateLocationAndCalculateDirection()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 2000)
                    }
                }
            }, 2000)
        }
    }
    
    private fun updateLocationAndCalculateDirection() {
        fusedLocationClient.lastLocation.addOnSuccessListener { currentLocation ->
            currentLocation?.let { current ->
                targetLocation?.let { target ->
                    // Tính khoảng cách thật giữa 2 vị trí GPS
                    val distance = current.distanceTo(target) // meters
                    
                    // Tính bearing (hướng) từ vị trí hiện tại đến vị trí đích
                    val bearing = current.bearingTo(target) // degrees
                    val azimuth = Math.toRadians(bearing.toDouble()).toFloat() // radians
                    
                    uwbManager?.updateRangingResult(distance, azimuth, 0f)
                    
                    android.util.Log.d("ARNavigation", "Distance: ${distance}m, Bearing: ${bearing}°, Target: (${target.latitude}, ${target.longitude})")
                }
            }
        }
    }
    
    private fun updateUI(result: UwbRangingResult) {
        // Update distance
        val distanceFeet = result.distanceFeet
        binding.distanceText.text = "${distanceFeet.toInt()} ft"
        
        // Calculate direction relative to device orientation
        val targetAzimuth = result.azimuthDegrees
        val relativeAngle = normalizeAngle(targetAzimuth - deviceAzimuth)
        
        // Update arrow rotation
        binding.arrowView.rotation = relativeAngle
        
        // Update instruction text
        val instruction = when {
            distanceFeet < 3 -> "Đã đến gần"
            relativeAngle > -15 && relativeAngle < 15 -> "Đi thẳng"
            relativeAngle > 15 && relativeAngle < 75 -> "Rẽ phải"
            relativeAngle > 75 && relativeAngle < 105 -> "Rẽ phải mạnh"
            relativeAngle > 105 && relativeAngle < 165 -> "Quay lại phải"
            relativeAngle > -75 && relativeAngle < -15 -> "Rẽ trái"
            relativeAngle > -105 && relativeAngle < -75 -> "Rẽ trái mạnh"
            else -> "Quay lại trái"
        }
        binding.instructionText.text = instruction
        
        // Show/hide target indicator
        if (distanceFeet < 10) {
            binding.targetIndicator.visibility = View.VISIBLE
        } else {
            binding.targetIndicator.visibility = View.GONE
        }
    }
    
    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        while (normalized > 180) normalized -= 360
        while (normalized < -180) normalized += 360
        return normalized
    }
    
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }
    
    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(it.values, 0, accelerometerReading, 0, accelerometerReading.size)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(it.values, 0, magnetometerReading, 0, magnetometerReading.size)
                }
            }
            updateOrientationAngles()
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    private fun updateOrientationAngles() {
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        deviceAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        
        // Simulate UWB ranging based on compass direction
        // In production with real UWB, this would come from UWB hardware
        val mockDistance = 20f // meters
        val mockAzimuth = Math.toRadians(deviceAzimuth.toDouble()).toFloat()
        uwbManager?.updateRangingResult(mockDistance, mockAzimuth, 0f)
        
        // Update UI if we have ranging result
        currentRangingResult?.let { updateUI(it) }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        uwbManager?.stopRanging()
        cameraExecutor.shutdown()
    }
    
    companion object {
        const val EXTRA_TARGET_ADDRESS = "target_address"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_HAS_UWB = "has_uwb"
        const val EXTRA_TARGET_LATITUDE = "target_latitude"
        const val EXTRA_TARGET_LONGITUDE = "target_longitude"
    }
}
