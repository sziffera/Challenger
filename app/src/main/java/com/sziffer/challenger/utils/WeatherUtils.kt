package com.sziffer.challenger.utils

import android.annotation.SuppressLint
import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sziffer.challenger.R

fun setWeatherIcon(id: Int, weatherImageView: ImageView, context: Context, background: Boolean) {
    var imageName: String = when (id) {
        in 200..221 -> {
            "cloud_storm_rain" //ok
        }
        300, 301, 310, 500, 501 -> {
            "cloud_drizzle" //ok
        }
        302, in 311..312, in 502..531 -> {
            "cloud_rain_heavy" //ok
        }
        in 600..602, in 620..622 -> {
            "cloud_snow" //ok
        }
        in 611..616 -> {
            "cloud_drizzle_snow"
        }
        711 -> {
            "raindrops"
        }
        701, in 721..771 -> {
            "cloud_drizzle_snow"
        }
        781 -> {
            "wind_tornado"
        }
        800 -> {
            "sun"
        }
        801 -> {
            "sun"
        }
        802, 803 -> {
            "clouds_sun"
        }
        804 -> {
            "cloud"
        }
        else -> {
            "cloud"
        }
    }
    if (background)
        imageName += "_bg"
    val imageId = context.resources.getIdentifier(
        imageName,
        "drawable", context.packageName
    )
    weatherImageView.setImageDrawable(ContextCompat.getDrawable(context, imageId))
}

/** Sets windDirectionImage's color based on wind speed according to Beaufort Scala*/
fun setBeaufortWindColor(windSpeed: Int, imageView: ImageView) {
    when (windSpeed) {
        in 0..49 -> imageView.setColorFilter(android.R.color.white)
        in 50..61 -> imageView.setColorFilter(R.color.colorWindYellow)
        in 62..74 -> imageView.setColorFilter(android.R.color.holo_orange_light)
        in 75..88 -> imageView.setColorFilter(android.R.color.holo_orange_dark)
        in 89..102 -> imageView.setColorFilter(android.R.color.holo_red_light)
        in 103..117 -> imageView.setColorFilter(android.R.color.holo_red_dark)
        else -> imageView.setColorFilter(R.color.colorStop)
    }
}


@SuppressLint("ResourceAsColor")
//api level
fun setUvIndexColor(uvIndex: Double, textView: TextView, context: Context) {
    when (uvIndex) {
        in 0.0..2.9 -> {
            textView.backgroundTintList = context.resources.getColorStateList(
                R.color.colorGreen,
                null
            )
        }
        in 3.0..5.9 -> {
            textView.backgroundTintList =
                context.resources.getColorStateList(R.color.colorWindYellow, null)
        }
        in 6.0..7.9 -> {
            textView.backgroundTintList =
                context.resources.getColorStateList(android.R.color.holo_orange_dark, null)
        }
        in 8.0..10.9 -> {
            textView.backgroundTintList =
                context.resources.getColorStateList(android.R.color.holo_red_dark, null)
        }
        else -> {
            textView.backgroundTintList =
                context.resources.getColorStateList(android.R.color.holo_purple, null)
        }
    }
}