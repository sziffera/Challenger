package com.sziffer.challenger.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sziffer.challenger.R
import com.sziffer.challenger.model.weather.HourlyData
import com.sziffer.challenger.utils.extensions.popToPercent
import com.sziffer.challenger.utils.getStringFromNumber
import com.sziffer.challenger.utils.setBeaufortWindColor
import com.sziffer.challenger.utils.setWeatherIcon
import java.text.SimpleDateFormat
import java.util.*


class WeatherForecastRecyclerViewAdapter(
    private val weatherData: ArrayList<HourlyData>,
    private val context: Context
) :
    RecyclerView.Adapter<WeatherForecastRecyclerViewAdapter.ViewHolder>() {

    class ViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        val windImageView: ImageView = itemView.findViewById(R.id.forecastWindDirectionImage)
        val degrees: TextView = itemView.findViewById(R.id.forecastDegreesTextView)
        val windSpeed: TextView = itemView.findViewById(R.id.forecastWindSpeedTextView)
        val date: TextView = itemView.findViewById(R.id.forecastDateTextView)
        val weatherImageView: ImageView = itemView.findViewById(R.id.weatherImageView)
        val precipitationTextView: TextView = itemView.findViewById(R.id.chanceOfRainTextView)
        //val description: TextView = itemView.findViewById(R.id.forecastWeatherDescriptionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val itemView: View = LayoutInflater.from(context).inflate(
            R.layout.weather_item,
            parent, false
        )
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val weatherDataItem = weatherData[position]
        with(holder) {
            degrees.text = "${getStringFromNumber(0, weatherDataItem.temp)}°"
            windSpeed.text = "${getStringFromNumber(0, weatherDataItem.wind_speed.times(3.6))} km/h"
            precipitationTextView.text = weatherDataItem.pop.popToPercent()
            //description.text = data.weather[0].description
            val tempTime = weatherDataItem.dt.times(1000)
            val time = Date(tempTime)
            val dateFormat = SimpleDateFormat("HH:mm");
            date.text = dateFormat.format(time)
            windImageView.rotation = (-90f + weatherDataItem.wind_deg).toFloat()
            setBeaufortWindColor(weatherDataItem.wind_speed.times(3.6).toInt(), windImageView)
            setWeatherIcon(weatherDataItem.weather[0].id, weatherImageView, context, false)
        }
    }

    override fun getItemCount(): Int {
        return weatherData.size
    }

}