package com.resq.resq_sos_mientrung_android.services

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Weather data models for WeatherAPI.com
 */
data class WeatherResponse(
    @SerializedName("location") val location: LocationInfo,
    @SerializedName("current") val current: CurrentWeather
)

data class LocationInfo(
    @SerializedName("name") val name: String,
    @SerializedName("region") val region: String,
    @SerializedName("country") val country: String,
    @SerializedName("lat") val latitude: Double,
    @SerializedName("lon") val longitude: Double
)

data class CurrentWeather(
    @SerializedName("temp_c") val temperature: Double,
    @SerializedName("feelslike_c") val feelsLike: Double,
    @SerializedName("humidity") val humidity: Int,
    @SerializedName("pressure_mb") val pressure: Double,
    @SerializedName("condition") val condition: WeatherCondition,
    @SerializedName("wind_kph") val windSpeed: Double,
    @SerializedName("precip_mm") val precipitation: Double
)

data class WeatherCondition(
    @SerializedName("text") val text: String,
    @SerializedName("icon") val icon: String,
    @SerializedName("code") val code: Int
)

/**
 * Weather API service interface for WeatherAPI.com
 */
interface WeatherApiService {
    @GET("current.json")
    suspend fun getCurrentWeather(
        @Query("key") apiKey: String,
        @Query("q") query: String,
        @Query("lang") lang: String = "vi"
    ): WeatherResponse
}

/**
 * Weather Service
 * Uses WeatherAPI.com (free tier: 1 million calls/month)
 */
object WeatherService {
    private const val BASE_URL = "https://api.weatherapi.com/v1/"
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val apiService = retrofit.create(WeatherApiService::class.java)
    
    /**
     * Get current weather by coordinates
     * @param latitude Latitude
     * @param longitude Longitude
     * @param apiKey WeatherAPI.com API key (get from https://www.weatherapi.com/)
     * @return WeatherResponse or null if error
     */
    suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
        apiKey: String
    ): WeatherResponse? {
        return try {
            // WeatherAPI.com accepts coordinates as "lat,lon"
            val query = "$latitude,$longitude"
            apiService.getCurrentWeather(apiKey, query)
        } catch (e: Exception) {
            android.util.Log.e("WeatherService", "Error fetching weather: ${e.message}", e)
            null
        }
    }
}
