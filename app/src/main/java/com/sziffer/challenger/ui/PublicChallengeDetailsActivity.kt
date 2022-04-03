package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.gms.maps.model.LatLng
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.sziffer.challenger.R
import com.sziffer.challenger.State
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityPublicChallengeDetailsBinding
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.model.challenge.RecordingType
import com.sziffer.challenger.utils.getStringFromNumber
import com.sziffer.challenger.viewmodels.PublicChallengeDetailsViewModel
import com.sziffer.challenger.viewmodels.PublicChallengeDetailsViewModelFactory

class PublicChallengeDetailsActivity : AppCompatActivity() {


    private var style: Style? = null
    private var isRouteAdded = false

    private lateinit var binding: ActivityPublicChallengeDetailsBinding
    private lateinit var viewModel: PublicChallengeDetailsViewModel

    // todo: this should be moved to the viewModel
    private var challenge: PublicChallenge? = null
    private var currentLatLng: LatLng? = null


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPublicChallengeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, PublicChallengeDetailsViewModelFactory()).get(
            PublicChallengeDetailsViewModel::class.java
        )

        binding.transparentImageView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        binding.challengeDetailsScrollView
                            .requestDisallowInterceptTouchEvent(true)
                        return false
                    }
                    MotionEvent.ACTION_UP -> {
                        binding.challengeDetailsScrollView
                            .requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        binding.challengeDetailsScrollView
                            .requestDisallowInterceptTouchEvent(true)
                        return false
                    }
                    else -> return true
                }
            }
        })

        viewModel.challenge.observe(this) { state ->
            when (state) {
                is State.Loading -> loading()
                is State.Success -> challengeFetched(state.data)
                is State.Failed -> shorError(state.message)
            }
        }

        intent.getStringExtra(KEY_CHALLENGE_ID)?.let {
            Log.d(TAG, "The challenge id is $it")
            viewModel.getChallenge(it)
        }


        currentLatLng = intent.getParcelableExtra(KEY_USER_LOCATION) as LatLng?

//        binding.challengeDetailsMap.onCreate(savedInstanceState)
//        binding.challengeDetailsMap.getMapAsync {
//            this.mapBox = it
//            mapBox!!.setStyle(Style.OUTDOORS) { style ->
//                this.style = style
//                if (challenge != null) {
//                    addRouteToMap(style)
//                    enableLocationComponent(style)
//                }
//            }
//        }

        binding.distanceFromUserPosition.text =
            "${intent.getIntExtra(KEY_DISTANCE_FROM_USER, 0)} km"

        binding.startButton.setOnClickListener {
            if (challenge == null) Toast.makeText(
                this,
                getString(R.string.please_wait),
                Toast.LENGTH_SHORT
            ).show()
            else {
                viewModel.insertChallengeToRoom(challenge!!, this)
                val id = challenge!!.id
                startActivity(Intent(this, ChallengeRecorderActivity::class.java).apply {
                    putExtra(
                        ChallengeRecorderActivity.RECORDING_TYPE,
                        RecordingType.PUBLIC_CHALLENGE
                    )
                    putExtra(ChallengeRecorderActivity.RECORDED_CHALLENGE_ID, id)
                })
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

        if (challenge.userId == FirebaseManager.mAuth.currentUser?.uid) {
            // the current user is the 1st on this challenge
            binding.userStateOnChallengeImage.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.winner
                )
            )
            binding.userStateOnChallengeText.text =
                getString(R.string.you_are_the_fastest_on_this_route)
            binding.startButton.text = getString(R.string.i_will_do_it_faster)
        }

        // todo: FIX it
        binding.userStateOnChallengeHolder.visibility = View.VISIBLE
        binding.userStateOnChallengeHolder.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 1000
        })


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
        binding.attemptsDescription.text =
            resources.getQuantityText(R.plurals.attempt, challenge.attempts)

        setUpElevationProfileChart()

        // TODO: the map style should be started loading before the challenge fetch ends
        addRouteToMap()

    }

    private fun shorError(message: String) {
        //todo: implement
        binding.progressBar.visibility = View.GONE
        Log.e(TAG, message)
    }

    private fun setUpElevationProfileChart() {
        initLineCharts(binding.elevationProfileChart, (challenge!!.distance / 1000.0).toFloat())
        val entries =
            challenge!!.route!!.map {
                Entry(
                    (it.distance / 1000F),
                    it.altitude.toFloat()
                )
            } as ArrayList<Entry>
        addDataToChart(
            entries,
            binding.elevationProfileChart,
            getString(R.string.elevation),
            ContextCompat.getColor(this, R.color.colorPlus),
            ContextCompat.getColor(this, R.color.colorMinus)
        )
    }


    private fun initLineCharts(lineChart: LineChart, distance: Float) {
        with(lineChart) {
            isDragEnabled = false
            setPinchZoom(false)
            setTouchEnabled(false)
            extraBottomOffset = -50f
            legend.textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
            description.isEnabled = false
            xAxis.apply {
                //enableAxisLineDashedLine(10f, 10f, 0f)
                axisMinimum = 0f
                textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
                mAxisMaximum = distance
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
                ContextCompat.getColor(this@PublicChallengeDetailsActivity, android.R.color.white)
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

    private fun addRouteToMap() {

        challenge?.route?.let { route ->

            val points = route.map {
                Point.fromLngLat(it.latLng.longitude,it.latLng.latitude)
            }
            val lineString: LineString = LineString.fromLngLats(points)
            val feature = Feature.fromGeometry(lineString)

            binding.challengeDetailsMap.getMapboxMap().apply {
                loadStyle(com.mapbox.maps.extension.style.style(styleUri = Style.OUTDOORS) {
                    +geoJsonSource(id = "geojson-source") {
                        feature(feature)
                    }
                    +lineLayer("linelayer", "geojson-source") {
                        lineCap(LineCap.ROUND)
                        lineJoin(LineJoin.MITER)
                        lineOpacity(1.0)
                        lineWidth(4.0)
                        lineColor(
                            resources.getColor(
                                R.color.colorPrimaryDark,
                                null
                            )
                        )
                    }

                })
                this.addOnStyleLoadedListener {
                    val cameraPosition = cameraForCoordinates(
                        points, EdgeInsets(50.0, 50.0, 50.0, 50.0)
                    )
                    flyTo(cameraPosition, MapAnimationOptions.mapAnimationOptions {
                        duration(5000)
                    })
                }
            }
        }
    }


    companion object {
        private const val TAG = "PublicChallengeDetailsActivity"
        const val KEY_CHALLENGE_ID = "challengeId"
        const val KEY_DISTANCE_FROM_USER = "distanceFromUser"
        const val KEY_USER_LOCATION = "userLocation"
    }


}