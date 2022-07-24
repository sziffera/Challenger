package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.sziffer.challenger.R
import com.sziffer.challenger.adapters.WeatherForecastRecyclerViewAdapter
import com.sziffer.challenger.databinding.FragmentWeatherBinding
import com.sziffer.challenger.model.weather.AlertData
import com.sziffer.challenger.model.weather.MinuteData
import com.sziffer.challenger.model.weather.OneCallWeather
import com.sziffer.challenger.utils.*
import com.sziffer.challenger.viewmodels.MainViewModel
import com.sziffer.challenger.viewmodels.NearbyChallengesViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


class WeatherFragment : Fragment(), NetworkStateListener {

    private var _binding: FragmentWeatherBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel

    private lateinit var myNetworkCallback: MyNetworkCallback
    private lateinit var connectivityManager: ConnectivityManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(
            requireActivity(),
            NearbyChallengesViewModelFactory()
        )[MainViewModel::class.java]

        connectivityManager = requireActivity().getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(
            this, connectivityManager
        )


        Log.d("WEATHER", "oncreate")

        viewModel.weatherLiveData.observe(this) {
            Log.d("VIEW_MODEL_OBSERVER", "CALLED")
            setWeather(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeatherBinding.inflate(layoutInflater, container, false)
        binding.weatherForecastLinearLayout.alpha = 0f
        return binding.root
    }


    override fun onStart() {
        if (connectivityManager.allNetworks.isEmpty()) {
            binding.noInternetTextView?.visibility = View.VISIBLE
        }
        myNetworkCallback.registerCallback()
        super.onStart()
    }

    override fun onPause() {
        myNetworkCallback.unregisterCallback()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    @SuppressLint("SetTextI18n")
    private fun setWeather(weatherData: OneCallWeather) {

        checkPrecipitation(weatherData)

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

        binding.currentWeatherDescription?.text =
            weatherData.current.weather[0].description.split(' ')
                .joinToString(" ") { it ->
                    it.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(
                            Locale.getDefault()
                        ) else it.toString()
                    }
                }

        weatherData.alerts?.let { setWeatherAlert(it) }

        //setOneHourPrecipitation(weatherData.minutely)
    }

    private fun checkPrecipitation(weatherData: OneCallWeather) {
        // precipitation in the next hour
        if (weatherData.minutely.any { it.precipitation > 0 }) {
            binding.minutelyPrecipitationLineChart.visibility = View.VISIBLE
            binding.chanceOfWithinOneHourTextView.visibility = View.VISIBLE
            setUpPrecipitationChart(weatherData.minutely)
        }
    }

    private fun setWeatherAlert(alerts: ArrayList<AlertData>) {
        if (alerts.isEmpty()) {
            binding.weatherAlertLinearLayout?.visibility = View.GONE
        } else {
            binding.weatherAlertLinearLayout?.visibility = View.VISIBLE
            alerts.firstOrNull()?.let {
                binding.apply {
                    weatherAlertTitle?.text = it.event
                    weatherAlertDescription?.text = it.description
                }
            }
        }
    }


    // region precipitation chart

    private fun setUpPrecipitationChart(minutelyData: ArrayList<MinuteData>) {
        initLineCharts(binding.minutelyPrecipitationLineChart)
        val entries = ArrayList<Entry>()

        minutelyData.forEachIndexed { index, minuteData ->
            entries.add(
                Entry(
                    (index + 1).toFloat(),
                    minuteData.precipitation.times(100).roundToInt().toFloat()
                )
            )
        }

        addDataToChart(
            entries,
            binding.minutelyPrecipitationLineChart,
            getString(R.string.elevation),
            ContextCompat.getColor(requireContext(), R.color.colorPlus),
            ContextCompat.getColor(requireContext(), R.color.colorMinus)
        )
    }


    private fun initLineCharts(lineChart: LineChart) {
        with(lineChart) {
            isDragEnabled = false
            setPinchZoom(false)
            setTouchEnabled(false)
            extraBottomOffset = -50f
            legend.textColor = ContextCompat.getColor(context, android.R.color.white)
            description.isEnabled = false
            xAxis.apply {
                //enableAxisLineDashedLine(10f, 10f, 0f)
                axisMinimum = 0f
                textColor = ContextCompat.getColor(context, android.R.color.white)
                mAxisMaximum = 60f
            }
            axisRight.isEnabled = false
            animateX(2000)
        }
    }

    private fun addDataToChart(
        entries: ArrayList<Entry>,
        lineChart: LineChart,
        title: String,
        lineColor: Int,
        chartFillColor: Int
    ) {

        val max = entries.maxOf { it.y } + 10f

        lineChart.axisLeft.apply {
            textColor =
                ContextCompat.getColor(requireContext(), android.R.color.white)
            enableAxisLineDashedLine(10f, 10f, 0f)
            axisMaximum = max
        }
        lineChart.setVisibleYRange(max, max, lineChart.axisLeft.axisDependency)

        val set: LineDataSet

        if (lineChart.data != null && lineChart.data.dataSetCount > 0) {
            set = lineChart.data.getDataSetByIndex(0) as LineDataSet
            set.values = entries
            set.notifyDataSetChanged()
            lineChart.data.notifyDataChanged()
            lineChart.notifyDataSetChanged()
        } else {
            set = LineDataSet(entries, title)
            with(set) {
                setDrawIcons(false)
                color = lineColor
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(false)
                setDrawFilled(true)
                fillColor = chartFillColor
                fillFormatter =
                    IFillFormatter { _, _ -> lineChart.axisLeft.mAxisMinimum }

            }
            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(set)
            val data = LineData(dataSets)
            lineChart.data = data
        }
    }

    // endregion

    override fun noInternetConnection() {
        requireActivity().runOnUiThread {
            binding.noInternetTextView?.visibility = View.VISIBLE
        }

    }

    override fun connectedToInternet() {
        requireActivity().runOnUiThread {
            binding.noInternetTextView?.visibility = View.INVISIBLE
        }
    }
}