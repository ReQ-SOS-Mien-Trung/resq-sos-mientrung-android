package com.resq.resq_sos_mientrung_android.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.resq.resq_sos_mientrung_android.BuildConfig
import com.resq.resq_sos_mientrung_android.R
import com.resq.resq_sos_mientrung_android.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupVersionInfo()
        setupClickListeners()
        setupSwitches()
    }
    
    private fun setupVersionInfo() {
        try {
            val versionName = BuildConfig.VERSION_NAME
            val versionCode = BuildConfig.VERSION_CODE
            binding.tvVersionInfo.text = "Phiên bản $versionName"
            binding.tvBuildInfo.text = "Build: $versionCode"
        } catch (e: Exception) {
            binding.tvVersionInfo.text = "Phiên bản 1.0.0"
            binding.tvBuildInfo.text = "Build: 1"
        }
    }
    
    private fun setupSwitches() {
        // Dark Mode Switch
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        binding.switchDarkMode.isChecked = currentNightMode == AppCompatDelegate.MODE_NIGHT_YES
        
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
        
        // Notifications Switch - Default on
        binding.switchNotifications.isChecked = true
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) "Đã bật thông báo" else "Đã tắt thông báo"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        
        // Bluetooth Mesh Switch - Default on
        binding.switchBluetoothMesh.isChecked = true
        binding.switchBluetoothMesh.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) "Đã bật Bluetooth Mesh" else "Đã tắt Bluetooth Mesh"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
        
        // Auto SOS Switch - Default off
        binding.switchAutoSOS.isChecked = false
        binding.switchAutoSOS.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showAutoSOSWarningDialog()
            } else {
                Toast.makeText(requireContext(), "Đã tắt tự động gửi SOS", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showAutoSOSWarningDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Tự động gửi SOS")
            .setMessage(
                "Tính năng này sẽ tự động gửi tín hiệu SOS khi phát hiện:\n\n" +
                "• Động đất mạnh\n" +
                "• Tai nạn nghiêm trọng\n" +
                "• Mất liên lạc đột ngột\n\n" +
                "Bạn có chắc muốn bật tính năng này?"
            )
            .setPositiveButton("Bật") { dialog, _ ->
                Toast.makeText(requireContext(), "Đã bật tự động gửi SOS", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                binding.switchAutoSOS.isChecked = false
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        // Language setting
        binding.settingLanguage.setOnClickListener {
            showLanguageDialog()
        }
        
        // About app
        binding.settingAbout.setOnClickListener {
            openAboutScreen()
        }
        
        // Privacy policy
        binding.settingPrivacy.setOnClickListener {
            openUrl("https://resq-sos.com/privacy")
        }
        
        // Terms of service
        binding.settingTerms.setOnClickListener {
            openUrl("https://resq-sos.com/terms")
        }
        
        // Rate app
        binding.settingRate.setOnClickListener {
            rateApp()
        }
        
        // Share app
        binding.settingShare.setOnClickListener {
            shareApp()
        }
    }
    
    private fun showLanguageDialog() {
        val languages = arrayOf("Tiếng Việt", "English")
        var selectedIndex = 0
        
        AlertDialog.Builder(requireContext())
            .setTitle("Chọn ngôn ngữ")
            .setSingleChoiceItems(languages, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Xác nhận") { dialog, _ ->
                binding.tvLanguageValue.text = languages[selectedIndex]
                Toast.makeText(requireContext(), "Đã chọn: ${languages[selectedIndex]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun openAboutScreen() {
        try {
            val aboutFragment = AboutFragment.newInstance()
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right,
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right
                )
                .hide(this)
                .add(com.resq.resq_sos_mientrung_android.R.id.fragmentContainer, aboutFragment, "fragment_about")
                .addToBackStack("about")
                .commit()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Không thể mở trang Về ứng dụng", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Không thể mở liên kết", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun rateApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${requireContext().packageName}"))
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${requireContext().packageName}"))
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), "Không thể mở Play Store", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ResQ SOS Miền Trung")
            putExtra(Intent.EXTRA_TEXT, 
                "Tải ứng dụng ResQ SOS Miền Trung - Hỗ trợ cứu hộ khẩn cấp!\n\n" +
                "• Gửi tín hiệu SOS\n" +
                "• Chat không cần internet\n" +
                "• Bản đồ cứu hộ\n\n" +
                "https://play.google.com/store/apps/details?id=${requireContext().packageName}"
            )
        }
        startActivity(Intent.createChooser(shareIntent, "Chia sẻ ứng dụng"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance() = SettingsFragment()
    }
}
