package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.LineChart
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.exceptions.InvalidLatLngBoundsException
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.sziffer.challenger.R
import com.sziffer.challenger.State
import com.sziffer.challenger.databinding.ActivityPublicChallengeDetailsBinding
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.utils.MAPBOX_ACCESS_TOKEN
import com.sziffer.challenger.utils.getStringFromNumber
import com.sziffer.challenger.viewmodels.PublicChallengeDetailsViewModel
import com.sziffer.challenger.viewmodels.PublicChallengeDetailsViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class PublicChallengeDetailsActivity : AppCompatActivity() {

    private var mapBox: MapboxMap? = null
    private var style: Style? = null

    private lateinit var binding: ActivityPublicChallengeDetailsBinding
    private lateinit var viewModel: PublicChallengeDetailsViewModel

    private var challenge: PublicChallenge? = null

    // Coroutine Scope
    private val uiScope = CoroutineScope(Dispatchers.Main)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, MAPBOX_ACCESS_TOKEN)

        binding = ActivityPublicChallengeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, PublicChallengeDetailsViewModelFactory()).get(
            PublicChallengeDetailsViewModel::class.java
        )

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync {
            this.mapBox = it
            mapBox!!.setStyle(Style.OUTDOORS) { style ->
                this.style = style
                if (challenge != null)
                    addRouteToMap(style)
            }
        }

        val challengeId = intent.getStringExtra(KEY_CHALLENGE_ID).also {
            Log.d(TAG, "The challenge id is $it")
        }
        uiScope.launch {
            if (challengeId != null) {
                getChallenge(challengeId)
            }
        }
    }


    private suspend fun getChallenge(id: String) {
        viewModel.getChallenge(id).collect { state ->
            when (state) {
                is State.Loading -> loading()
                is State.Success -> challengeFetched(state.data)
                is State.Failed -> shorError(state.message)
            }
        }
    }

    private fun loading() {
        Log.d(TAG, "loading")
        binding.progressBar.visibility = View.VISIBLE
    }

    @SuppressLint("SetTextI18n")
    private fun challengeFetched(challenge: PublicChallenge) {
        binding.progressBar.visibility = View.GONE
        this.challenge = challenge
        style?.let {
            addRouteToMap(it)
        }

        // setting the details on screen

        val avgSpeed = (challenge.distance / challenge.duration.toDouble()) * 3.6

        binding.challengeTypeImageView.setImageDrawable(
            com.sziffer.challenger.utils.getDrawable(
                challenge.type,
                this
            )
        )
        binding.distance.text = "${getStringFromNumber(1, challenge.distance / 1000.0)}km"
        binding.duration.text = DateUtils.formatElapsedTime(challenge.duration)
        binding.avgSpeed.text = "${getStringFromNumber(1, avgSpeed)}km/h"
        binding.elevationGainedTv.text = "${challenge.elevationGained}m"
        binding.attempts.text = challenge.attempts.toString()

        setUpElevationProfileChart()

    }

    private fun shorError(message: String) {
        //todo: implement
        binding.progressBar.visibility = View.GONE
        Log.e(TAG, message)
    }

    private fun setUpElevationProfileChart() {
        // todo: not implemented
        initLineCharts(binding.elevationProfileChart, (challenge!!.distance / 1000.0).toFloat())
    }


    private fun initLineCharts(lineChart: LineChart, distance: Float) {
        with(lineChart) {
            isDragEnabled = true
            setPinchZoom(true)
            setTouchEnabled(true)
            extraBottomOffset = -50f
            legend.textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
            description.isEnabled = false
            xAxis.apply {
                enableAxisLineDashedLine(10f, 10f, 0f)
                axisMinimum = 0f
                textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
                mAxisMaximum = distance
            }
            axisRight.isEnabled = false
            animateX(2000)
        }
    }


    private fun addRouteToMap(style: Style) {

        val handler = Handler(Looper.myLooper()!!)

        if (challenge == null) return

        Executors.newSingleThreadExecutor().execute {

            val latLngBoundsBuilder = LatLngBounds.Builder()

            latLngBoundsBuilder.includes(challenge!!.route!!.map {
                LatLng(
                    it.latLng.latitude,
                    it.latLng.longitude
                )
            })

            val points: ArrayList<Point> = challenge!!.route!!.map {
                Point.fromLngLat(
                    it.latLng.latitude,
                    it.latLng.longitude
                )
            } as ArrayList<Point>

            handler.post {
                val lineString: LineString = LineString.fromLngLats(points)
                val feature = Feature.fromGeometry(lineString)
                val geoJsonSource = GeoJsonSource("geojson-source", feature)
                val lineLayer = LineLayer("linelayer", "geojson-source").withProperties(
                    PropertyFactory.lineCap(Property.LINE_CAP_SQUARE),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_MITER),
                    PropertyFactory.lineOpacity(1f),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineColor(
                        resources.getColor(
                            R.color.colorPrimaryDark,
                            null
                        )
                    )
                )

                style.addSource(geoJsonSource)
                style.addLayer(lineLayer)

                try {
                    mapBox?.animateCamera(
                        com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngBounds(
                            latLngBoundsBuilder.build(),
                            100
                        ), 2000
                    )
                } catch (e: InvalidLatLngBoundsException) {
                    e.printStackTrace()
                }
            }
        }

    }


    companion object {
        private const val TAG = "PublicChallengeDetailsActivity"
        const val KEY_CHALLENGE_ID = "challengeId"
    }


}