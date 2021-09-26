package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.psambit9791.jdsp.signal.Smooth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.databinding.ActivityChartsBinding
import com.sziffer.challenger.model.challenge.MyLocation
import com.sziffer.challenger.model.heartrate.HeartRateZones
import com.sziffer.challenger.utils.extensions.dp
import com.sziffer.challenger.utils.extensions.toPace
import com.sziffer.challenger.utils.getStringFromNumber
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList


class ChartsActivity : AppCompatActivity() {

    private var elevationGain = 0.0
    private var elevationLoss = 0.0

    private var maxHr = 0
    private var avgHr = 0

    private var isCycling = false

    private var avgSpeed: Double = 0.0

    private var heartRateZones: HeartRateZones? = null

    private lateinit var binding: ActivityChartsBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChartsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getLongExtra(CHALLENGE_ID, 0)
        val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
        val dbHelper = ChallengeDbHelper(this)
        val challenge = dbHelper.getChallenge(id.toInt())
        dbHelper.close()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = challenge?.name?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(
                Locale.ROOT
            ) else it.toString()
        }

        isCycling = challenge?.type == getString(R.string.cycling)

        var challengeData =
            Gson().fromJson<ArrayList<MyLocation>>(challenge?.routeAsString, typeJson)

        val dataPoints = resources.displayMetrics.widthPixels
        val filterIndex: Double = if (challengeData!!.size < dataPoints)
            1.0
        else {
            challengeData.size.toDouble() / dataPoints
        }
        challengeData =
            challengeData.filterIndexed { index, _ ->
                (index % filterIndex.toInt()) == 0 || index == challengeData!!.size - 1
            } as ArrayList<MyLocation>

        Log.d("CHARTS", "the size of reduced challengeData: ${challengeData.size}")

        val showHr = intent.getBooleanExtra(SHOW_HR, false)

        avgSpeed = intent.getDoubleExtra(AVG_SPEED, 0.0)

        elevationGain = intent.getDoubleExtra(ELEVATION_GAIN, 0.0)
        elevationLoss = intent.getDoubleExtra(ELEVATION_LOSS, 0.0)

        avgHr = intent.getIntExtra(AVG_HR, 0)
        maxHr = intent.getIntExtra(MAX_HR, 0)

        heartRateZones = intent.getParcelableExtra(HEART_RATE_ZONES)

        if (!showHr) {
            binding.heartRateLineChart.visibility = View.GONE
            binding.hrChartTitle.visibility = View.GONE
            binding.hrPieChartTitle.visibility = View.GONE
            binding.heartRatePieChart.visibility = View.GONE
            binding.maxHeartRateTextView.visibility = View.GONE
            binding.avgHeartRateTextView.visibility = View.GONE
        } else {
            binding.apply {
                maxHeartRateTextView.text = "${getString(R.string.max)}: $maxHr BPM"
                avgHeartRateTextView.text = "${getString(R.string.avg)} $avgHr BPM"
            }
        }

        binding.shareImageButton.setOnClickListener {
            startActivity(
                Intent(
                    this, ShareActivity::class.java
                ).apply {
                    putExtra(ChallengeDetailsActivity.CHALLENGE_ID, challenge!!.id)
                }
            )
        }

        binding.elevationGainedTextView.text = "${getString(R.string.elevation_gained)}:" +
                " ${getStringFromNumber(0, elevationGain)} m"
        binding.elevationLossTextView.text = "${getString(R.string.elevation_lost)}: " +
                "${getStringFromNumber(0, elevationLoss)} m"

        val distance = challengeData.last().distance
        initLineCharts(binding.speedLineChart, distance)
        initLineCharts(binding.heartRateLineChart, distance)
        initLineCharts(binding.elevationLineChart, distance)

        val limitLine =
            LimitLine(
                avgSpeed.toFloat(),
                "${getString(R.string.avgspeed)} ${getStringFromNumber(1, avgSpeed)}km/h"
            )
        limitLine.lineColor = ContextCompat.getColor(this, R.color.colorMinus)
        limitLine.lineWidth = 4f
        limitLine.textColor = ContextCompat.getColor(this, android.R.color.white)
        limitLine.textSize = 10f
        binding.speedLineChart.axisLeft.addLimitLine(limitLine)

        if (showHr) {
            setHeartRateZonesData(challengeData)
        }
        setUpLineCharts(showHr, challengeData)
    }


    override fun onBackPressed() {
        super.onBackPressed()
        finish()
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

    private fun share(fileUri: Uri) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, fileUri)
        intent.type = "image/png"
        startActivity(Intent.createChooser(intent, getString(R.string.share_challenge)))
    }

    private fun setHeartRateZonesData(challengeData: ArrayList<MyLocation>) {


        val data = ArrayList<PieEntry>().apply {
            addAll(
                arrayListOf(
                    PieEntry(heartRateZones!!.relaxed.toFloat(), getString(R.string.relaxed)),
                    PieEntry(heartRateZones!!.moderate.toFloat(), getString(R.string.moderate)),
                    PieEntry(
                        heartRateZones!!.weightControl.toFloat(),
                        getString(R.string.weight_control)
                    ),
                    PieEntry(heartRateZones!!.aerobic.toFloat(), getString(R.string.aerobic)),
                    PieEntry(heartRateZones!!.anaerobic.toFloat(), getString(R.string.anaerobic)),
                    PieEntry(heartRateZones!!.vo2Max.toFloat(), getString(R.string.vomax)),
                )
            )
        }

        val colors = arrayListOf(
            resources.getColor(R.color.colorRelaxed, null),
            resources.getColor(R.color.colorModerate, null),
            resources.getColor(R.color.colorWeightControl, null),
            resources.getColor(R.color.colorAerobic, null),
            resources.getColor(R.color.colorAnaerobic, null),
            resources.getColor(R.color.colorVoMax, null),
        )

        val dataSet = PieDataSet(data, "").apply {
            setColors(colors)
            valueFormatter = object : ValueFormatter() {
                override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
                    return if (value > 5) "${value.toInt()}%" else ""
                }
            }
            setDrawValues(false)
        }

        val pieData = PieData(dataSet).apply {
            setValueTextSize(12f)
            setValueTextColor(Color.WHITE)
        }
        binding.heartRatePieChart.apply {
            invalidate()
            setUsePercentValues(true)
            this.data = pieData
            description.text = getString(R.string.heart_rate_zones)
            description.textColor = Color.WHITE
            legend.isWordWrapEnabled = true
            this.data.setDrawValues(true)
            setDrawEntryLabels(false)
            setDrawSliceText(false)
            holeRadius = 0f
            transparentCircleRadius = 0f
            legend.textColor = Color.WHITE
            setEntryLabelColor(Color.WHITE)
            setTouchEnabled(false)
        }
    }

    private fun setUpLineCharts(hr: Boolean, challengeData: ArrayList<MyLocation>) {

        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {


            val elevationData = challengeData.map { it.altitude }.toDoubleArray()
            val mode = "triangular"
            val windowSize = 11
            val s1 = Smooth(elevationData, windowSize, mode)
            val filteredElevation = s1.smoothSignal()

            val elevationEntries = ArrayList<Entry>()
            val speedEntries = ArrayList<Entry>()
            val hrEntries = ArrayList<Entry>()

            var kmCounter = 0.0
            var tempDuration: Long = 0
            var lastSavedMetres: Float = 0f
            val paces = ArrayList<BarEntry>()

            val paceChartLabels = ArrayList<String>(paces.size)

            for (i in filteredElevation!!.indices) {

                if (challengeData[i].distance - lastSavedMetres > 1000) {
                    kmCounter++
                    val duration = (challengeData[i].time - tempDuration) / 1000.0
                    lastSavedMetres = challengeData[i].distance
                    tempDuration = challengeData[i].time
                    paceChartLabels.add("$kmCounter km - ${duration.toPace()}")
                    paces.add(
                        BarEntry(
                            kmCounter.toFloat(),
                            duration.toFloat()
                        )
                    )
                }

                elevationEntries.add(
                    Entry(
                        challengeData[i].distance,
                        filteredElevation[i].toFloat()
                    )
                )
                speedEntries.add(
                    Entry(
                        challengeData[i].distance,
                        challengeData[i].speed.times(3.6f)
                    )
                )
                if (hr)
                    hrEntries.add(
                        Entry(
                            challengeData[i].distance,
                            challengeData[i].hr.toFloat()
                        )
                    )
            }

            handler.post {

                setUpPaceBarChart(paces, paceChartLabels)


                addDataToChart(
                    elevationEntries,
                    binding.elevationLineChart,
                    getString(R.string.altitude_in_metres),
                    ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus),
                    ContextCompat.getColor(this@ChartsActivity, R.color.colorMinus)
                )
                addDataToChart(
                    speedEntries,
                    binding.speedLineChart,
                    getString(R.string.speed) + " km/h",
                    ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus),
                    ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                )
                if (hr) {
                    addDataToChart(
                        hrEntries,
                        binding.heartRateLineChart,
                        getString(R.string.heart_rate),
                        ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus),
                        ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                    )
                }
                //challengeData = null
            }
        }
    }

    private fun setUpPaceBarChart(paces: ArrayList<BarEntry>, paceLabels: ArrayList<String>) {

        binding.paceHorizontalBarChart.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (PACE_ITEM_HEIGHT * paces.size).dp
            )
            setDrawBarShadow(false)
            legend.isEnabled = false
            setPinchZoom(false)

            description.isEnabled = false
            setTouchEnabled(false)
            setDrawValueAboveBar(false)
            setFitBars(true)
            legend.textColor = Color.WHITE
            axisLeft.apply {
                isEnabled = false
                axisMinimum = 0f
            }
            axisRight.apply {
                isEnabled = false
                axisMaximum = 0f
            }

            xAxis.apply {
                setDrawGridLines(false)
                position = XAxis.XAxisPosition.BOTTOM
                isEnabled = true
                textColor = Color.WHITE
                setDrawAxisLine(true)
                setLabelCount(paces.size, true)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()} km"
                    }
                }
            }
        }


        val barDataSet = BarDataSet(paces, getString(R.string.pace)).apply {
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toPace()
//                    if (!isCycling)  else getStringFromNumber(
//                        1,
//                        (1000 / value).times(3.6)
//                    ) + " km/h"
                }
            }
            valueTextSize = 10f
            valueTextColor = resources.getColor(R.color.colorDarkBlue, null)
        }


        val data = BarData(barDataSet).apply {
            barWidth = 0.9f
        }

        binding.paceHorizontalBarChart.animateY(2000)
        binding.paceHorizontalBarChart.data = data
        binding.paceHorizontalBarChart.invalidate()
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
            textColor = ContextCompat.getColor(this@ChartsActivity, android.R.color.white)
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


    companion object {
        const val AVG_SPEED = "avgSpeed"
        const val CHALLENGE_ID = "challengeId"
        const val ELEVATION_GAIN = "elevationGain"
        const val ELEVATION_LOSS = "elevationLoss"
        const val HEART_RATE_ZONES = "hrZones"
        const val SHOW_HR = "showHr"
        const val MAX_SPEED = "maxSpeed"
        const val AVG_HR = "avgHr"
        const val MAX_HR = "maxHr"

        private const val PACE_ITEM_HEIGHT = 18
    }
}
