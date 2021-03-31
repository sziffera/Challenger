package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.github.psambit9791.jdsp.filter.Wiener
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.databinding.ActivityChallengeDetailsBinding
import com.sziffer.challenger.model.Challenge
import com.sziffer.challenger.model.MyLocation
import com.sziffer.challenger.sync.KEY_UPLOAD
import com.sziffer.challenger.sync.updateSharedPrefForSync
import com.sziffer.challenger.utils.getStringFromNumber
import com.sziffer.challenger.utils.locationPermissionCheck
import com.sziffer.challenger.utils.locationPermissionRequest
import java.io.*
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.abs


class ChallengeDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var challenge: Challenge
    private var previousChallenge: Challenge? = null
    private lateinit var builder: LatLngBounds.Builder
    private var route: ArrayList<MyLocation>? = null
    private var elevGain = 0.0
    private var elevLoss = 0.0
    private var sharingImageBitmap: Bitmap? = null
    private var update: Boolean = false
    private var isItAChallenge: Boolean = false
    private var avgSpeed: Double = 0.0
    private var maxHr = -1
    private var avgHr = -1
    private var start: Long = 0

    private lateinit var binding: ActivityChallengeDetailsBinding

    //region lifecycle
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChallengeDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = ChallengeDbHelper(this)

        // just for calculating performance
        start = System.currentTimeMillis()

        //id for the challenge from the intent
        val id = intent.getLongExtra(CHALLENGE_ID, 0)
        challenge = dbHelper.getChallenge(id.toInt())!!
        builder = LatLngBounds.builder()

        Log.i("CHALLENGE DETAILS", challenge.toString())


        isItAChallenge = intent.getBooleanExtra(IS_IT_A_CHALLENGE, false).also {
            Log.i(TAG, "$IS_IT_A_CHALLENGE is $it")
        }
        update = intent.getBooleanExtra(UPDATE, false).also {
            Log.i(TAG, "$UPDATE is $it")
        }

        binding.discardButton.setOnClickListener {
            showDiscardAlertDialog()
        }

        //can be null
        val previousChallengeId = intent.getLongExtra(PREVIOUS_CHALLENGE_ID, -1)
        previousChallenge = dbHelper.getChallenge(previousChallengeId.toInt())

        binding.showChartsButton.setOnClickListener {
            if (route == null) {
                //the Gson() conversion has not finished yet.
                Toast.makeText(this, getString(R.string.please_wait), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startActivity(
                Intent(this, ChartsActivity::class.java)
                    .putExtra(ChartsActivity.CHALLENGE_ID, id)
                    .putExtra(ChartsActivity.AVG_SPEED, challenge.avg)
                    .putExtra(ChartsActivity.ELEVATION_GAIN, elevGain)
                    .putExtra(ChartsActivity.ELEVATION_LOSS, elevLoss)
                    .putExtra(ChartsActivity.SHOW_HR, maxHr > 0)
            )
        }

        binding.writeToFileButton?.setOnClickListener {
            //route?.let { it1 -> writeToFile(it1,"saab") }

            val challengeCopy = challenge
            val currentDate: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy. HH:mm")
                current.format(formatter)

            } else {
                val date = Date()
                val formatter = SimpleDateFormat("dd-MM-yyyy. HH:mm")
                formatter.format(date)
            }
            challengeCopy.date = currentDate
            challengeCopy.firebaseId = UUID.randomUUID().toString()

            updateSharedPrefForSync(applicationContext, challengeCopy.firebaseId, KEY_UPLOAD)

            dbHelper.addChallenge(challengeCopy)


        }

        when {
            //the user chose a Challenge to do it better, and wants to start recording
            isItAChallenge -> {
                binding.discardButton.visibility = View.GONE
                binding.buttonDivSpace.visibility = View.GONE
                binding.saveChallengeInDetailsButton.text =
                    getString(R.string.challenge_this_activity)
                binding.saveChallengeInDetailsButton.setOnClickListener {
                    if (locationPermissionCheck(this))
                        startChallenge()
                    else
                        locationPermissionRequest(this, this)
                }
                binding.challengeDetailsNameEditText.inputType = InputType.TYPE_NULL
                binding.challengeDetailsNameEditText.setText(challenge.name.toUpperCase(Locale.ROOT))

            }
            //the user finished recording a challenged activity, update data with new values
            update -> {
                binding.saveChallengeInDetailsButton.text = getString(R.string.update_challenge)
                binding.challengeDetailsNameEditText.inputType = InputType.TYPE_NULL
                binding.challengeDetailsNameEditText.setText(
                    previousChallenge?.name?.toUpperCase(
                        Locale.ROOT
                    )
                )

                binding.saveChallengeInDetailsButton.setOnClickListener {
                    updateChallenge()
                }
            }
            //this is just a normal recorded challenge
            else -> {
                binding.saveChallengeInDetailsButton.setOnClickListener {
                    saveChallenge()
                }
            }
        }
        initVariables()

        //solving the Google Maps touch error caused by ScrollView
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

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.challengeDetailsMap) as SupportMapFragment
        mapFragment.getMapAsync(this)


        binding.shareChallengeButton.setOnClickListener {
            initShareImage()
        }


    }

    //endregion lifecycle

    //region helper methods

    override fun onMapReady(p0: GoogleMap) {

        Log.i(TAG, "onMapReady ${System.currentTimeMillis() - start}ms")
        mMap = p0
        runProcessThread()
    }

    private fun startChallenge() {

        val intent = Intent(this, ChallengeRecorderActivity::class.java)
        intent.putExtra(ChallengeRecorderActivity.CHALLENGE, true)
        intent.putExtra(ChallengeRecorderActivity.RECORDED_CHALLENGE_ID, challenge.id.toInt())
        dbHelper.close()
        startActivity(intent)
    }

    /**
     * updating the previous challenge
     * this is just a normal saving, but with the previous challenge's name.
     * In this way, the previous challenge's data is not lost, later I the user
     * can see the improvement. I think this is a better approach, than overriding data.
     */
    private fun updateChallenge() {
        //we have a saved new challenge, and the previous one
        if (previousChallenge != null) {

            challenge.name = previousChallenge!!.name
            dbHelper.updateChallenge(challenge.id.toInt(), challenge)
            updateSharedPrefForSync(applicationContext, challenge.firebaseId, KEY_UPLOAD)

            Toast.makeText(
                this, getString(R.string.save_ok), Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, getString(R.string.cant_save), Toast.LENGTH_LONG).show()
        }
        startMainActivity()
    }

    private fun saveChallenge() {

        if (binding.challengeDetailsNameEditText.text.isEmpty()) {
            binding.challengeDetailsNameEditText.error = getString(R.string.please_name_challenge)
            return
        }
        challenge.name = binding.challengeDetailsNameEditText.text.toString()

        dbHelper.updateChallenge(challenge.id.toInt(), challenge)

        updateSharedPrefForSync(applicationContext, challenge.firebaseId, KEY_UPLOAD)

        Toast.makeText(this, getString(R.string.save_ok), Toast.LENGTH_SHORT).show()
        startMainActivity()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        dbHelper.close()
        finish()
    }

    @SuppressLint("SetTextI18n")
    private fun initVariables() {


        with(challenge) {

            binding.challengeDetailsDurationTextView.text = DateUtils.formatElapsedTime(dur)
            binding.challengeDetailsAvgSpeedTextView.text = getStringFromNumber(1, avg) + " km/h"
            binding.challengeDetailsDistanceTextView.text = getStringFromNumber(1, dst) + " km"
            if (type == getString(R.string.running)) {
                binding.challengeTypeImageView.setImageResource(R.drawable.running)
            }
            avgSpeed = this.avg
            binding.challengeDetailsMaxSpeedTextView.text = getStringFromNumber(1, mS) + " km/h"
            val avgPace = dur.div(dst)
            binding.avgPaceTextView.text = DateUtils.formatElapsedTime(avgPace.toLong()) + " min/km"
        }
    }

    private fun showDiscardAlertDialog() {

        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialog)
        val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
            findViewById<TextView>(R.id.dialogTitleTextView).text =
                getString(R.string.discard_challenge)
            findViewById<TextView>(R.id.dialogDescriptionTextView).text =
                getString(R.string.are_you_sure_to_discard)
            findViewById<Button>(R.id.dialogOkButton).text = getString(R.string.yes)
            findViewById<Button>(R.id.dialogCancelButton).text = getString(R.string.no)
            findViewById<ImageView>(R.id.dialogImageView).setImageDrawable(
                ContextCompat.getDrawable(
                    this@ChallengeDetailsActivity,
                    R.drawable.delete
                )
            )
        }
        dialogBuilder.setView(layoutView)
        val alertDialog = dialogBuilder.create().apply {
            window?.setGravity(Gravity.BOTTOM)
            window?.attributes?.windowAnimations = R.style.DialogAnimation
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }

        layoutView.findViewById<Button>(R.id.dialogCancelButton).setOnClickListener {
            alertDialog.dismiss()
        }
        layoutView.findViewById<Button>(R.id.dialogOkButton).setOnClickListener {
            discardChallenge()
            alertDialog.dismiss()
        }
    }

    /** removes the temp. stored challenge from DB when the user presses discard button */
    private fun discardChallenge() {
        dbHelper.deleteChallenge(challenge.id).also {
            Log.i(TAG, "delete success is: $it")
        }
        startMainActivity()
    }

    //endregion helper methods

    //region processing

    /**
     * Starts a thread that converts the string data to MyLocation array and
     * calculates the LatLngBound for zooming on Map
     * and calculates the elevation data
     **/
    private fun runProcessThread() {
        object : Thread() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
                route = Gson().fromJson<ArrayList<MyLocation>>(challenge.routeAsString, typeJson)
                val polylineOptions = PolylineOptions()
                val elevationArray = DoubleArray(route!!.size)
                var hrSum = 0
                var hr = false
                if (route?.get(0)?.hr == -1) {
                    for ((index, i) in route!!.withIndex()) {

                        builder.include(i.latLng)
                        elevationArray[index] = i.altitude
                        polylineOptions.add(i.latLng)
                    }
                } else {
                    hr = true
                    for ((index, i) in route!!.withIndex()) {
                        hrSum += i.hr
                        if (i.hr > maxHr)
                            maxHr = i.hr
                        builder.include(i.latLng)
                        elevationArray[index] = i.altitude
                        polylineOptions.add(i.latLng)
                    }
                    avgHr = hrSum.div(route!!.size)
                }

                if (elevationArray.size > 100) {
                    val wiener = Wiener(elevationArray, elevationArray.size / 25)
                    val filteredElevation = wiener.filter()
                    for (i in 10..filteredElevation.size - 10) {
                        if (filteredElevation[i] < filteredElevation[i + 1]) {
                            elevGain += abs(filteredElevation[i] - filteredElevation[i + 1])
                        } else {
                            elevLoss += abs(filteredElevation[i] - filteredElevation[i + 1])
                        }
                    }
                }


                runOnUiThread {

                    mMap.addPolyline(
                        polylineOptions.color(
                            ContextCompat.getColor(
                                this@ChallengeDetailsActivity,
                                R.color.colorAccent
                            )
                        )
                    )
                    val padding = 100
                    val cu = CameraUpdateFactory.newLatLngBounds(builder.build(), padding)
                    mMap.animateCamera(cu)
                    binding.elevationGainedTextView.text = getStringFromNumber(0, elevGain) + " m"
                    binding.elevationLostTextView.text = getStringFromNumber(0, elevLoss) + " m"
                    if (hr) {
                        binding.apply {
                            maxHeartRateTextView?.text = "$maxHr bpm"
                            avgHeartRateTextView?.text = "$avgHr bpm"
                        }
                    }
                }

            }
        }.start()
    }

    //endregion processing

    //region sharing

    /** takes a screenshot of map and adds text to image */
    private fun initShareImage() {

        val callback: GoogleMap.SnapshotReadyCallback = GoogleMap.SnapshotReadyCallback {
            val canvas = Canvas(it)
            val scale = resources.displayMetrics.density
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(
                    this@ChallengeDetailsActivity,
                    android.R.color.white
                )
                textSize = 16 * scale
                textAlign = Paint.Align.CENTER
                setShadowLayer(1f, 0f, 1f, Color.DKGRAY)
            }
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(
                    this@ChallengeDetailsActivity,
                    R.color.colorPrimaryDark
                )
            }

            val drawable = ContextCompat.getDrawable(this, R.drawable.sharing_logo)
            drawable?.setBounds(
                (it.width * 0.83).toInt(),
                (it.height * 0.1).toInt(), (it.width * 0.98).toInt(), (it.height * 0.16).toInt()
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

            sharingImageBitmap = it
            shareBitmap(it, challenge.date)

        }
        mMap.snapshot(callback)
    }

    /** Saves bitmap */
    private fun shareBitmap(bitmap: Bitmap, name: String) {

        //get cache directory
        val cachePath = File(externalCacheDir, "challenger_images/")
        cachePath.mkdirs()

        //create png file
        val file = File(cachePath, "$name.png")
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
        val myImageFileUri: Uri =
            FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", file)

        //create a intent
        val intent = Intent(Intent.ACTION_SEND)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, myImageFileUri)
        intent.type = "image/png"
        startActivity(Intent.createChooser(intent, getString(R.string.share_challenge)))
    }

    //endregion sharing

    //region GPX

    private fun createAndSaveGpx(name: String) {

        if (route == null) return

        val header =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"MapSource 6.15.5\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\"><trk>\n"
        val name = """
            <name>${name}</name><trkseg>
            
            """.trimIndent()

        var segments = ""
        val challengeDateFormat = SimpleDateFormat("dd-MM-yyyy. HH:mm")
        val date = challengeDateFormat.parse(challenge.date)
        val gpxDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        for (location in route!!) {

            segments += "<trkpt lat=\"" + location.latLng.latitude + "\" lon=\"" + location.latLng.longitude + "\"><time>" + "</time></trkpt>\n";
        }
        val footer = "</trkseg></trk></gpx>"

    }

    //endregion GPX

    //region for testing
    private fun writeToFile(testArray: ArrayList<MyLocation>, name: String) {
        val outputStreamWriter =
            OutputStreamWriter(openFileOutput("terror.txt", Context.MODE_PRIVATE))

        outputStreamWriter.write("time,speed\n")

        Toast.makeText(this, testArray.size.toString() + " items", Toast.LENGTH_LONG).show()
        for (myLocation in testArray) {
            outputStreamWriter.write("${myLocation.time / 1000};${myLocation.speed}\n")

        }
        outputStreamWriter.flush()
        outputStreamWriter.close()
    }


    private fun contentValues(): ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        return values
    }

    private fun saveImageToStream(bitmap: Bitmap, outputStream: OutputStream?) {
        if (outputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    //endregion for testing

    companion object {
        private val TAG = this::class.java.simpleName
        private const val REQUEST = 112
        private const val CHALLENGE_DETAILS = "challengeDetails"
        const val CHALLENGE_ID = "$CHALLENGE_DETAILS.id"
        const val IS_IT_A_CHALLENGE = "$CHALLENGE_DETAILS.isChallenge"
        const val UPDATE = "$CHALLENGE_DETAILS.update"
        const val PREVIOUS_CHALLENGE_ID = "$CHALLENGE_DETAILS.previousChallengeId"
    }
}