package com.sziffer.challenger.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.model.MyLocation


class MapboxActivity : AppCompatActivity() {

    private var mapView: MapView? = null
    private lateinit var points: ArrayList<Point>

    private var mapBox: MapboxMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        setContentView(R.layout.activity_mapbox)

        val dbHelper = ChallengeDbHelper(this)
        val challenges = dbHelper.getAllChallenges()
        val challenge = challenges.random()
        dbHelper.close()

        val typeJson = object : TypeToken<java.util.ArrayList<MyLocation>>() {}.type
        val route =
            Gson().fromJson<java.util.ArrayList<MyLocation>>(challenge.routeAsString, typeJson)

        points = ArrayList()

        val latLngBoundsBuilder = LatLngBounds.Builder()

        route.forEach {
            latLngBoundsBuilder.include(
                LatLng(
                    it.latLng.latitude,
                    it.latLng.longitude,
                    it.altitude
                )
            )
        }

        points = route.map {
            Point.fromLngLat(
                it.latLng.longitude,
                it.latLng.latitude,
                it.altitude
            )
        } as ArrayList<Point>


        Log.d("MAPBOX_ACTIVITY", points.size.toString())


        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { mapBox ->

            mapBox.setStyle(Style.OUTDOORS) {
                val lineString: LineString = LineString.fromLngLats(points)
                val feature = Feature.fromGeometry(lineString)
                val geoJsonSource = GeoJsonSource("geojson-source", feature)
                it.addSource(geoJsonSource)
                it.addLayer(
                    LineLayer("linelayer", "geojson-source").withProperties(
                        PropertyFactory.lineCap(Property.LINE_CAP_SQUARE),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_MITER),
                        PropertyFactory.lineOpacity(1f),
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineColor(
                            resources.getColor(
                                R.color.colorAccent,
                                null
                            )
                        )
                    )
                )
                mapBox.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        latLngBoundsBuilder.build(),
                        100
                    ), 5000
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }


//    private fun createStyle() = style(Style.OUTDOORS) {
//        +geoJsonSource("id") {
//            feature(Feature.fromGeometry(LineString.fromLngLats(points)))
//            lineMetrics(true)
//        }
//        +lineLayer("layer-id","id") {
//            lineCap(LineCap.SQUARE)
//            lineJoin(LineJoin.MITER)
//            lineOpacity(0.7)
//            lineWidth(4.0)
//            lineColor(resources.getColor(R.color.colorPrimaryDark,null))
//        }
//    }
}