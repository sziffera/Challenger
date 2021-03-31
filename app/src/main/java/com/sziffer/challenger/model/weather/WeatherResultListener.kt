package com.sziffer.challenger.model.weather

interface WeatherResultListener {

    //    fun currentWeatherFetched(currentWeather: WeatherData)
//    fun forecastFetched(forecast: ArrayList<WeatherData>)
//    fun uvFetched(uv: Double)
    fun weatherFetched(oneCallWeather: OneCallWeather)
}