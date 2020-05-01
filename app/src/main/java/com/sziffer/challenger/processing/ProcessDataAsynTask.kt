package com.sziffer.challenger.processing

import android.graphics.Color
import android.os.AsyncTask
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.sziffer.challenger.MyLocation

//TODO(this package is unused)
class ProcessDataAsyncTask(
    private val listener: DataProcessCompletionListener,
    private val avgSpeed: Double
) : AsyncTask<ArrayList<MyLocation>, String, String>() {


    private lateinit var polylineOptions: PolylineOptions
    private var elevationGained = 0.0
    private var elevationLoss = 0.0
    private lateinit var builder: LatLngBounds.Builder


    override fun onPostExecute(result: String?) {
        listener.completed(polylineOptions, elevationGained, elevationLoss, builder)
        super.onPostExecute(result)
    }

    override fun onPreExecute() {
        super.onPreExecute()
        polylineOptions = PolylineOptions()
        builder = LatLngBounds.builder()
    }

    override fun doInBackground(vararg params: ArrayList<MyLocation>): String {

        val route = params[0]

        for (i in 0..route.size - 2) {
            builder.include(route[i].latLng)
            val speed = (route[i].speed + route[i + 1].speed).times(1.8)
            if (speed >= avgSpeed) {
                polylineOptions
                    .add(route[i].latLng, route[i + 1].latLng)
                    .color(Color.GREEN)

            } else {
                polylineOptions
                    .add(route[i].latLng, route[i + 1].latLng)
                    .color(Color.RED)
            }
        }

        return "ok"
    }
}