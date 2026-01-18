package com.resq.resq_sos_mientrung_android.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.resq.resq_sos_mientrung_android.R
import com.resq.resq_sos_mientrung_android.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        // Notification icon
        binding.notificationIcon.setOnClickListener {
            // TODO: Navigate to notifications screen
        }
        
        // SOS Button
        binding.btnSOS.setOnClickListener {
            // TODO: Implement SOS functionality
        }
        
        // Grid cards
        binding.cardMapNeedHelp.setOnClickListener {
            // TODO: Navigate to map of people needing help
        }
        
        binding.cardDisasterMap.setOnClickListener {
            // TODO: Navigate to disaster map
        }
        
        binding.cardEvacuationMap.setOnClickListener {
            // TODO: Navigate to evacuation map
        }
        
        binding.cardPostWarning.setOnClickListener {
            // TODO: Navigate to post warning screen
        }
        
        binding.cardPostHelpRequest.setOnClickListener {
            // TODO: Navigate to post help request screen
        }
        
        binding.cardContact.setOnClickListener {
            // TODO: Navigate to contact screen
        }
        
        binding.cardNews.setOnClickListener {
            // TODO: Navigate to news screen
        }
        
        binding.cardDisasterNews.setOnClickListener {
            // TODO: Navigate to disaster news screen
        }
        
        binding.cardAIAssistant.setOnClickListener {
            // TODO: Navigate to AI assistant screen
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
