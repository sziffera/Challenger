package com.sziffer.challenger.model.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.utils.Constants.KEY_WEATHER
import com.sziffer.challenger.utils.Constants.KEY_WEATHER_DATA
import com.sziffer.challenger.utils.UpdateTypes
import com.sziffer.challenger.utils.WEATHER_KEY
import com.sziffer.challenger.utils.updateRefreshDate
import okhttp3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.*

class WeatherRequest(
    private val weatherResultListener: WeatherResultListener,
    private val location: Location,
    private val context: Context
) {

    private val okHttpClient = OkHttpClient()

    /** Downloads weather data based on lastKnownLocation */
    fun fetchWeatherData() {

        var shouldShowUv: Boolean

        val loc = Locale.getDefault().isO3Country.also {
            Log.d("LOCALE", it)
        }

        //fetching current weather data
        val request = Request.Builder()
            .url("${WEATHER_URL}lat=${location.latitude}&lon=${location.longitude}")
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.i("WEATHER", e.toString())
            }


            @SuppressLint("SimpleDateFormat")
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val data = response.body?.string()
                    if (data != null) {
                        val typeJson = object : TypeToken<OneCallWeather>() {}.type
                        val weatherData = Gson()
                            .fromJson<OneCallWeather>(data, typeJson)
                        Log.i("WEATHER_ALERTS", weatherData.alerts.toString())

                        val cal = Calendar.getInstance()
                        val tz = cal.timeZone
                        val format = SimpleDateFormat("HH:mm")
                        format.timeZone = tz
                        val localSunset = format.format(Date(weatherData.current.sunset * 1000))
                        val localSunrise = format.format(Date(weatherData.current.sunrise * 1000))
                        Log.i("DATENOW", format.format(Date().time))

                        shouldShowUv =
                            LocalTime.now().isAfter(LocalTime.parse(localSunrise)) &&
                                    LocalTime.now().isBefore(LocalTime.parse(localSunset))

                        //if its not cloudy and not night, fetching UV index
                        if (shouldShowUv && weatherData.current.clouds < 95) {
                            Log.d(
                                "WEATHER",
                                "should show uv is: $shouldShowUv, clouds are above 95"
                            )
                            weatherData.shouldShowUvIndex = true
                        }

                        // updating weather update time for weather
                        updateRefreshDate(UpdateTypes.WEATHER, context)
                        saveWeather(weatherData)

                        //TODO: set back
                        weatherResultListener.weatherFetched(weatherData)

                    }
                } else {
                    Log.i("WEATHER", "WAS NOT SUCCESSFUL")
                }
            }
        })
    }

    private fun saveWeather(weather: OneCallWeather) {
        val sharedPreferences = context.getSharedPreferences(KEY_WEATHER, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString(KEY_WEATHER_DATA, Gson().toJson(weather))
            commit()
        }
    }

    companion object {
        private const val SHOWCASE_ID = "MainActivity"
        private const val WEATHER_URL =
            "https://api.openweathermap.org/data/2.5/onecall?" +
                    "appid=$WEATHER_KEY&exclude=daily&units=metric&"

    }

}