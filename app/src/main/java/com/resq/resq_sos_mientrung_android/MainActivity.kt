package com.resq.resq_sos_mientrung_android

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.resq.resq_sos_mientrung_android.databinding.ActivityMainBinding
import com.resq.resq_sos_mientrung_android.bridgefy.BridgefyManager
import com.resq.resq_sos_mientrung_android.fragments.ChatFragment
import com.resq.resq_sos_mientrung_android.fragments.MapFragment
import com.resq.resq_sos_mientrung_android.fragments.RescuersFragment
import com.resq.resq_sos_mientrung_android.fragments.UsersFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var currentFragment: Fragment? = null
    private var selectedTab = -1 // Khởi tạo -1 để đảm bảo load lần đầu
    private var navBarView: View? = null
    private var selectedTabIndicator: View? = null
    private val animationDuration = 300L
    private val argbEvaluator = ArgbEvaluator()
    
    // Cache fragments to avoid recreating
    private val fragments = mutableMapOf<Int, Fragment>()
    
    // Flag để tránh multiple transactions đồng thời
    private var isFragmentTransactionInProgress = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingFragmentPosition: Int? = null
    
    // Tags cho mỗi fragment
    private val fragmentTags = mapOf(
        0 to "fragment_rescuers",
        1 to "fragment_users",
        2 to "fragment_chat",
        3 to "fragment_map"
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
        selectedTabIndicator = navBarView?.findViewById(R.id.selectedTabIndicator)
        
        // Initialize Bridgefy SDK
        BridgefyManager.getInstance(this).initialize(this)
        
        // Setup navigation tabs after layout is measured
        navBarView?.post {
            setupNavigationBar()
            // Load default fragment (Rescuers) after layout is ready
            loadFragment(0)
        }
    }

    private fun setupNavigationBar() {
        val navView = navBarView ?: return
        val tabRescuers = navView.findViewById<View>(R.id.tabRescuers)
        val tabUsers = navView.findViewById<View>(R.id.tabUsers)
        val tabChat = navView.findViewById<View>(R.id.tabChat)
        val tabMap = navView.findViewById<View>(R.id.tabMap)

        tabRescuers.setOnClickListener { 
            performHapticFeedback()
            loadFragmentSafely(0) 
        }
        tabUsers.setOnClickListener { 
            performHapticFeedback()
            loadFragmentSafely(1) 
        }
        tabChat.setOnClickListener { 
            performHapticFeedback()
            loadFragmentSafely(2) 
        }
        tabMap.setOnClickListener { 
            performHapticFeedback()
            loadFragmentSafely(3) 
        }
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
                0 -> RescuersFragment()
                1 -> UsersFragment()
                2 -> ChatFragment()
                3 -> MapFragment()
                else -> RescuersFragment()
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
        // Load Chat fragment (position 2)
        loadFragmentSafely(2)
        
        // Set the selected user in ChatFragment after a short delay to ensure fragment is ready
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                val chatFragment = fragments[2] as? ChatFragment
                chatFragment?.setPrivateChatMode(userId, displayName)
            }
        }, 300)
    }

    private fun updateNavigationBar(selectedPosition: Int) {
        try {
            val navView = navBarView ?: return
            val indicator = selectedTabIndicator ?: return

            val tabs = listOf(
                navView.findViewById<View>(R.id.tabRescuers),
                navView.findViewById<View>(R.id.tabUsers),
                navView.findViewById<View>(R.id.tabChat),
                navView.findViewById<View>(R.id.tabMap)
            )

            val icons = listOf(
                navView.findViewById<ImageView>(R.id.iconRescuers),
                navView.findViewById<ImageView>(R.id.iconUsers),
                navView.findViewById<ImageView>(R.id.iconChat),
                navView.findViewById<ImageView>(R.id.iconMap)
            )

            val texts = listOf(
                navView.findViewById<TextView>(R.id.textRescuersTab),
                navView.findViewById<TextView>(R.id.textUsersTab),
                navView.findViewById<TextView>(R.id.textChatTab),
                navView.findViewById<TextView>(R.id.textMapTab)
            )

            // Check if all views are found
            if (tabs.any { it == null } || icons.any { it == null } || texts.any { it == null }) {
                return
            }

            val selectedColor = getColor(R.color.nav_selected)
            val unselectedColor = getColor(R.color.nav_unselected)

            // Animate indicator position
            animateIndicatorPosition(selectedPosition, tabs)

            // Animate tabs
            for (i in tabs.indices) {
                val isSelected = i == selectedPosition
                val icon = icons[i] ?: continue
                val text = texts[i] ?: continue

                if (isSelected) {
                    // Animate to selected state
                    animateIconScale(icon, true)
                    animateColorChange(icon, unselectedColor, selectedColor, true)
                    animateColorChange(text, unselectedColor, selectedColor, false)
                } else {
                    // Animate to unselected state
                    animateIconScale(icon, false)
                    animateColorChange(icon, selectedColor, unselectedColor, true)
                    animateColorChange(text, selectedColor, unselectedColor, false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateIndicatorPosition(position: Int, tabs: List<View>) {
        try {
            val indicator = selectedTabIndicator ?: return
            val targetTab = tabs.getOrNull(position) ?: return

            // Ensure views are measured
            if (targetTab.width == 0 || targetTab.height == 0) {
                targetTab.post {
                    animateIndicatorPosition(position, tabs)
                }
                return
            }

            // Calculate target position - both indicator and tabs are in the same FrameLayout
            val targetX = targetTab.left.toFloat()
            val targetWidth = targetTab.width.toFloat()

            // Initialize indicator position if first time
            if (indicator.visibility != View.VISIBLE || indicator.alpha == 0f) {
                indicator.x = targetX
                val layoutParams = indicator.layoutParams
                layoutParams.width = targetWidth.toInt()
                indicator.layoutParams = layoutParams
                indicator.alpha = 1.0f
                indicator.visibility = View.VISIBLE
                return
            }

            // Animate X position
            val currentX = indicator.x
            if (kotlin.math.abs(currentX - targetX) > 1f) {
                val xAnimator = ObjectAnimator.ofFloat(indicator, "x", currentX, targetX)
                xAnimator.duration = animationDuration
                xAnimator.interpolator = DecelerateInterpolator()
                xAnimator.start()
            }

            // Animate width
            val currentWidth = indicator.width.toFloat()
            if (kotlin.math.abs(currentWidth - targetWidth) > 1f) {
                val widthAnimator = ValueAnimator.ofFloat(currentWidth, targetWidth)
                widthAnimator.duration = animationDuration
                widthAnimator.interpolator = DecelerateInterpolator()
                widthAnimator.addUpdateListener { animator ->
                    try {
                        val width = (animator.animatedValue as Float).toInt()
                        val layoutParams = indicator.layoutParams
                        layoutParams.width = width
                        indicator.layoutParams = layoutParams
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                widthAnimator.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateIconScale(icon: ImageView, isSelected: Boolean) {
        try {
            val scale = if (isSelected) 1.2f else 1.0f
            icon.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(animationDuration)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun animateColorChange(view: View, fromColor: Int, toColor: Int, isIcon: Boolean) {
        try {
            // Skip animation if colors are the same
            if (fromColor == toColor) {
                if (isIcon && view is ImageView) {
                    view.setColorFilter(toColor)
                } else if (view is TextView) {
                    view.setTextColor(toColor)
                }
                return
            }

            val animator = ValueAnimator.ofObject(argbEvaluator, fromColor, toColor)
            animator.duration = animationDuration
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { animator ->
                try {
                    val color = animator.animatedValue as Int
                    if (isIcon && view is ImageView) {
                        view.setColorFilter(color)
                    } else if (view is TextView) {
                        view.setTextColor(color)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            animator.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}