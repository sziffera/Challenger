package com.sziffer.challenger.weather

data class WeatherData(
    val main: Main,
    val wind: Wind,
    val sys: Sys,
    val clouds: Cloud,
    val weather: Array<Weather>
)

data class Weather(
    val description: String,
    val id: Int,
    val main: String
)

data class Cloud(
    val all: Int
)

data class Wind(
    val deg: Int,
    val speed: Double
)

data class Sys(
    val sunset: Long,
    val sunrise: Long
)

data class Main(
    val temp: Double
)

data class UvIndex(
    val value: Double
)