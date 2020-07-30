package com.sziffer.challenger.weather

data class WeatherData(
    val main: Main,
    val wind: Wind,
    val weather: Array<Weather>
)

data class Weather(
    val description: String,
    val id: Int,
    val main: String
)

data class Wind(
    val deg: Int,
    val speed: Double
)

data class Main(
    val temp: Double
)