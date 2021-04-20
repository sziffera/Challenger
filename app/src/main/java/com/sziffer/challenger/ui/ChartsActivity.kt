package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
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
import com.sziffer.challenger.model.HeartRateZones
import com.sziffer.challenger.model.MyLocation
import com.sziffer.challenger.utils.extensions.round
import com.sziffer.challenger.utils.getStringFromNumber
import java.util.*
import kotlin.collections.ArrayList

class ChartsActivity : AppCompatActivity() {

    private var challengeData: ArrayList<MyLocation>? = null

    private var elevationGain = 0.0
    private var elevationLoss = 0.0

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
        val challenge = ChallengeDbHelper(this).getChallenge(id.toInt())

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = challenge?.name?.capitalize(Locale.ROOT)

        challengeData = Gson().fromJson<ArrayList<MyLocation>>(challenge?.routeAsString, typeJson)

        val dataPoints = resources.displayMetrics.widthPixels
        val filterIndex: Double = if (challengeData!!.size < dataPoints)
            1.0
        else {
            challengeData!!.size.toDouble() / dataPoints
        }

        challengeData =
            challengeData!!.filterIndexed { index, _ ->
                (index % filterIndex.toInt()) == 0 || index == challengeData!!.size - 1
            }
                    as ArrayList<MyLocation>

        Log.d("CHARTS", "the size of reduced challengeData: ${challengeData!!.size}")

        val showHr = intent.getBooleanExtra(SHOW_HR, false)

        if (!showHr) {
            binding.heartRateLineChart.visibility = View.GONE
            binding.hrChartTitle.visibility = View.GONE
            binding.hrPieChartTitle.visibility = View.GONE
            binding.heartRatePieChart.visibility = View.GONE
        }

        avgSpeed = intent.getDoubleExtra(AVG_SPEED, 0.0)

        elevationGain = intent.getDoubleExtra(ELEVATION_GAIN, 0.0)
        elevationLoss = intent.getDoubleExtra(ELEVATION_LOSS, 0.0)


        heartRateZones = intent.getParcelableExtra(HEART_RATE_ZONES)


        binding.shareImageButton.setOnClickListener {
            startActivity(
                Intent(
                    this, ShareActivity::class.java
                )
            )
        }

        binding.elevationGainedTextView.text = "${getString(R.string.elevation_gained)}:" +
                " ${getStringFromNumber(0, elevationGain)} m"
        binding.elevationLossTextView.text = "${getString(R.string.elevation_lost)}: " +
                "${getStringFromNumber(0, elevationLoss)} m"

        initLineCharts(binding.speedLineChart)
        initLineCharts(binding.heartRateLineChart)
        initLineCharts(binding.elevationLineChart)

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
            setHeartRateZonesData()
        }
        setUpLineCharts(showHr)

    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        challengeData = null
    }

    private fun initLineCharts(lineChart: LineChart) {
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
                mAxisMaximum = challengeData!![challengeData!!.size - 1].distance
            }
            axisRight.isEnabled = false
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


    private fun setHeartRateZonesData() {

        if (challengeData == null)
            return

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
                    return value.round(0).toString()
                }
            }
            setDrawValues(false)
        }

        val pieData = PieData(dataSet)
        binding.heartRatePieChart.apply {
            invalidate()
            setUsePercentValues(true)
            this.data = pieData
            description.text = getString(R.string.heart_rate_zones)
            description.textColor = Color.WHITE
            legend.isWordWrapEnabled = true
            this.data.setDrawValues(false)
            setDrawEntryLabels(false)
            setDrawSliceText(false)
            holeRadius = 0f
            transparentCircleRadius = 0f
            legend.textColor = Color.WHITE
            setEntryLabelColor(Color.WHITE)
            setTouchEnabled(true)
        }

    }


    private fun setUpLineCharts(hr: Boolean) {

        val elevationData = challengeData!!.map { it.altitude }.toDoubleArray()
        val mode = "triangular"
        val windowSize = 11
        val s1 = Smooth(elevationData, windowSize, mode)
        val filteredElevation = s1.smoothSignal()

        val elevationEntries = ArrayList<Entry>()
        val speedEntries = ArrayList<Entry>()
        val hrEntries = ArrayList<Entry>()


        for (i in filteredElevation!!.indices) {
            elevationEntries.add(
                Entry(
                    challengeData!![i].distance,
                    filteredElevation[i].toFloat()
                )
            )
            speedEntries.add(
                Entry(
                    challengeData!![i].distance,
                    challengeData!![i].speed.times(3.6f)
                )
            )
            if (hr)
                hrEntries.add(
                    Entry(
                        challengeData!![i].distance,
                        challengeData!![i].hr.toFloat()
                    )
                )
        }

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
        challengeData = null
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
    }
}
