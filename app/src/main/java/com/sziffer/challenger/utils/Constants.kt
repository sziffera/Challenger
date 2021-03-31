package com.sziffer.challenger.utils

object Constants {

    object HeartRate {
        const val UUID_HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb"
        const val UUID_HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
        const val UUID_BODY_SENSOR_LOCATION = "00002a38-0000-1000-8000-00805f9b34fb"
        const val UUID_HEART_RATE_CONTROL_POINT = "00002a39-0000-1000-8000-00805f9b34fb"

        // Descriptor for enabling notification on HEART_RATE_MEASUREMENT characteristic
        const val UUID_CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

        // Battery Service
        const val UUID_BATTERY_SERVICE = "0000180f-0000-1000-8000-00805f9b34fb"
        const val UUID_BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"

    }

    const val KEY_WEATHER = "Weather"
    const val KEY_WEATHER_DATA = "WeatherData"

}