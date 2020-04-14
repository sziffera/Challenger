package com.sziffer.challenger

import android.os.Bundle
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
import kotlinx.android.synthetic.main.activity_charts.*

class ChartsActivity : AppCompatActivity() {

    private var challengeData: ArrayList<MyLocation>? = null
    private lateinit var speedData: ArrayList<Entry>
    private lateinit var yAxis: YAxis
    private lateinit var xAxis: XAxis
    private lateinit var limitLine: LimitLine
    private var avgSpeed: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_charts)

        challengeData = intent.getParcelableArrayListExtra(CHALLENGE_DATA_ARRAY)
        avgSpeed = intent.getDoubleExtra(AVG_SPEED, 0.0)

        speedLineChart.isDragEnabled = true
        speedLineChart.setPinchZoom(true)
        speedLineChart.setTouchEnabled(true)
        speedLineChart.extraBottomOffset = -50f
        speedLineChart.legend.textColor = ContextCompat.getColor(this, android.R.color.white)
        speedLineChart.description.isEnabled = false

        limitLine =
            LimitLine(avgSpeed.toFloat(), "Avg speed ${getStringFromNumber(1, avgSpeed)}km/h")
        limitLine.lineColor = ContextCompat.getColor(this, R.color.colorMinus)
        limitLine.lineWidth = 4f
        limitLine.textColor = ContextCompat.getColor(this, android.R.color.white)
        limitLine.textSize = 10f

        xAxis = speedLineChart.xAxis
        xAxis.enableAxisLineDashedLine(10f, 10f, 0f)
        xAxis.axisMinimum = 0f
        xAxis.textColor = ContextCompat.getColor(this, android.R.color.white)
        xAxis.mAxisMaximum = challengeData!![challengeData!!.size - 1].distance

        speedLineChart.axisRight.isEnabled = false

        setSpeedChartData()
        //setAltitudeData()

    }

    private fun setAltitudeData() {
        var max = 0f
        if (challengeData != null) {

            speedData = ArrayList()

            for (i in 0 until challengeData!!.size) {

                if (max < challengeData!![i].altitude)
                    max = challengeData!![i].altitude.toFloat()

                speedData.add(
                    Entry(
                        challengeData!![i].distance,
                        challengeData!![i].altitude.toFloat()
                    )
                )
            }
        }

        yAxis = speedLineChart.axisLeft
        yAxis.textColor = ContextCompat.getColor(this, android.R.color.white)
        yAxis.addLimitLine(limitLine)
        yAxis.enableAxisLineDashedLine(10f, 10f, 0f)
        max += 10
        yAxis.axisMaximum = max
        speedLineChart.setVisibleYRange(max, max, yAxis.axisDependency)

        val set: LineDataSet

        if (speedLineChart.data != null &&
            speedLineChart.data.dataSetCount > 0
        ) {

            set = speedLineChart.data.getDataSetByIndex(0) as LineDataSet
            set.values = speedData
            set.notifyDataSetChanged()
            speedLineChart.data.notifyDataChanged()
            speedLineChart.notifyDataSetChanged()
        } else {

            set = LineDataSet(speedData, "Speed km/h")

            with(set) {
                setDrawIcons(false)
                color = ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(false)
                setDrawFilled(true)
                fillColor = ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                fillFormatter =
                    IFillFormatter { _, _ -> speedLineChart.axisLeft.mAxisMinimum }
            }

            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(set)
            val data = LineData(dataSets)
            speedLineChart.data = data
        }
    }

    private fun setSpeedChartData() {
        var max = 0f
        if (challengeData != null) {

            speedData = ArrayList()

            for (i in 0 until challengeData!!.size) {

                if (max < challengeData!![i].speed)
                    max = challengeData!![i].speed

                speedData.add(
                    Entry(
                        challengeData!![i].distance,
                        challengeData!![i].speed.times(3.6f)
                    )
                )
            }
        }

        yAxis = speedLineChart.axisLeft
        yAxis.textColor = ContextCompat.getColor(this, android.R.color.white)
        yAxis.addLimitLine(limitLine)
        yAxis.enableAxisLineDashedLine(10f, 10f, 0f)
        max = max.times(3.6f) + 10
        yAxis.axisMaximum = max
        speedLineChart.setVisibleYRange(max, max, yAxis.axisDependency)

        val set: LineDataSet

        if (speedLineChart.data != null &&
            speedLineChart.data.dataSetCount > 0
        ) {

            set = speedLineChart.data.getDataSetByIndex(0) as LineDataSet
            set.values = speedData
            set.notifyDataSetChanged()
            speedLineChart.data.notifyDataChanged()
            speedLineChart.notifyDataSetChanged()
        } else {

            set = LineDataSet(speedData, "Speed km/h")

            with(set) {
                setDrawIcons(false)
                color = ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                lineWidth = 2f
                setDrawValues(false)
                setDrawCircles(false)
                setDrawFilled(true)
                fillColor = ContextCompat.getColor(this@ChartsActivity, R.color.colorPlus)
                fillFormatter =
                    IFillFormatter { _, _ -> speedLineChart.axisLeft.mAxisMinimum }
            }

            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(set)
            val data = LineData(dataSets)
            speedLineChart.data = data
        }

    }

    companion object {
        const val AVG_SPEED = "avgSpeed"
        const val CHALLENGE_DATA_ARRAY = "challengeDataArray"
    }
}
