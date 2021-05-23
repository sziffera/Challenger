package com.sziffer.challenger.ui

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.github.psambit9791.jdsp.signal.Smooth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.api.staticmap.v1.MapboxStaticMap
import com.mapbox.api.staticmap.v1.StaticMapCriteria
import com.mapbox.api.staticmap.v1.models.StaticPolylineAnnotation
import com.mapbox.geojson.Point
import com.mapbox.geojson.utils.PolylineUtils
import com.squareup.picasso.Picasso
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.databinding.ActivityShareBinding
import com.sziffer.challenger.model.Challenge
import com.sziffer.challenger.model.MyLocation
import com.sziffer.challenger.utils.*
import java.io.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class ShareActivity : AppCompatActivity(), NetworkStateListener {

    private lateinit var binding: ActivityShareBinding
    private lateinit var challenge: Challenge
    private var sharingImage: Bitmap? = null

    private lateinit var myNetworkCallback: MyNetworkCallback
    private lateinit var connectivityManager: ConnectivityManager
    private var connected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.cancelImageButton.setOnClickListener {
            onBackPressed()
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(
            this, connectivityManager
        )

        val challengeId = intent.getStringExtra(ChallengeDetailsActivity.CHALLENGE_ID)
        val dbHelper = ChallengeDbHelper(this)
        challenge = dbHelper.getChallenge(challengeId!!.toInt())!!
        dbHelper.close()

        supportActionBar?.title = challenge.name.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(
                Locale.ROOT
            ) else it.toString()
        }

        val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
        val challengeData =
            Gson().fromJson<ArrayList<MyLocation>>(challenge.routeAsString, typeJson)

        binding.exportGPXButton.setOnClickListener {

            binding.exportGPXButton.text = getString(R.string.processing)
            binding.exportGPXButton.isEnabled = false
            exportGPX(challengeData)
        }
        val dataSize = 600
        val filterIndex: Double = if (challengeData!!.size < dataSize)
            1.0
        else {
            challengeData.size.toDouble() / dataSize
        }
        val filtered =
            challengeData.filterIndexed { index, _ ->
                (index % filterIndex.toInt()) == 0 || index == challengeData.size - 1
            } as ArrayList<MyLocation>

        val staticImage = MapboxStaticMap.builder()
            .accessToken(MAPBOX_ACCESS_TOKEN)
            .styleId(StaticMapCriteria.OUTDOORS_STYLE)
            .staticPolylineAnnotations(
                listOf(
                    StaticPolylineAnnotation.builder()
                        .polyline(PolylineUtils.encode(filtered.map {
                            Point.fromLngLat(
                                it.latLng.longitude,
                                it.latLng.latitude
                            )
                        }, 5))
                        .strokeColor(60, 147, 180)
                        .strokeWidth(10.0)
                        .build()
                )
            )
            .cameraAuto(true)
            .width(1000) // Image width
            .height(1000) // Image height
            .retina(true) // Retina 2x image will be returned
            .build()

        Executors.newSingleThreadExecutor().execute {
            initShareImage(Picasso.get().load(staticImage.url().toString()).get())
        }

        binding.shareImageButton.setOnClickListener {
            if (sharingImage == null) {
                Toast.makeText(
                    this,
                    getString(R.string.sharing_image_not_loaded),
                    Toast.LENGTH_LONG
                ).show()
            } else
                share(sharingImage!!)
        }

    }


    override fun onStart() {

        if (connectivityManager.allNetworks.isEmpty()) {
            connected = false
            binding.noInternetTextView.visibility = View.VISIBLE
        }

        myNetworkCallback.registerCallback()
        super.onStart()
    }

    override fun onStop() {
        myNetworkCallback.unregisterCallback()
        super.onStop()
    }


    //region sharing

    /** takes a screenshot of map and adds text to image */
    private fun initShareImage(it: Bitmap) {
        val mutableBitmap =
            it.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val scale = resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(
                this@ShareActivity,
                android.R.color.white
            )
            textSize = 22 * scale
            textAlign = Paint.Align.CENTER
            setShadowLayer(1f, 0f, 1f, Color.DKGRAY)
        }
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(
                this@ShareActivity,
                R.color.colorPrimaryDark
            )
            alpha = 160
        }

        val drawable = ContextCompat.getDrawable(this, R.drawable.sharing_logo)

        drawable?.setBounds(
            (it.width * 0.83).toInt(),
            (it.height * 0.1).toInt(), (it.width * 0.98).toInt(), (it.height * 0.18).toInt()
        )

        drawable?.draw(canvas)

        canvas.drawRect(
            0f, 0f, it.width.toFloat(),
            it.height * 0.07f, background
        )

        canvas.drawText("CHALLENGER", it.width * 0.5f, it.height * 0.05f, textPaint)

        canvas.drawRect(
            0f, it.height * 0.93f, it.width.toFloat(),
            it.height.toFloat(), background
        )
        canvas.drawText(
            DateUtils.formatElapsedTime(challenge.dur),
            0.5f * it.width, 0.98f * it.height, textPaint
        )
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            "${getStringFromNumber(1, challenge.dst)} km",
            (0.05f * it.width), it.height.toFloat() * 0.98f, textPaint
        )
        textPaint.textAlign = Paint.Align.RIGHT

        //if it is running, the user needs avg pace not speed
        if (challenge.type == getString(R.string.running)) {
            val avgPace = challenge.dur.div(challenge.dst)
            canvas.drawText(
                "${DateUtils.formatElapsedTime(avgPace.toLong())} /km",
                (0.95f * it.width), it.height.toFloat() * 0.98f, textPaint
            )
        } else {
            canvas.drawText(
                "${getStringFromNumber(1, challenge.avg)} km/h",
                (0.95f * it.width), it.height.toFloat() * 0.98f, textPaint
            )
        }
        sharingImage = mutableBitmap
        runOnUiThread {
            binding.sharingImageView.setImageBitmap(mutableBitmap)
        }
    }

    //endregion sharing


    private fun exportGPX(challengeData: ArrayList<MyLocation>) {

        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {


            val elevationArray = challengeData.map { it.altitude }.toDoubleArray()

            var windowSize = elevationArray.size.div(Constants.WINDOW_SIZE_HELPER)
            if (windowSize > Constants.MAX_WINDOW_SIZE)
                windowSize = Constants.MAX_WINDOW_SIZE
            Log.d("ELEVATION", "the calculated window size is: $windowSize")
            val s1 = Smooth(elevationArray, windowSize, Constants.SMOOTH_MODE)
            val filteredElevation = s1.smoothSignal()

            handler.post {
                binding.progressBar.apply {
                    max = filteredElevation.size
                    progress = 0
                    visibility = View.VISIBLE
                }
            }

            var segments = ""
            val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")

            val lastDuration = challengeData.last().time
            val endDate = Constants.challengeDateFormat.parse(challenge.date)
            val metadata = """<metadata><time>${df.format(endDate!!)}</time></metadata>"""
            val baseInfo = """<trk>
                <name>${challenge.name}</name>
                <type>9</type>
                <trkseg>"""

            if (challengeData.first().hr == -1) {
                for (i in filteredElevation.indices) {
                    segments += """<trkpt lat="${
                        challengeData[i].latLng.latitude
                    }" lon="${challengeData[i].latLng.longitude}"><time>${
                        df.format(
                            Date(
                                endDate.time - (lastDuration - challengeData[i].time)
                            )
                        )
                    }</time>
            <ele>${filteredElevation[i]}</ele>
            </trkpt>"""
                    handler.post {
                        binding.progressBar.progress = i
                    }
                }
            } else {
                for (i in filteredElevation.indices) {
                    segments += """<trkpt lat="${
                        challengeData[i].latLng.latitude
                    }" lon="${challengeData[i].latLng.longitude}"><time>${
                        df.format(
                            Date(
                                endDate.time - (lastDuration - challengeData[i].time)
                            )
                        )
                    }</time>
            <ele>${filteredElevation[i]}</ele>
            <extensions>
             <gpxtpx:TrackPointExtension>
              <gpxtpx:hr>${challengeData[i].hr}</gpxtpx:hr>
             </gpxtpx:TrackPointExtension>
            </extensions>
            </trkpt>"""
                    handler.post {
                        binding.progressBar.progress = i
                    }
                }
            }
            val footer = "</trkseg></trk></gpx>"

            val cachePath = File(externalCacheDir, "challenger_images/")
            cachePath.mkdirs()

            //create png file
            val re = Regex("[^A-Za-z0-9]")
            val fileName = re.replace(challenge.name, "_").also {
                Log.d("GPX", it)
            }
            val file = File(cachePath, "$fileName.gpx")

            try {
                file.printWriter().apply {
                    appendLine(GPX_HEADER)
                    appendLine(metadata)
                    appendLine(baseInfo)
                    appendLine(segments)
                    appendLine(footer)
                    flush()
                    close()
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val gpxUri =
                FileProvider.getUriForFile(
                    this,
                    applicationContext.packageName + ".provider", file
                )


            handler.post {
                binding.progressBar.visibility = View.GONE
                binding.exportGPXButton.apply {
                    isEnabled = true
                    text = getString(R.string.export_as_gpx)
                }
                shareGpx(gpxUri)
            }
        }

    }

    private fun shareGpx(fileUri: Uri) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_STREAM, fileUri)
        intent.type = "text/xml"
        startActivity(Intent.createChooser(intent, getString(R.string.share_challenge)))
    }


    private fun share(bitmap: Bitmap) {

        //get cache directory
        val cachePath = File(externalCacheDir, "challenger_images/")
        cachePath.mkdirs()

        //create png file
        var fileName = challenge.name + challenge.date
        val re = Regex("[^A-Za-z0-9]")
        fileName = re.replace(fileName, "_").also {
            Log.d("GPX", it)
        }
        val file = File(cachePath, "$fileName.png")

        val fileOutputStream: FileOutputStream
        try {
            fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        //---Share File---//
        //get file uri
        val sharingImageUri =
            FileProvider.getUriForFile(
                this,
                applicationContext.packageName + ".provider", file
            )

        shareBitmap(sharingImageUri)

    }


    private fun shareBitmap(fileUri: Uri) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, fileUri)
        intent.type = "image/png"
        startActivity(Intent.createChooser(intent, getString(R.string.share_challenge)))
    }

    override fun noInternetConnection() {
        runOnUiThread {
            binding.noInternetTextView.visibility = View.VISIBLE
        }
    }

    override fun connectedToInternet() {
        runOnUiThread {
            binding.noInternetTextView.visibility = View.INVISIBLE
        }
    }
}