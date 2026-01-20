package com.resq.resq_sos_mientrung_android.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.resq.resq_sos_mientrung_android.R
import com.resq.resq_sos_mientrung_android.bridgefy.BridgefyManager
import com.resq.resq_sos_mientrung_android.bridgefy.SOSPacket
import com.resq.resq_sos_mientrung_android.databinding.FragmentSosFormBinding
import com.resq.resq_sos_mientrung_android.utils.LocationHelper
import com.resq.resq_sos_mientrung_android.utils.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SOS Form Fragment - Matches iOS SOSFormView functionality
 * 
 * Features:
 * - Network status display
 * - Location display
 * - Quick message selection
 * - Custom message input
 * - Send SOS via mesh + server
 */
class SOSFormFragment : Fragment() {
    
    private var _binding: FragmentSosFormBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var bridgefyManager: BridgefyManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var locationHelper: LocationHelper
    
    private var currentLocation: Location? = null
    private var selectedMessage: String = ""
    private var isSending: Boolean = false
    
    // Quick messages matching iOS
    private val quickMessages = listOf(
        "Gãy chân, cần cứu hộ",
        "Bị mắc kẹt, cần giúp đỡ",
        "Cần thức ăn và nước uống",
        "Bị thương, cần y tế",
        "Nhà sập, có người bị kẹt"
    )
    
    private var selectedQuickMessageButton: MaterialButton? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSosFormBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Prevent touch events from passing through to fragment below
        // But allow children to receive events normally
        view.isClickable = true
        view.isFocusable = true
        
        bridgefyManager = BridgefyManager.getInstance(requireContext())
        networkMonitor = NetworkMonitor.getInstance(requireContext())
        locationHelper = LocationHelper(requireContext())
        
        setupToolbar()
        setupNetworkStatusObserver()
        setupLocationDisplay()
        setupQuickMessages()
        setupCustomMessageInput()
        setupSendButton()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            // Close this fragment and show HomeFragment back
            closeFragment()
        }
    }
    
    private fun closeFragment() {
        // Show HomeFragment again before closing
        parentFragmentManager.findFragmentByTag("fragment_home")?.let { homeFragment ->
            parentFragmentManager.beginTransaction()
                .show(homeFragment)
                .commit()
        }
        parentFragmentManager.popBackStack()
    }
    
    private fun setupNetworkStatusObserver() {
        // Observe network status changes
        viewLifecycleOwner.lifecycleScope.launch {
            networkMonitor.isConnected.collectLatest { isConnected ->
                updateNetworkStatusUI(isConnected)
            }
        }
    }
    
    private fun updateNetworkStatusUI(isConnected: Boolean) {
        if (isConnected) {
            binding.networkStatusDot.setBackgroundResource(R.drawable.network_status_dot_green)
            binding.textNetworkStatus.text = "Có kết nối mạng"
            binding.textNetworkStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.card_icon_green))
        } else {
            binding.networkStatusDot.setBackgroundResource(R.drawable.network_status_dot_red)
            binding.textNetworkStatus.text = "Không có mạng - sẽ gửi qua Mesh"
            binding.textNetworkStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.card_icon_red))
        }
    }
    
    private fun setupLocationDisplay() {
        // Check permission and get location
        if (locationHelper.hasLocationPermission()) {
            fetchLocation()
        } else {
            binding.iconLocation.setImageResource(R.drawable.ic_location_off)
            binding.iconLocation.setColorFilter(ContextCompat.getColor(requireContext(), R.color.card_icon_orange))
            binding.textLocation.text = "Cần cấp quyền vị trí"
            
            // Request permission
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun fetchLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.textLocation.text = "Đang lấy vị trí..."
            binding.iconLocation.setColorFilter(ContextCompat.getColor(requireContext(), R.color.card_icon_orange))
            
            val location = withContext(Dispatchers.IO) {
                locationHelper.getCurrentLocation() ?: locationHelper.getLastKnownLocation()
            }
            
            if (location != null) {
                currentLocation = location
                binding.textLocation.text = String.format("%.6f, %.6f", location.latitude, location.longitude)
                binding.iconLocation.setImageResource(R.drawable.ic_location)
                binding.iconLocation.setColorFilter(ContextCompat.getColor(requireContext(), R.color.card_icon_green))
            } else {
                binding.textLocation.text = "Không thể lấy vị trí"
                binding.iconLocation.setImageResource(R.drawable.ic_location_off)
                binding.iconLocation.setColorFilter(ContextCompat.getColor(requireContext(), R.color.card_icon_red))
            }
        }
    }
    
    private fun setupQuickMessages() {
        val quickButtons = listOf(
            binding.btnQuickMsg1,
            binding.btnQuickMsg2,
            binding.btnQuickMsg3,
            binding.btnQuickMsg4,
            binding.btnQuickMsg5
        )
        
        quickButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectQuickMessage(button, quickMessages[index])
            }
        }
    }
    
    private fun selectQuickMessage(button: MaterialButton, message: String) {
        // Deselect previous button
        selectedQuickMessageButton?.let { prevButton ->
            prevButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
            prevButton.setStrokeColorResource(R.color.card_stroke)
            prevButton.icon = null
        }
        
        // Select new button
        if (selectedQuickMessageButton == button) {
            // Clicking same button deselects
            selectedQuickMessageButton = null
            selectedMessage = ""
            binding.editTextCustomMessage.setText("")
        } else {
            selectedQuickMessageButton = button
            selectedMessage = message
            button.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.sos_selected_bg))
            button.setStrokeColorResource(R.color.orange_primary)
            button.setIconResource(R.drawable.ic_check_circle)
            button.setIconTintResource(R.color.card_icon_green)
            
            // Set message to text field
            binding.editTextCustomMessage.setText(message)
        }
        
        updateSendButtonState()
    }
    
    private fun setupCustomMessageInput() {
        binding.editTextCustomMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                selectedMessage = s?.toString()?.trim() ?: ""
                
                // If user types custom message, deselect quick buttons
                if (selectedMessage.isNotEmpty() && !quickMessages.contains(selectedMessage)) {
                    selectedQuickMessageButton?.let { prevButton ->
                        prevButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
                        prevButton.setStrokeColorResource(R.color.card_stroke)
                        prevButton.icon = null
                    }
                    selectedQuickMessageButton = null
                }
                
                updateSendButtonState()
            }
        })
    }
    
    private fun setupSendButton() {
        binding.btnSendSOS.setOnClickListener {
            sendSOS()
        }
        
        updateSendButtonState()
    }
    
    private fun updateSendButtonState() {
        val canSend = selectedMessage.isNotEmpty() && !isSending
        binding.btnSendSOS.isEnabled = canSend
        binding.btnSendSOS.alpha = if (canSend) 1.0f else 0.5f
    }
    
    private fun sendSOS() {
        if (selectedMessage.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập nội dung SOS", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isSending) return
        
        isSending = true
        binding.loadingOverlay.visibility = View.VISIBLE
        updateSendButtonState()
        
        val latitude = currentLocation?.latitude
        val longitude = currentLocation?.longitude
        
        bridgefyManager.sendSOSWithUpload(
            message = selectedMessage,
            latitude = latitude,
            longitude = longitude,
            senderName = bridgefyManager.myDeviceName,
            senderPhone = null
        ) { success ->
            activity?.runOnUiThread {
                isSending = false
                binding.loadingOverlay.visibility = View.GONE
                updateSendButtonState()
                
                if (success) {
                    showSuccessDialog()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Không thể gửi SOS. Vui lòng thử lại.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun showSuccessDialog() {
        val isConnected = networkMonitor.isConnectedSync
        
        val message = if (isConnected) {
            "Tín hiệu SOS đã được gửi trực tiếp lên server và broadcast đến các thiết bị gần đó."
        } else {
            "Tín hiệu SOS đã được gửi qua mạng Mesh. Khi có thiết bị có kết nối mạng nhận được, họ sẽ relay lên server giúp bạn."
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Đã gửi SOS!")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // Close this fragment
                parentFragmentManager.popBackStack()
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation()
            } else {
                binding.textLocation.text = "Quyền vị trí bị từ chối"
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2001
        
        fun newInstance(): SOSFormFragment {
            return SOSFormFragment()
        }
    }
}
