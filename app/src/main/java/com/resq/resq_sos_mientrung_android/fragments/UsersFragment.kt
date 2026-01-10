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

class UsersFragment : Fragment(), BridgefyManager.BridgefyListener {
    private var _binding: FragmentUsersBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var bridgefyManager: BridgefyManager
    private val usersAdapter = UsersAdapter()
    private val nearbyUsers = mutableListOf<User>()
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 1002
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        bridgefyManager = BridgefyManager.getInstance(requireContext())
        bridgefyManager.addListener(this)
        
        setupRecyclerView()
        updateBridgefyStatus()
        checkAndRequestPermissions()
        
        // Periodically check status (every 500ms) until initialized
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val statusChecker = object : Runnable {
            override fun run() {
                if (!bridgefyManager.isInitialized()) {
                    updateBridgefyStatus()
                    handler.postDelayed(this, 500)
                } else {
                    updateBridgefyStatus()
                }
            }
        }
        handler.post(statusChecker)
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
        updateBridgefyStatus()
        loadNearbyUsers()
        Snackbar.make(
            binding.root,
            "Bridgefy đã sẵn sàng! Đang tìm người dùng gần đây...",
            Snackbar.LENGTH_SHORT
        ).show()
    }
    
    override fun onBridgefyStartError(error: String) {
        android.util.Log.e("UsersFragment", "Lỗi khởi động Bridgefy: $error")
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
    
    override fun onUserFound(user: User) {
        android.util.Log.d("UsersFragment", "Tìm thấy người dùng: ${user.userId}")
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
    
    override fun onUserLost(user: User) {
        android.util.Log.d("UsersFragment", "Mất kết nối với người dùng: ${user.userId}")
        nearbyUsers.remove(user)
        updateUsersList()
        val count = nearbyUsers.size
        Snackbar.make(
            binding.root,
            "Mất kết nối với người dùng (Còn lại: $count người)",
            Snackbar.LENGTH_SHORT
        ).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        bridgefyManager.removeListener(this)
        _binding = null
    }
    
    // Adapter for users list
    private class UsersAdapter : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {
        private val users = mutableListOf<User>()
        
        fun submitList(newUsers: List<User>) {
            users.clear()
            users.addAll(newUsers)
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user, parent, false)
            return UserViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val user = users[position]
            holder.bind(user)
        }
        
        override fun getItemCount() = users.size
        
        class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textUserName = itemView.findViewById<TextView>(R.id.textUserName)
            private val textUserStatus = itemView.findViewById<TextView>(R.id.textUserStatus)
            private val textUserInitial = itemView.findViewById<TextView>(R.id.textUserInitial)
            
            fun bind(user: User) {
                val shortId = user.userId.take(8)
                textUserName?.text = "Người dùng $shortId"
                textUserStatus?.text = "Đang online"
                textUserInitial?.text = shortId.firstOrNull()?.uppercase() ?: "U"
            }
        }
    }
}
