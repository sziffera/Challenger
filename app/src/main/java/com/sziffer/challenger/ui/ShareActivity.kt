package com.sziffer.challenger.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.github.psambit9791.jdsp.signal.Smooth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.databinding.ActivityShareBinding
import com.sziffer.challenger.model.Challenge
import com.sziffer.challenger.model.MyLocation
import com.sziffer.challenger.utils.Constants
import com.sziffer.challenger.utils.GPX_HEADER
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class ShareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareBinding
    private lateinit var challenge: Challenge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val bitmap = BitmapFactory.decodeStream(openFileInput("challenge"))

        val image = intent.getParcelableExtra<Bitmap>(URI)
        binding.sharingImageView.setImageBitmap(
            bitmap
        )

        binding.shareImageButton.setOnClickListener {
            saveSharingBitmap(bitmap)
        }

        binding.cancelImageButton.setOnClickListener {
            onBackPressed()
        }

        val challengeId = intent.getStringExtra(ChallengeDetailsActivity.CHALLENGE_ID)
        val dbHelper = ChallengeDbHelper(this)
        challenge = dbHelper.getChallenge(challengeId!!.toInt())!!
        dbHelper.close()

        supportActionBar?.title = challenge.name.capitalize(Locale.ROOT)

        binding.exportGPXButton.setOnClickListener {

            binding.exportGPXButton.text = getString(R.string.processing)
            binding.exportGPXButton.isEnabled = false
            exportGPX()
        }

    }


    private fun exportGPX() {

        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
            val challengeData =
                Gson().fromJson<ArrayList<MyLocation>>(challenge.routeAsString, typeJson)

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


    private fun saveSharingBitmap(bitmap: Bitmap) {

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

    companion object {
        const val URI = "uri"
    }
}