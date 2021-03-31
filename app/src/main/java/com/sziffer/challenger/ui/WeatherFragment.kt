package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sziffer.challenger.R
import com.sziffer.challenger.databinding.FragmentWeatherBinding
import com.sziffer.challenger.model.ActivityMainViewModel
import com.sziffer.challenger.model.weather.MinuteData
import com.sziffer.challenger.model.weather.OneCallWeather
import com.sziffer.challenger.ui.weather.WeatherForecastRecyclerViewAdapter
import com.sziffer.challenger.utils.getStringFromNumber
import com.sziffer.challenger.utils.setBeaufortWindColor
import com.sziffer.challenger.utils.setUvIndexColor
import java.text.SimpleDateFormat
import java.util.*


class WeatherFragment : Fragment() {

    private var _binding: FragmentWeatherBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ActivityMainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("WEATHER", "oncreate")

        viewModel.weatherLiveData.observe(this, {
            Log.d("VIEW_MODEL_OBSERVER", "CALLED")
            setWeather(it)
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeatherBinding.inflate(layoutInflater, container, false)
        binding.weatherForecastLinearLayout.alpha = 0f
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    private fun setWeather(weatherData: OneCallWeather) {

        binding.weatherForecastLinearLayout
            .animate()
            .setDuration(1000)
            .alpha(1f)
            .setInterpolator(DecelerateInterpolator())
            .start()

        //hiding the loading panel
        binding.loadingPanel.visibility = View.GONE

        binding.degreesTextView.text = "${getStringFromNumber(0, weatherData.current.temp)}°"
        binding.feelsLikeTextView.text = getString(R.string.feels_like) +
                " ${getStringFromNumber(0, weatherData.current.feels_like)}°"
        binding.humidityTextView.text = "${getString(R.string.humidity)}:" +
                " ${getStringFromNumber(0, weatherData.current.humidity)}%"
        //binding.minDegreesTextView.text = "${getStringFromNumber(0, weatherData.current.temp_min)}°"
        //binding.maxDegreesTextView.text = "${getStringFromNumber(0, current.main.temp_max)}°"

        val windSpeed = weatherData.current.wind_speed.times(3.6)
        binding.windSpeedTextView.text = "${windSpeed.toInt()}km/h"
        binding.windDirectionImageView.rotation = (-90f + weatherData.current.wind_deg).toFloat()
        setBeaufortWindColor(windSpeed = windSpeed.toInt(), binding.windDirectionImageView)

//        setWeatherIcon(
//            current.weather[0].id, binding.weatherBackgroundImageView,
//            requireContext(), true
//        )
        val cal = Calendar.getInstance()
        val tz = cal.timeZone
        val format = SimpleDateFormat("HH:mm")
        format.timeZone = tz
        val localSunset = format.format(Date(weatherData.current.sunset * 1000))
        val localSunrise = format.format(Date(weatherData.current.sunrise * 1000))
        Log.i("DATENOW", format.format(Date().time))
        binding.sunriseTextView.text = localSunrise
        binding.sunsetTextView.text = localSunset

        val forecastRecyclerViewAdapter =
            WeatherForecastRecyclerViewAdapter(weatherData.hourly, requireContext())

        if (weatherData.shouldShowUvIndex) {
            Log.d("UV", "should be set")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                setUvIndexColor(weatherData.current.uvi, binding.uvIndexTextView, requireContext())
            binding.uvIndexTextView.text = getStringFromNumber(1, weatherData.current.uvi)
        }

        binding.weatherForecastRecyclerView.apply {
            adapter = forecastRecyclerViewAdapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        }

        setOneHourPrecipitation(weatherData.minutely)
    }

    private fun setOneHourPrecipitation(minutely: ArrayList<MinuteData>) {

        //TODO(not implemented)
        var shouldShowForecast: Boolean = false

        for (minuteData in minutely) {
            if (minuteData.precipitation > 0) {
                shouldShowForecast = true
                break
            }
        }
        if (!shouldShowForecast)
            return

    }
}