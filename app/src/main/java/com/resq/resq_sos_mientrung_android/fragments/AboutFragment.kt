package com.resq.resq_sos_mientrung_android.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.resq.resq_sos_mientrung_android.BuildConfig
import com.resq.resq_sos_mientrung_android.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupVersionInfo()
        setupClickListeners()
    }
    
    private fun setupVersionInfo() {
        try {
            binding.tvVersionInfo.text = "Phiên bản ${BuildConfig.VERSION_NAME}"
            binding.tvVersion.text = BuildConfig.VERSION_NAME
            binding.tvBuild.text = BuildConfig.VERSION_CODE.toString()
            binding.tvSdk.text = "API ${Build.VERSION.SDK_INT}"
        } catch (e: Exception) {
            binding.tvVersionInfo.text = "Phiên bản 1.0.0"
            binding.tvVersion.text = "1.0.0"
            binding.tvBuild.text = "1"
            binding.tvSdk.text = "API ${Build.VERSION.SDK_INT}"
        }
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance() = AboutFragment()
    }
}
