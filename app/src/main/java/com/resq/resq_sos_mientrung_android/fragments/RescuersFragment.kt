package com.resq.resq_sos_mientrung_android.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.resq.resq_sos_mientrung_android.ARNavigationActivity
import com.resq.resq_sos_mientrung_android.bridgefy.BridgefyManager
import com.resq.resq_sos_mientrung_android.bridgefy.Message
import com.resq.resq_sos_mientrung_android.bridgefy.User
import com.resq.resq_sos_mientrung_android.uwb.UwbAddressStub
import com.resq.resq_sos_mientrung_android.R
import com.resq.resq_sos_mientrung_android.databinding.FragmentRescuersBinding
import com.resq.resq_sos_mientrung_android.databinding.ItemRescueDeviceBinding
import org.json.JSONObject

data class RescueDevice(
    val deviceId: String,
    val deviceName: String,
    val uwbAddress: UwbAddressStub?,
    val latitude: Double? = null,  // Vĩ độ GPS
    val longitude: Double? = null, // Kinh độ GPS
    val lastSeen: Long = System.currentTimeMillis()
)

class RescuersFragment : Fragment(), BridgefyManager.BridgefyListener {
    private var _binding: FragmentRescuersBinding? = null
    private val binding get() = _binding!!
    
    private val devices = mutableListOf<RescueDevice>()
    private lateinit var adapter: RescueDeviceAdapter
    private lateinit var bridgefyManager: BridgefyManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRescuersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup Bridgefy
        bridgefyManager = BridgefyManager.getInstance(requireContext())
        bridgefyManager.addListener(this)
        
        // Setup RecyclerView
        adapter = RescueDeviceAdapter { device ->
            startARNavigation(device)
        }
        binding.devicesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.devicesRecyclerView.adapter = adapter
        
        // Load existing devices
        loadRescueDevices()
    }
    
    private fun loadRescueDevices() {
        // Devices sẽ được thêm khi nhận SOS signals từ Bridgefy
        devices.clear()
        updateUI()
    }
    
    fun addRescueDevice(device: RescueDevice) {
        val existingIndex = devices.indexOfFirst { it.deviceId == device.deviceId }
        if (existingIndex >= 0) {
            devices[existingIndex] = device
        } else {
            devices.add(device)
        }
        updateUI()
    }
    
    fun removeRescueDevice(deviceId: String) {
        devices.removeAll { it.deviceId == deviceId }
        updateUI()
    }
    
    private fun updateUI() {
        adapter.submitList(devices.toList())
        binding.emptyStateText.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun startARNavigation(device: RescueDevice) {
        // Sử dụng vị trí GPS thật nếu có
        if (device.latitude == null || device.longitude == null) {
            android.widget.Toast.makeText(
                requireContext(),
                "Thiết bị này chưa gửi vị trí GPS",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Tạo address từ deviceId (chỉ để identify, không phải UWB thật)
        val addressBytes = device.uwbAddress?.address 
            ?: byteArrayOf(
                device.deviceId.hashCode().toByte(),
                (device.deviceId.hashCode() shr 8).toByte()
            )
        
        val intent = Intent(requireContext(), ARNavigationActivity::class.java).apply {
            putExtra(ARNavigationActivity.EXTRA_TARGET_ADDRESS, addressBytes)
            putExtra(ARNavigationActivity.EXTRA_DEVICE_NAME, device.deviceName)
            putExtra(ARNavigationActivity.EXTRA_HAS_UWB, device.uwbAddress != null)
            putExtra(ARNavigationActivity.EXTRA_TARGET_LATITUDE, device.latitude)
            putExtra(ARNavigationActivity.EXTRA_TARGET_LONGITUDE, device.longitude)
        }
        startActivity(intent)
    }

    // BridgefyListener callbacks
    override fun onBridgefyStart() {
        Log.d("RescuersFragment", "Bridgefy started")
    }
    
    override fun onBridgefyStartError(error: String) {
        Log.e("RescuersFragment", "Bridgefy start error: $error")
    }
    
    override fun onUserFound(user: User) {
        // User found, but wait for SOS signal
        Log.d("RescuersFragment", "User found: ${user.userId}")
    }
    
    override fun onUserLost(user: User) {
        // Remove device when user is lost
        Log.d("RescuersFragment", "User lost: ${user.userId}")
        removeRescueDevice(user.userId)
    }
    
    override fun onMessageReceived(message: Message, user: User) {
        // Check if this is an SOS signal
        try {
            val jsonObject = JSONObject(message.content)
            val messageType = jsonObject.optString("type", "")
            
            if (messageType == "SOS") {
                val senderName = jsonObject.optString("senderName", user.getDisplayNameOrShortId())
                val senderId = jsonObject.optString("senderId", user.userId)
                
                // Parse GPS location từ SOS signal
                val latitude = if (jsonObject.has("latitude")) {
                    jsonObject.optDouble("latitude", Double.NaN).takeIf { !it.isNaN() }
                } else {
                    null
                }
                val longitude = if (jsonObject.has("longitude")) {
                    jsonObject.optDouble("longitude", Double.NaN).takeIf { !it.isNaN() }
                } else {
                    null
                }
                
                // Parse UWB address if available
                val uwbAddressBytes = jsonObject.optString("uwbAddress", null)
                val uwbAddress = if (!uwbAddressBytes.isNullOrEmpty()) {
                    try {
                        // Convert from base64 or hex string to bytes
                        UwbAddressStub(android.util.Base64.decode(uwbAddressBytes, android.util.Base64.DEFAULT))
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
                
                val rescueDevice = RescueDevice(
                    deviceId = senderId,
                    deviceName = senderName,
                    uwbAddress = uwbAddress,
                    latitude = latitude,
                    longitude = longitude,
                    lastSeen = System.currentTimeMillis()
                )
                
                activity?.runOnUiThread {
                    if (!isAdded || _binding == null) return@runOnUiThread
                    addRescueDevice(rescueDevice)
                    val locationInfo = if (latitude != null && longitude != null) {
                        " (vị trí: ${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)})"
                    } else {
                        " (chưa có vị trí)"
                    }
                    Log.d("RescuersFragment", "SOS signal received from $senderName$locationInfo")
                }
            }
        } catch (e: Exception) {
            Log.e("RescuersFragment", "Error parsing SOS message", e)
        }
    }
    
    override fun onMessageSent(messageId: String) {
        // Not used
    }
    
    override fun onMessageFailed(messageId: String, error: String) {
        // Not used
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        bridgefyManager.removeListener(this)
        _binding = null
    }
    
    private class RescueDeviceAdapter(
        private val onNavigateClick: (RescueDevice) -> Unit
    ) : RecyclerView.Adapter<RescueDeviceAdapter.ViewHolder>() {
        
        private var devices = listOf<RescueDevice>()
        
        fun submitList(newDevices: List<RescueDevice>) {
            devices = newDevices
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRescueDeviceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(devices[position])
        }
        
        override fun getItemCount() = devices.size
        
        inner class ViewHolder(
            private val binding: ItemRescueDeviceBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(device: RescueDevice) {
                binding.deviceNameText.text = device.deviceName
                binding.deviceInfoText.text = "Đang phát tín hiệu cấp cứu"
                binding.navigateButton.setOnClickListener {
                    onNavigateClick(device)
                }
            }
        }
    }
}
