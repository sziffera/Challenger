package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.OnCameraTrackingChangedListener
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.sziffer.challenger.R
import com.sziffer.challenger.databinding.ActivityMapboxBinding


class MapboxActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMapboxBinding

    private lateinit var mapBox: MapboxMap
    private var style: Style? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        binding = ActivityMapboxBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { mapBox ->
            this.mapBox = mapBox
            this.mapBox.setStyle(Style.OUTDOORS) {
                style = it
                enableLocationComponent(it)
            }
        }
    }

    @SuppressLint("MissingPermission")//checked
    private fun startLocationTracking() {
        val locationComponent: LocationComponent = mapBox.locationComponent
        // Activate with a built LocationComponentActivationOptions object

        // Activate with a built LocationComponentActivationOptions object
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(
                this,
                style!!
            ).build()
        )
        locationComponent.apply {
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
            addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() {

                }

                override fun onCameraTrackingChanged(currentMode: Int) {

                }

            })
            zoomWhileTracking(15.0, 2000)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(style: Style) {
        val customLocationComponentOptions = LocationComponentOptions.builder(this)
            .trackingGesturesManagement(true)
            .accuracyColor(ContextCompat.getColor(this, R.color.colorGreen))
            .build()

        val locationComponentActivationOptions =
            LocationComponentActivationOptions.builder(this, style)
                .locationComponentOptions(customLocationComponentOptions)
                .build()

        this.mapBox.locationComponent.apply {
            activateLocationComponent(locationComponentActivationOptions)
            isLocationComponentEnabled = true
            cameraMode = CameraMode.TRACKING
            zoomWhileTracking(14.0)
            renderMode = RenderMode.COMPASS
        }
        startLocationTracking()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
}