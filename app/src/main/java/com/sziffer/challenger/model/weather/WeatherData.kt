package com.sziffer.challenger.model.weather


data class OneCallWeather(
    val current: Current,
    val minutely: ArrayList<MinuteData>,
    val hourly: ArrayList<HourlyData>,
    val daily: ArrayList<DailyData>,
    val alerts: ArrayList<AlertData>?,
    var shouldShowUvIndex: Boolean = false //set based on the conditions after weather fetch
)

data class AlertData(
    val sender_name: String,
    val event: String,
    val start: Long,
    val end: Long,
    val description: String,
    val tags: ArrayList<String>
)

data class MinuteData(
    val dt: Long,
    val precipitation: Double
)

data class HourlyData(
    val dt: Long,
    val feels_like: Double,
    val temp: Double,
    val pressure: Double,
    val humidity: Double,
    val uvi: Double,
    val visibility: Int,
    val wind_speed: Double,
    val wind_deg: Double,
    val weather: Array<Weather>,
    val pop: Double //probability of precipitation
)

data class DailyData(
    val temp: Temp,
    val feels_like: FeelsLike,
    val pressure: Double,
    val humidity: Double,
    val wind_deg: Double,
    val wind_speed: Double,
    val weather: Array<Weather>,
    val clouds: Int,
    val pop: Double,
    val uvi: Double
)

data class FeelsLike(
    val day: Double,
    val night: Double
)

data class Temp(
    val day: Double,
    val min: Double,
    val max: Double,
    val night: Double,
    val eve: Double,
    val morn: Double
)

data class Current(
    val sunrise: Long,
    val sunset: Long,
    val temp: Double,
    val feels_like: Double,
    val pressure: Double,
    val humidity: Double,
    val clouds: Int,
    val uvi: Double,
    val visibility: Int,
    val wind_speed: Double,
    val wind_deg: Double,
    val weather: Array<Weather>
)

data class Weather(
    val description: String,
    val id: Int,
    val main: String
)