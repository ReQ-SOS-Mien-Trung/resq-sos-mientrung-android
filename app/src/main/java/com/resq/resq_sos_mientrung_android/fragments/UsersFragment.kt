package com.resq.resq_sos_mientrung_android.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.resq.resq_sos_mientrung_android.R
import com.resq.resq_sos_mientrung_android.bridgefy.BridgefyManager
import com.resq.resq_sos_mientrung_android.bridgefy.Message
import com.resq.resq_sos_mientrung_android.bridgefy.User
import com.resq.resq_sos_mientrung_android.databinding.FragmentUsersBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.location.Location

class UsersFragment : Fragment(), BridgefyManager.BridgefyListener {
    private var _binding: FragmentUsersBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var bridgefyManager: BridgefyManager
    private val usersAdapter = UsersAdapter()
    private val nearbyUsers = mutableListOf<User>()
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 1002
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Handler để auto-refresh danh sách users
    private var refreshHandler: android.os.Handler? = null
    private var refreshRunnable: Runnable? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bridgefyManager = BridgefyManager.getInstance(requireContext())
        bridgefyManager.addListener(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        setupRecyclerView()
        updateBridgefyStatus()
        checkAndRequestPermissions()
        
        // Setup SOS button
        binding.buttonSOS.setOnClickListener {
            sendSOSSignal()
        }
        
        // Periodically check status (every 500ms) until initialized
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val statusChecker = object : Runnable {
            override fun run() {
                if (!bridgefyManager.isInitialized()) {
                    updateBridgefyStatus()
                    handler.postDelayed(this, 500)
                } else {
                    updateBridgefyStatus()
                    // Bắt đầu auto-refresh sau khi initialized
                    startAutoRefresh()
                }
            }
        }
        handler.post(statusChecker)
    }
    
    /**
     * Tự động refresh danh sách và gửi presence mỗi 10 giây
     * Giải quyết vấn đề one-way discovery
     */
    private fun startAutoRefresh() {
        if (refreshHandler != null) return // Đã bắt đầu rồi
        
        refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
        refreshRunnable = object : Runnable {
            override fun run() {
                if (_binding != null && isAdded) {
                    // Refresh danh sách và gửi presence
                    loadNearbyUsers()
                    android.util.Log.d("UsersFragment", "🔄 Auto-refresh: ${nearbyUsers.size} users")
                    refreshHandler?.postDelayed(this, 10000) // Mỗi 10 giây
                }
            }
        }
        refreshHandler?.postDelayed(refreshRunnable!!, 10000)
    }
    
    private fun stopAutoRefresh() {
        refreshRunnable?.let { refreshHandler?.removeCallbacks(it) }
        refreshHandler = null
        refreshRunnable = null
    }
    
    private fun updateBridgefyStatus() {
        if (bridgefyManager.isInitialized()) {
            binding.statusIndicator.setBackgroundResource(R.drawable.bridgefy_status_indicator_active)
            binding.textBridgefyStatus.text = "Đang hoạt động"
            binding.textBridgefyStatus.setTextColor(requireContext().getColor(R.color.nav_selected))
        } else {
            binding.statusIndicator.setBackgroundResource(R.drawable.bridgefy_status_indicator)
            binding.textBridgefyStatus.text = "Đang khởi động..."
            binding.textBridgefyStatus.setTextColor(requireContext().getColor(R.color.nav_unselected))
        }
    }
    
    private fun setupRecyclerView() {
        binding.recyclerViewUsers.layoutManager = LinearLayoutManager(requireContext())
        // Pass click callback to adapter
        usersAdapter.setOnUserClickListener { user ->
            // Navigate to chat with selected user
            val activity = requireActivity()
            if (activity is com.resq.resq_sos_mientrung_android.MainActivity) {
                activity.navigateToChatWithUser(user.userId, user.getDisplayNameOrShortId())
            }
        }
        binding.recyclerViewUsers.adapter = usersAdapter
        
        updateUsersList()
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_ADMIN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
        
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissions.toTypedArray(),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        } else {
            loadNearbyUsers()
        }
    }
    
    private fun loadNearbyUsers() {
        if (bridgefyManager.isInitialized()) {
            // Thử start SDK nếu chưa start (sau khi permissions được grant)
            bridgefyManager.tryStartSDK()
            
            // Gửi presence announcement để các thiết bị khác biết mình online
            // Giải quyết vấn đề "one-way discovery"
            bridgefyManager.announcePresence()
            
            val users = bridgefyManager.getNearbyUsers()
            nearbyUsers.clear()
            nearbyUsers.addAll(users)
            updateUsersList()
        }
    }
    
    private fun updateUsersList() {
        val userCount = nearbyUsers.size
        updateUserCountBadge(userCount)
        
        if (nearbyUsers.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerViewUsers.visibility = View.GONE
            binding.textNoUsers.text = "Đang tìm người dùng gần đây..."
            binding.textEmptySubtitle?.text = "Có 0 người trong mạng. Đảm bảo Bluetooth đã bật và có người dùng khác đang mở app gần bạn."
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerViewUsers.visibility = View.VISIBLE
            usersAdapter.submitList(nearbyUsers.toList())
        }
    }
    
    private fun updateUserCountBadge(count: Int) {
        binding.textUserCount.text = count.toString()
        binding.textUserCount.visibility = View.VISIBLE
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadNearbyUsers()
            } else {
                Snackbar.make(
                    binding.root,
                    "Cần quyền Bluetooth và vị trí để tìm người dùng gần đây",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
    
    // BridgefyListener callbacks
    override fun onBridgefyStart() {
        android.util.Log.d("UsersFragment", "Bridgefy đã khởi động thành công!")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            updateBridgefyStatus()
            loadNearbyUsers()
            Snackbar.make(
                binding.root,
                "Bridgefy đã sẵn sàng! Đang tìm người dùng gần đây...",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onBridgefyStartError(error: String) {
        android.util.Log.e("UsersFragment", "Lỗi khởi động Bridgefy: $error")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            updateBridgefyStatus()
            binding.statusIndicator.setBackgroundResource(R.drawable.bridgefy_status_indicator)
            binding.textBridgefyStatus.text = "Lỗi: $error"
            binding.textBridgefyStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            Snackbar.make(
                binding.root,
                "Lỗi khởi động Bridgefy: $error",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onUserFound(user: User) {
        android.util.Log.d("UsersFragment", "Tìm thấy người dùng: ${user.userId}")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            if (!nearbyUsers.contains(user)) {
                nearbyUsers.add(user)
                updateUsersList()
                val count = nearbyUsers.size
                Snackbar.make(
                    binding.root,
                    "Tìm thấy người dùng mới! (Tổng: $count người)",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onUserLost(user: User) {
        android.util.Log.d("UsersFragment", "Mất kết nối với người dùng: ${user.userId}")
        activity?.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
            nearbyUsers.remove(user)
            updateUsersList()
            val count = nearbyUsers.size
            Snackbar.make(
                binding.root,
                "Mất kết nối với người dùng (Còn lại: $count người)",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onMessageReceived(message: Message, user: User) {
        // Not used in UsersFragment
    }
    
    override fun onMessageSent(messageId: String) {
        // Not used in UsersFragment
    }
    
    override fun onMessageFailed(messageId: String, error: String) {
        // Not used in UsersFragment
    }
    
    private fun sendSOSSignal() {
        try {
            if (!isAdded || _binding == null) {
                android.util.Log.w("UsersFragment", "Fragment not attached, cannot send SOS")
                return
            }
            
            if (!bridgefyManager.isInitialized()) {
                Snackbar.make(
                    binding.root,
                    "Bridgefy chưa sẵn sàng. Vui lòng đợi...",
                    Snackbar.LENGTH_SHORT
                ).show()
                return
            }
            
            // Lấy vị trí GPS thật trước khi gửi SOS
            if (checkLocationPermission()) {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                        try {
                            location?.let {
                                val messageIds = bridgefyManager.sendSOSSignal(it.latitude, it.longitude)
                                if (messageIds.isEmpty()) {
                                    Snackbar.make(
                                        binding.root,
                                        "Không có thiết bị gần đây để gửi tín hiệu SOS",
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Snackbar.make(
                                        binding.root,
                                        "✅ Đã phát tín hiệu SOS (vị trí: ${String.format("%.6f", it.latitude)}, ${String.format("%.6f", it.longitude)}) đến ${messageIds.size} thiết bị",
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            } ?: run {
                                // Không lấy được vị trí, gửi SOS không có vị trí
                                val messageIds = bridgefyManager.sendSOSSignal()
                                if (messageIds.isEmpty()) {
                                    Snackbar.make(
                                        binding.root,
                                        "Không có thiết bị gần đây. Không lấy được vị trí GPS",
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Snackbar.make(
                                        binding.root,
                                        "⚠️ Đã phát SOS nhưng chưa có vị trí GPS",
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("UsersFragment", "Error in location callback", e)
                            val messageIds = bridgefyManager.sendSOSSignal()
                            Snackbar.make(
                                binding.root,
                                "⚠️ Đã phát SOS nhưng có lỗi khi xử lý vị trí",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }.addOnFailureListener { e ->
                        android.util.Log.e("UsersFragment", "Error getting location", e)
                        try {
                            // Gửi SOS không có vị trí
                            val messageIds = bridgefyManager.sendSOSSignal()
                            Snackbar.make(
                                binding.root,
                                "⚠️ Đã phát SOS nhưng không lấy được vị trí GPS",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        } catch (ex: Exception) {
                            android.util.Log.e("UsersFragment", "Error sending SOS without location", ex)
                        }
                    }
                } catch (e: SecurityException) {
                    android.util.Log.e("UsersFragment", "Security exception getting location", e)
                    try {
                        val messageIds = bridgefyManager.sendSOSSignal()
                    } catch (ex: Exception) {
                        android.util.Log.e("UsersFragment", "Error sending SOS", ex)
                    }
                }
            } else {
                // Không có quyền location, gửi SOS không có vị trí
                try {
                    val messageIds = bridgefyManager.sendSOSSignal()
                    if (messageIds.isEmpty()) {
                        Snackbar.make(
                            binding.root,
                            "Không có thiết bị gần đây. Cần quyền vị trí để gửi vị trí GPS",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    } else {
                        Snackbar.make(
                            binding.root,
                            "⚠️ Đã phát SOS nhưng cần quyền vị trí để gửi GPS",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UsersFragment", "Error sending SOS without permission", e)
                    Snackbar.make(
                        binding.root,
                        "Lỗi khi gửi tín hiệu SOS",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("UsersFragment", "Error in sendSOSSignal", e)
            Snackbar.make(
                binding.root,
                "Lỗi: ${e.message ?: "Không xác định"}",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoRefresh()
        bridgefyManager.removeListener(this)
        _binding = null
    }
    
    // Adapter for users list
    private class UsersAdapter : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {
        private val users = mutableListOf<User>()
        private var onUserClickListener: ((User) -> Unit)? = null
        
        fun setOnUserClickListener(listener: (User) -> Unit) {
            onUserClickListener = listener
        }
        
        fun submitList(newUsers: List<User>) {
            users.clear()
            users.addAll(newUsers)
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user, parent, false)
            return UserViewHolder(view, onUserClickListener)
        }
        
        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val user = users[position]
            holder.bind(user)
        }
        
        override fun getItemCount() = users.size
        
        class UserViewHolder(
            itemView: View,
            private val onUserClickListener: ((User) -> Unit)?
        ) : RecyclerView.ViewHolder(itemView) {
            private val textUserName = itemView.findViewById<TextView>(R.id.textUserName)
            private val textUserStatus = itemView.findViewById<TextView>(R.id.textUserStatus)
            private val textUserInitial = itemView.findViewById<TextView>(R.id.textUserInitial)
            
            fun bind(user: User) {
                // Hiển thị tên thân thiện thay vì userId
                val displayName = user.getDisplayNameOrShortId()
                textUserName?.text = displayName
                textUserStatus?.text = "Đang online"
                
                // Lấy chữ cái đầu từ tên hiển thị
                val initial = displayName.firstOrNull()?.uppercase() ?: "U"
                textUserInitial?.text = initial
                
                // Set click listener on the entire item
                itemView.setOnClickListener {
                    onUserClickListener?.invoke(user)
                }
            }
        }
    }
}
