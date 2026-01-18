package com.resq.resq_sos_mientrung_android

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.resq.resq_sos_mientrung_android.databinding.ActivityMainBinding
import com.resq.resq_sos_mientrung_android.bridgefy.BridgefyManager
import com.resq.resq_sos_mientrung_android.dialogs.MoreMenuBottomSheet
import com.resq.resq_sos_mientrung_android.fragments.AIChatbotFragment
import com.resq.resq_sos_mientrung_android.fragments.ChatFragment
import com.resq.resq_sos_mientrung_android.fragments.HomeFragment
import com.resq.resq_sos_mientrung_android.fragments.MapFragment
import com.resq.resq_sos_mientrung_android.fragments.RescuersFragment
import com.resq.resq_sos_mientrung_android.fragments.UsersFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var currentFragment: Fragment? = null
    private var selectedTab = -1 // Khởi tạo -1 để đảm bảo load lần đầu
    private var navBarView: View? = null
    private val animationDuration = 250L
    private val argbEvaluator = ArgbEvaluator()
    
    // Cache fragments to avoid recreating
    private val fragments = mutableMapOf<Int, Fragment>()
    
    // Flag để tránh multiple transactions đồng thời
    private var isFragmentTransactionInProgress = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingFragmentPosition: Int? = null
    
    // Tags cho mỗi fragment
    private val fragmentTags = mapOf(
        0 to "fragment_home",
        1 to "fragment_rescuers",
        2 to "fragment_users",
        3 to "fragment_chat",
        4 to "fragment_map"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force dark mode as default for battery saving (priority for SOS app)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inflate navigation bar
        navBarView = layoutInflater.inflate(R.layout.bottom_navigation_bar, binding.navBarContainer, true)
        
        // Initialize Bridgefy SDK
        BridgefyManager.getInstance(this).initialize(this)
        
        // Setup navigation tabs after layout is measured
        navBarView?.post {
            setupNavigationBar()
            // Load default fragment (Home) after layout is ready
            loadFragment(0)
        }
        
        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val aiChatbotFragment = supportFragmentManager.findFragmentByTag("fragment_ai_chatbot")
                if (aiChatbotFragment != null && aiChatbotFragment.isVisible) {
                    supportFragmentManager.popBackStack()
                    // Restore the previous tab
                    if (selectedTab >= 0) {
                        loadFragmentSafely(selectedTab)
                    } else {
                        loadFragmentSafely(0) // Default to home
                    }
                } else {
                    // Default back behavior
                    if (!supportFragmentManager.popBackStackImmediate()) {
                        finish()
                    }
                }
            }
        })
    }

    private fun setupNavigationBar() {
        val navView = navBarView ?: return
        val tabHome = navView.findViewById<View>(R.id.tabHome)
        val tabRescuers = navView.findViewById<View>(R.id.tabRescuers)
        val tabUsers = navView.findViewById<View>(R.id.tabUsers)
        val tabChat = navView.findViewById<View>(R.id.tabChat)
        val tabMap = navView.findViewById<View>(R.id.tabMap)

        tabHome.setOnClickListener { 
            performHapticFeedback()
            loadFragmentSafely(0) 
        }
        tabChat.setOnClickListener { 
            performHapticFeedback()
            loadFragmentSafely(3) 
        }
        tabUsers.setOnClickListener { 
            performHapticFeedback()
            loadFragmentSafely(2) 
        }
        tabRescuers.setOnClickListener { 
            performHapticFeedback()
            showMoreMenu()
        }
        tabMap.setOnClickListener { 
            performHapticFeedback()
            loadFragmentSafely(4) 
        }
    }
    
    private fun showMoreMenu() {
        val bottomSheet = MoreMenuBottomSheet.newInstance()
        bottomSheet.setOnMenuItemClickListener(object : MoreMenuBottomSheet.OnMenuItemClickListener {
            override fun onRescuersClick() {
                loadFragmentSafely(1)
            }

            override fun onNewsClick() {
                // Navigate to News - có thể mở HomeFragment và scroll đến phần News
                // hoặc mở một Activity/Fragment riêng cho News
                Toast.makeText(this@MainActivity, "Tính năng Tin tức đang phát triển", Toast.LENGTH_SHORT).show()
            }

            override fun onAIChatbotClick() {
                // Mở Chat Bot AI
                showAIChatbotFragment()
            }

            override fun onSettingsClick() {
                // Mở cài đặt
                Toast.makeText(this@MainActivity, "Tính năng Cài đặt đang phát triển", Toast.LENGTH_SHORT).show()
            }

            override fun onAboutClick() {
                // Hiển thị thông tin ứng dụng
                showAboutDialog()
            }
        })
        bottomSheet.show(supportFragmentManager, MoreMenuBottomSheet.TAG)
    }
    
    private fun showAboutDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("ResQ SOS Miền Trung")
        builder.setMessage(
            "Phiên bản: 1.0.0\n\n" +
            "Ứng dụng hỗ trợ cứu hộ khẩn cấp trong thiên tai.\n\n" +
            "Tính năng chính:\n" +
            "• Gửi tín hiệu SOS\n" +
            "• Chat offline qua Bluetooth Mesh\n" +
            "• Tìm kiếm người dùng xung quanh\n" +
            "• Bản đồ cứu hộ\n\n" +
            "© 2026 ResQ Team"
        )
        builder.setPositiveButton("Đóng") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun performHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(10)
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted or vibrator not available, silently ignore
            e.printStackTrace()
        } catch (e: Exception) {
            // Any other error, silently ignore
            e.printStackTrace()
        }
    }

    /**
     * Safe wrapper để load fragment, tránh multiple transactions đồng thời
     */
    private fun loadFragmentSafely(position: Int) {
        // Nếu đang có transaction, lưu lại position để xử lý sau
        if (isFragmentTransactionInProgress) {
            pendingFragmentPosition = position
            return
        }
        
        // Nếu đang ở tab này rồi, không cần load lại
        if (selectedTab == position && currentFragment != null) {
            return
        }
        
        loadFragment(position)
    }
    
    private fun loadFragment(position: Int) {
        // Kiểm tra activity còn valid không
        if (isFinishing || isDestroyed) {
            return
        }

        // Kiểm tra fragment manager còn valid không
        if (supportFragmentManager.isDestroyed) {
            return
        }

        val tag = fragmentTags[position] ?: return
        val fragment = getOrCreateFragment(position, tag)

        isFragmentTransactionInProgress = true
        val previousTab = selectedTab
        selectedTab = position
        currentFragment = fragment

        try {
            val transaction = supportFragmentManager.beginTransaction()
            
            // Hide tất cả các fragment khác đang hiển thị
            for ((pos, fragTag) in fragmentTags) {
                if (pos != position) {
                    supportFragmentManager.findFragmentByTag(fragTag)?.let { frag ->
                        if (frag.isAdded && !frag.isHidden) {
                            transaction.hide(frag)
                        }
                    }
                }
            }
            
            // Show hoặc add fragment được chọn
            if (fragment.isAdded) {
                transaction.show(fragment)
            } else {
                transaction.add(R.id.fragmentContainer, fragment, tag)
            }
            
            // Commit transaction
            if (!supportFragmentManager.isStateSaved) {
                transaction.commitNow()
                onFragmentTransactionComplete(position)
            } else {
                transaction.commitAllowingStateLoss()
                handler.post {
                    onFragmentTransactionComplete(position)
                }
            }
        } catch (e: IllegalStateException) {
            // Fragment manager đã bị destroy hoặc state saved
            isFragmentTransactionInProgress = false
            selectedTab = previousTab
            e.printStackTrace()
        } catch (e: Exception) {
            isFragmentTransactionInProgress = false
            selectedTab = previousTab
            e.printStackTrace()
        }
    }
    
    private fun getOrCreateFragment(position: Int, tag: String): Fragment {
        // Kiểm tra fragment đã tồn tại trong FragmentManager chưa
        val existingFragment = supportFragmentManager.findFragmentByTag(tag)
        if (existingFragment != null) {
            fragments[position] = existingFragment
            return existingFragment
        }
        
        // Tạo mới nếu chưa có
        return fragments.getOrPut(position) {
            when (position) {
                0 -> HomeFragment()
                1 -> RescuersFragment()
                2 -> UsersFragment()
                3 -> ChatFragment()
                4 -> MapFragment()
                else -> HomeFragment()
            }
        }
    }
    
    private fun onFragmentTransactionComplete(position: Int) {
        isFragmentTransactionInProgress = false
        updateNavigationBarSafely(position)
        
        // Xử lý pending fragment nếu có
        pendingFragmentPosition?.let { pendingPos ->
            val pos = pendingPos
            pendingFragmentPosition = null
            if (pos != position) {
                handler.post {
                    loadFragmentSafely(pos)
                }
            }
        }
    }
    
    private fun updateNavigationBarSafely(position: Int) {
        if (isFinishing || isDestroyed) {
            return
        }
        
        navBarView?.post {
            if (!isFinishing && !isDestroyed) {
                updateNavigationBar(position)
            }
        }
    }
    
    /**
     * Navigate to Chat tab and set the selected user ID
     * This method can be called from fragments to start a chat with a specific user
     */
    fun navigateToChatWithUser(userId: String, displayName: String = "") {
        // Load Chat fragment (position 3)
        loadFragmentSafely(3)
        
        // Set the selected user in ChatFragment after a short delay to ensure fragment is ready
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                val chatFragment = fragments[3] as? ChatFragment
                chatFragment?.setPrivateChatMode(userId, displayName)
            }
        }, 300)
    }

    private fun updateNavigationBar(selectedPosition: Int) {
        try {
            val navView = navBarView ?: return

            // Map positions: 0=Home, 1=Rescuers, 2=Users, 3=Chat, 4=Map
            // UI order: Home, Chat, Users, Rescuers (More), Map(FAB)
            
            val pills = listOf(
                navView.findViewById<View>(R.id.tabHomePill),      // position 0
                navView.findViewById<View>(R.id.tabRescuersPill),  // position 1
                navView.findViewById<View>(R.id.tabUsersPill),     // position 2
                navView.findViewById<View>(R.id.tabChatPill),      // position 3
                null  // Map doesn't have a pill, it's a FAB
            )

            val icons = listOf(
                navView.findViewById<ImageView>(R.id.iconHome),     // position 0
                navView.findViewById<ImageView>(R.id.iconRescuers), // position 1
                navView.findViewById<ImageView>(R.id.iconUsers),    // position 2
                navView.findViewById<ImageView>(R.id.iconChat),     // position 3
                navView.findViewById<ImageView>(R.id.iconMap)       // position 4
            )

            val mapFab = navView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.tabMap)

            val selectedIconColor = getColor(R.color.nav_icon_selected)
            val unselectedIconColor = getColor(R.color.nav_icon_unselected)
            val pillColor = getColor(R.color.nav_pill_selected)
            val fabColor = getColor(R.color.nav_fab_bg)

            // Update tabs in the main nav bar (positions 0, 1, 2, 3)
            for (i in 0..3) {
                val isSelected = i == selectedPosition
                val pill = pills[i]
                val icon = icons[i] ?: continue

                if (isSelected) {
                    // Show pill with animation
                    pill?.let {
                        it.visibility = View.VISIBLE
                        it.alpha = 0f
                        it.animate()
                            .alpha(1f)
                            .setDuration(animationDuration)
                            .start()
                    }
                    // Change icon color to white (selected)
                    icon.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(animationDuration)
                        .start()
                    animateIconColor(icon, unselectedIconColor, selectedIconColor)
                } else {
                    // Hide pill with animation
                    pill?.let {
                        it.animate()
                            .alpha(0f)
                            .setDuration(animationDuration)
                            .withEndAction {
                                it.visibility = View.INVISIBLE
                            }
                            .start()
                    }
                    // Change icon color to grey (unselected)
                    icon.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(animationDuration)
                        .start()
                    animateIconColor(icon, selectedIconColor, unselectedIconColor)
                }
            }

            // Update Map FAB - always orange with white icon
            mapFab?.setCardBackgroundColor(fabColor)
            icons[4]?.setColorFilter(getColor(R.color.white))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateIconColor(icon: ImageView, fromColor: Int, toColor: Int) {
        if (fromColor == toColor) {
            icon.setColorFilter(toColor)
            return
        }
        
        val animator = ValueAnimator.ofObject(argbEvaluator, fromColor, toColor)
        animator.duration = animationDuration
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { anim ->
            try {
                val color = anim.animatedValue as Int
                icon.setColorFilter(color)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        animator.start()
    }
    
    private fun showAIChatbotFragment() {
        try {
            val aiChatbotFragment = AIChatbotFragment()
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragmentContainer, aiChatbotFragment, "fragment_ai_chatbot")
            transaction.addToBackStack("ai_chatbot")
            transaction.commit()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể mở Chat Bot AI", Toast.LENGTH_SHORT).show()
        }
    }
    
}