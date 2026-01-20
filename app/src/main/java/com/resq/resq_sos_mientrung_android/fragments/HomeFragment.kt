package com.resq.resq_sos_mientrung_android.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.resq.resq_sos_mientrung_android.BuildConfig
import com.resq.resq_sos_mientrung_android.R
import com.resq.resq_sos_mientrung_android.databinding.FragmentHomeBinding
import com.resq.resq_sos_mientrung_android.services.WeatherService
import com.resq.resq_sos_mientrung_android.services.WeatherResponse
import com.resq.resq_sos_mientrung_android.utils.LocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var locationHelper: LocationHelper
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

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
        
        locationHelper = LocationHelper(requireContext())
        
        setupClickListeners()
        loadWeatherData()
    }
    
    private fun loadWeatherData() {
        if (!locationHelper.hasLocationPermission()) {
            requestLocationPermission()
            return
        }
        
        coroutineScope.launch {
            try {
                val location = withContext(Dispatchers.IO) {
                    locationHelper.getCurrentLocation() ?: locationHelper.getLastKnownLocation()
                }
                
                if (location != null) {
                    val weatherApiKey = BuildConfig.WEATHER_API_KEY
                    android.util.Log.d("HomeFragment", "Weather API Key length: ${weatherApiKey.length}")
                    
                    if (weatherApiKey.isEmpty() || 
                        weatherApiKey == "YOUR_WEATHER_API_KEY_HERE" || 
                        weatherApiKey == "YOUR_OPENWEATHER_API_KEY_HERE") {
                        binding.weatherText.text = "Cần cấu hình API key"
                        binding.weatherDescription.text = "Vui lòng thêm WEATHER_API_KEY vào local.properties"
                        android.util.Log.w("HomeFragment", "Weather API key not configured")
                        return@launch
                    }
                    
                    android.util.Log.d("HomeFragment", "Fetching weather for location: ${location.latitude}, ${location.longitude}")
                    val weatherResponse = withContext(Dispatchers.IO) {
                        WeatherService.getCurrentWeather(
                            location.latitude,
                            location.longitude,
                            weatherApiKey
                        )
                    }
                    
                    if (weatherResponse != null) {
                        updateWeatherUI(weatherResponse)
                    } else {
                        binding.weatherText.text = "Không thể tải dữ liệu"
                        binding.weatherDescription.text = "Kiểm tra API key và kết nối mạng"
                        android.util.Log.e("HomeFragment", "Weather response is null")
                    }
                } else {
                    binding.weatherText.text = "Không lấy được vị trí"
                    binding.weatherDescription.text = "Vui lòng bật GPS"
                    android.util.Log.w("HomeFragment", "Location is null")
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error loading weather: ${e.javaClass.simpleName} - ${e.message}", e)
                e.printStackTrace()
                binding.weatherText.text = "Lỗi tải dữ liệu"
                binding.weatherDescription.text = "${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }
    
    private fun updateWeatherUI(weather: WeatherResponse) {
        binding.weatherText.text = weather.location.name
        val description = weather.current.condition.text
        binding.weatherDescription.text = description
        binding.weatherTemperature.text = "${weather.current.temperature.toInt()}°C"
        
        // Update weather icon based on condition code
        updateWeatherIcon(weather.current.condition.code)
    }
    
    /**
     * Update weather icon based on condition code from WeatherAPI.com
     * WeatherAPI.com condition codes: https://www.weatherapi.com/docs/weather_conditions.json
     */
    private fun updateWeatherIcon(conditionCode: Int) {
        val iconRes = when {
            // Sunny/Clear (1000)
            conditionCode == 1000 -> R.drawable.ic_weather_sunny
            // Partly cloudy (1003)
            conditionCode == 1003 -> R.drawable.ic_weather_partly_cloudy
            // Cloudy (1006, 1009)
            conditionCode in 1006..1009 -> R.drawable.ic_weather_cloudy
            // Rain (1063, 1066, 1069, 1072, 1087, 1150-1201, 1240-1246)
            conditionCode in 1063..1246 -> R.drawable.ic_weather_rainy
            // Storm/Thunder (1087, 1273-1282)
            conditionCode in 1273..1282 -> R.drawable.ic_weather_storm
            // Default to sunny
            else -> R.drawable.ic_weather_sunny
        }
        
        binding.weatherIcon.setImageResource(iconRes)
        // Remove tint to show colorful icons
        binding.weatherIcon.clearColorFilter()
    }
    
    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadWeatherData()
            } else {
                binding.weatherText.text = "Cần quyền vị trí"
                binding.weatherDescription.text = "Vui lòng cấp quyền trong Settings"
                Toast.makeText(
                    requireContext(),
                    "Cần quyền vị trí để hiển thị thời tiết",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
        coroutineScope.cancel()
        _binding = null
    }
}
