package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IFillFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.psambit9791.jdsp.filter.Wiener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.databinding.ActivityChartsBinding
import com.sziffer.challenger.model.MyLocation
import com.sziffer.challenger.utils.getStringFromNumber

class ChartsActivity : AppCompatActivity() {

    private var challengeData: ArrayList<MyLocation>? = null
    private var speedData: ArrayList<Entry>? = null
    private var hrData: ArrayList<Entry>? = null
    private var elevationData: ArrayList<Entry>? = null
    private var elevationGain = 0.0
    private var elevationLoss = 0.0
    private lateinit var yAxis: YAxis
    private lateinit var xAxis: XAxis
    private lateinit var yAxisElevation: YAxis
    private lateinit var xAxisElevation: XAxis
    private lateinit var xAxisHr: XAxis
    private lateinit var yAxisHr: YAxis
    private lateinit var limitLine: LimitLine
    private var avgSpeed: Double = 0.0

    private lateinit var binding: ActivityChartsBinding

    //TODO(IMPORTANT - solve memory problem)

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChartsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getLongExtra(CHALLENGE_ID, 0)
        val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
        val challenge = ChallengeDbHelper(this).getChallenge(id.toInt())
        challengeData = Gson().fromJson<ArrayList<MyLocation>>(challenge?.routeAsString, typeJson)

        val showHr = intent.getBooleanExtra(SHOW_HR, false)

        if (!showHr) {
            binding.heartRateLineChart.visibility = View.GONE
            binding.hrChartTitle.visibility = View.GONE
        }

        avgSpeed = intent.getDoubleExtra(AVG_SPEED, 0.0)

        elevationGain = intent.getDoubleExtra(ELEVATION_GAIN, 0.0)
        elevationLoss = intent.getDoubleExtra(ELEVATION_LOSS, 0.0)
        binding.elevationGainedTextView.text = "${getString(R.string.elevation_gained)}:" +
                " ${getStringFromNumber(0, elevationGain)} m"
        binding.elevationLossTextView.text = "${getString(R.string.elevation_lost)}: " +
                "${getStringFromNumber(0, elevationLoss)} m"
        with(binding.speedLineChart) {
            isDragEnabled = true
            setPinchZoom(true)
            setTouchEnabled(true)
            extraBottomOffset = -50f
            legend.textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
            description.isEnabled = false
        }

        with(binding.elevationLineChart) {
            isDragEnabled = true
            setPinchZoom(true)
            setTouchEnabled(true)
            extraBottomOffset = -50f
            legend.textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
            description.isEnabled = false
        }

        with(binding.heartRateLineChart) {
            isDragEnabled = true
            setPinchZoom(true)
            setTouchEnabled(true)
            extraBottomOffset = -50f
            legend.textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
            description.isEnabled = false
        }

        limitLine =
            LimitLine(
                avgSpeed.toFloat(),
                "${getString(R.string.avgspeed)} ${getStringFromNumber(1, avgSpeed)}km/h"
            )
        limitLine.lineColor = ContextCompat.getColor(this, R.color.colorMinus)
        limitLine.lineWidth = 4f
        limitLine.textColor = ContextCompat.getColor(this, android.R.color.white)
        limitLine.textSize = 10f

        xAxis = binding.speedLineChart.xAxis
        xAxis.enableAxisLineDashedLine(10f, 10f, 0f)
        xAxis.axisMinimum = 0f
        xAxis.textColor = ContextCompat.getColor(this, android.R.color.white)
        xAxis.mAxisMaximum = challengeData!![challengeData!!.size - 1].distance

        xAxisElevation = binding.elevationLineChart.xAxis.apply {
            enableAxisLineDashedLine(10f, 10f, 0f)
            axisMinimum = 0f
            textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
            mAxisMaximum = challengeData!![challengeData!!.size - 1].distance
        }

        xAxisHr = binding.heartRateLineChart.xAxis.apply {
            enableAxisLineDashedLine(10f, 10f, 0f)
            axisMinimum = 0f
            textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
            mAxisMaximum = challengeData!![challengeData!!.size - 1].distance
        }

        binding.speedLineChart.axisRight.isEnabled = false
        binding.elevationLineChart.axisRight.isEnabled = false
        binding.heartRateLineChart.axisRight.isEnabled = false

        setSpeedChartData()
        setAltitudeData()
        if (showHr)
            setHeartRateData()
        challengeData = null

    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        challengeData = null
        hrData = null
        elevationData = null
        speedData = null
    }

    private fun setAltitudeData() {
        var max = 0f
        if (challengeData != null) {


            val data = DoubleArray(challengeData!!.size)

            for ((index, i) in challengeData!!.withIndex()) {
                data[index] = i.altitude
            }

            val wiener = Wiener(data, data.size.div(data.size / 25))
            val filtered = wiener.filter()

            elevationData = ArrayList()

            for (i in filtered.indices) {

                if (max < filtered[i])
                    max = filtered[i].toFloat()

                elevationData?.add(
                    Entry(
                        challengeData!![i].distance,
                        filtered[i].toFloat()
                    )
                )
            }
        }

        yAxisElevation = binding.elevationLineChart.axisLeft.apply {
            textColor = ContextCompat.getColor(applicationContext, android.R.color.white)
            enableAxisLineDashedLine(10f, 10f, 0f)
        }

        max += 10
        yAxisElevation.axisMaximum = max
        binding.elevationLineChart.setVisibleYRange(max, max, yAxisElevation.axisDependency)

        val set: LineDataSet

        if (binding.elevationLineChart.data != null &&
            binding.elevationLineChart.data.dataSetCount > 0
        ) {

            set = binding.elevationLineChart.data.getDataSetByIndex(0) as LineDataSet
            set.values = elevationData
            set.notifyDataSetChanged()
            binding.elevationLineChart.data.notifyDataChanged()
            binding.elevationLineChart.notifyDataSetChanged()
        } else {

            set = LineDataSet(elevationData, getString(R.string.altitude_in_metres))

            with(set) {
                setDrawIcons(false)
                color = ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(false)
                setDrawFilled(true)
                fillColor = ContextCompat.getColor(this@ChartsActivity, R.color.colorMinus)
                fillFormatter =
                    IFillFormatter { _, _ -> binding.elevationLineChart.axisLeft.mAxisMinimum }
            }
            elevationData = null
            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(set)
            val data = LineData(dataSets)
            binding.elevationLineChart.data = data
        }
    }

    private fun setSpeedChartData() {
        var max = 0f
        if (challengeData != null) {

            speedData = ArrayList()

            for (i in 0 until challengeData!!.size) {

                if (max < challengeData!![i].speed)
                    max = challengeData!![i].speed

                speedData?.add(
                    Entry(
                        challengeData!![i].distance,
                        challengeData!![i].speed.times(3.6f)
                    )
                )
            }
        }

        yAxis = binding.speedLineChart.axisLeft
        yAxis.textColor = ContextCompat.getColor(this, android.R.color.white)
        yAxis.addLimitLine(limitLine)
        yAxis.enableAxisLineDashedLine(10f, 10f, 0f)
        max = max.times(3.6f) + 10
        yAxis.axisMaximum = max
        binding.speedLineChart.setVisibleYRange(max, max, yAxis.axisDependency)

        val set: LineDataSet

        if (binding.speedLineChart.data != null &&
            binding.speedLineChart.data.dataSetCount > 0
        ) {

            set = binding.speedLineChart.data.getDataSetByIndex(0) as LineDataSet
            set.values = speedData
            set.notifyDataSetChanged()
            binding.speedLineChart.data.notifyDataChanged()
            binding.speedLineChart.notifyDataSetChanged()
        } else {

            set = LineDataSet(speedData, getString(R.string.speed) + " km/h")

            with(set) {
                setDrawIcons(false)
                color = ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(false)
                setDrawFilled(true)
                fillColor = ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                fillFormatter =
                    IFillFormatter { _, _ -> binding.speedLineChart.axisLeft.mAxisMinimum }
            }
            speedData = null
            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(set)
            val data = LineData(dataSets)
            binding.speedLineChart.data = data
        }

    }

    private fun setHeartRateData() {
        var max = 0
        if (challengeData != null) {

            hrData = ArrayList()

            for (i in 0 until challengeData!!.size) {

                if (max < challengeData!![i].hr)
                    max = challengeData!![i].hr

                hrData?.add(
                    Entry(
                        challengeData!![i].distance,
                        challengeData!![i].hr.toFloat()
                    )
                )
            }
        }

        yAxis = binding.heartRateLineChart.axisLeft
        yAxis.textColor = ContextCompat.getColor(this, android.R.color.white)
        yAxis.addLimitLine(limitLine)
        yAxis.enableAxisLineDashedLine(10f, 10f, 0f)
        yAxis.axisMaximum = max.toFloat() + 10
        binding.heartRateLineChart.setVisibleYRange(
            max.toFloat(),
            max.toFloat(),
            yAxis.axisDependency
        )

        val set: LineDataSet

        if (binding.heartRateLineChart.data != null &&
            binding.heartRateLineChart.data.dataSetCount > 0
        ) {

            set = binding.heartRateLineChart.data.getDataSetByIndex(0) as LineDataSet
            set.values = hrData
            set.notifyDataSetChanged()
            binding.heartRateLineChart.data.notifyDataChanged()
            binding.heartRateLineChart.notifyDataSetChanged()
        } else {

            set = LineDataSet(hrData, getString(R.string.heart_rate) + " BPM")

            with(set) {
                setDrawIcons(false)
                color = ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(false)
                setDrawFilled(true)
                fillColor = ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                fillFormatter =
                    IFillFormatter { _, _ -> binding.heartRateLineChart.axisLeft.mAxisMinimum }
            }
            hrData = null
            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(set)
            val data = LineData(dataSets)
            binding.heartRateLineChart.data = data
        }
    }

    companion object {
        const val AVG_SPEED = "avgSpeed"
        const val CHALLENGE_ID = "challengeId"
        const val ELEVATION_GAIN = "elevationGain"
        const val ELEVATION_LOSS = "elevationLoss"
        const val SHOW_HR = "showHr"
    }
}
