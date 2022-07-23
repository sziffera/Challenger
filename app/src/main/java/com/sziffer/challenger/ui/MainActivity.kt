package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.google.android.gms.location.*
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import com.sziffer.challenger.R
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityMainBinding
import com.sziffer.challenger.model.challenge.RecordingType
import com.sziffer.challenger.model.user.UserManager
import com.sziffer.challenger.ui.*
import com.sziffer.challenger.ui.user.UserSettingsActivity
import com.sziffer.challenger.utils.WEATHER_KEY
import com.sziffer.challenger.utils.locationPermissionCheck
import com.sziffer.challenger.utils.locationPermissionRequest
import com.sziffer.challenger.viewmodels.MainViewModel


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userManager: UserManager

    private val viewModel: MainViewModel by viewModels()


    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var locationRequest: LocationRequest? = null
    private var locationCallback: LocationCallback? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userManager = UserManager(this)

        // if the db upgrade is done, but the data not processed yet
//        if (PreferenceManager.getDefaultSharedPreferences(this)
//                .getBoolean(ChallengeDbHelper.KEY_MIGRATION_DONE, false) && !isDatabaseUpgradeDone(
//                this
//            )
//        ) {
//            startActivity(Intent(this, DatabaseUpgradeActivity::class.java))
//            this.finish()
//        }


        viewModel.isTrainingLiveData.observe(this) { isTraining ->
            if (isTraining) {
                binding.recordTextView.text = "Start"
            } else {
                binding.recordTextView.text = getString(R.string.record)
            }
        }

        //setProfilePhoto()

        if (userManager.username == null || userManager.username.equals("null", true)) {
            setUserName()
        }

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)


        //if the recorder activity crashed, restores the bool for recording (important for ui init)
        val buttonSharedPreferences = getSharedPreferences("button", 0)
        buttonSharedPreferences.edit().putBoolean("started", false).apply()

        setSupportActionBar(binding.toolbar)


        supportActionBar?.title = if (userManager.username == null)
            getString(R.string.feed)
        else {
            if (FirebaseManager.mAuth.currentUser?.email == "juditbiliczki@gmail.com")
                "Hajrá Cukika!"
            else getString(R.string.feed)
        }

        binding.settingsImageButton.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    UserSettingsActivity::class.java
                )
            )
        }

        binding.mapImageButton.setOnClickListener {
            startActivity(
                Intent(
                    this, MapboxActivity::class.java
                )
            )
        }


        binding.recordHolder.setOnClickListener {
            if (viewModel.isTrainingLiveData.value == true) {

                if (viewModel.avgSpeed.value == 0.0) {
                    supportFragmentManager.setFragmentResult(
                        CreateFragment.KEY_CHALLENGE_START,
                        Bundle.EMPTY
                    )
                    return@setOnClickListener
                }

                val startRecordingIntent =
                    Intent(this, ChallengeRecorderActivity::class.java).apply {
                        putExtra(ChallengeRecorderActivity.RECORDING_TYPE, RecordingType.TRAINING)
                        putExtra(ChallengeRecorderActivity.AVG_SPEED, viewModel.avgSpeed.value)
                        putExtra(ChallengeRecorderActivity.DISTANCE, viewModel.distance.value)
                        putExtra(
                            ChallengeRecorderActivity.SHOW_UV_ALERT,
                            viewModel.shouldShowUvAlert
                        )
                        putExtra(
                            ChallengeRecorderActivity.SHOW_WIND_ALERT,
                            viewModel.shouldShowWindAlert
                        )
                    }
                startActivity(startRecordingIntent)
            } else {
                startActivity(
                    Intent(
                        this,
                        ChallengeRecorderActivity::class.java
                    ).apply {
                        putExtra(
                            ChallengeRecorderActivity.SHOW_UV_ALERT,
                            viewModel.shouldShowUvAlert
                        )
                        putExtra(
                            ChallengeRecorderActivity.SHOW_WIND_ALERT,
                            viewModel.shouldShowWindAlert
                        )
                        putExtra(
                            ChallengeRecorderActivity.RECORDING_TYPE,
                            RecordingType.NORMAL_RECORDING
                        )
                    }
                )
            }
        }

        setUpBottomNavBar()
    }


    override fun onStart() {
        super.onStart()

        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps()
            return
        }

        if (!locationPermissionCheck(this)) {
            locationPermissionRequest(this, this)
        } else {
            checkLastLocation()
        }

    }

    private fun setProfilePhoto() {
        FirebaseManager.mAuth.currentUser?.photoUrl?.let {
            Picasso.get().load(it).into(binding.profileImageView)
            return
        }
        binding.profileImageView.visibility = View.GONE
    }


    private fun buildAlertMessageNoGps() {

        //TODO: add don't show again

        Log.d("UTILS", "Dialog called")
        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialog)
        val layoutView = layoutInflater.inflate(R.layout.alert_dialog_base, null).apply {
            findViewById<TextView>(R.id.dialogTitleTextView).text = getString(R.string.no_gps)
            findViewById<TextView>(R.id.dialogDescriptionTextView).text =
                getString(R.string.no_gps_text_weather)
            findViewById<ImageView>(R.id.dialogImageView).setImageDrawable(
                ContextCompat.getDrawable(
                    this@MainActivity,
                    R.drawable.location_off
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
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            alertDialog.dismiss()
        }
    }

    //region weather
    //TODO(check gps enabled)
    @SuppressLint("MissingPermission") //already checked
    private fun checkLastLocation() {
        fusedLocationProviderClient.lastLocation.addOnCompleteListener {
            val location = it.result
            if (location != null) {
                viewModel.fetchWeatherData(location, this)
            } else {
                requestLocation()
            }
        }
    }

    private fun fetchWeatherData(location: Location) {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback!!)
        viewModel.fetchWeatherData(location, this)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            interval = 1000
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                p0.lastLocation?.let { fetchWeatherData(it) }
            }
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest!!, locationCallback!!, Looper.getMainLooper()
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("LOCATION", "permissions granted")
        if (grantResults.isNotEmpty()) {
            checkLastLocation()
            Log.d("LOCATION", "permissions granted")
        }
    }

    //endregion weather

    private fun setUserName() {

        if (!FirebaseManager.isUserValid)
            return

        FirebaseManager.mAuth.currentUser?.displayName?.let {
            userManager.username = it
            supportActionBar?.title = "${getString(R.string.hey)}, ${userManager.username}!"
            return
        }

        FirebaseManager.currentUserRef?.child("username")
            ?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val update =
                        UserProfileChangeRequest.Builder().setDisplayName(snapshot.value.toString())
                    FirebaseManager.mAuth.currentUser?.updateProfile(update.build())
                        ?.addOnCompleteListener {
                            if (it.isSuccessful)
                                Log.d("UPDATE", "ok")
                            else
                                Log.d("UPDATE", "not ok")
                        }
                    userManager.username = snapshot.value.toString()
                    this@MainActivity.supportActionBar?.title =
                        "${getString(R.string.hey)}, ${userManager.username}!"
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.d("UPDATE", error.message)
                }

            })
    }

    private fun setUpBottomNavBar() {
        binding.navView.setOnItemSelectedListener { item ->

            viewModel.setIsTraining(item.itemId == R.id.navigation_create)

            Log.d("NAV", "feed")
            when (item.itemId) {

                R.id.navigation_feed -> {
                    Log.d("NAV", "feed")
                    supportFragmentManager.commit {
                        replace<FeedFragment>(R.id.nav_host_fragment)
                    }

                    supportActionBar?.title = getString(R.string.feed)

//                    supportActionBar?.title = if (userManager.username == null)
//                        "Challenger"
//                    else
//                        "${getString(R.string.hey)}, ${userManager.username}!"

                }

                R.id.navigation_weather -> {
                    supportFragmentManager.commit {
                        replace<WeatherFragment>(R.id.nav_host_fragment)
                    }
                    supportActionBar?.title = getString(R.string.weather)
                }

                R.id.navigation_create -> {
                    supportFragmentManager.commit {
                        replace<CreateFragment>(R.id.nav_host_fragment)
                    }
                    supportActionBar?.title = getString(R.string.set_your_goal)
                }

//                R.id.navigation_record -> {
//
//                }

                R.id.navigation_profile -> {
                    supportFragmentManager.commit {
                        replace<ProfileFragment>(R.id.nav_host_fragment)
                    }
                    supportActionBar?.title =
                        getString(R.string.profile)
                }
            }
            true
        }
        //to avoid item reselection
        binding.navView.setOnItemReselectedListener {}
    }

    companion object {
        private const val WEATHER_URL =
            "https://api.openweathermap.org/data/2.5/" +
                    "weather?appid=$WEATHER_KEY&units=metric&"
        private const val UV_INDEX_URL =
            "https://api.openweathermap.org/data/2.5/" +
                    "uvi?$WEATHER_KEY&"

        //final uid which is used for authorization
        const val FINAL_USER_ID = "finalUid"

        //key for the user's sharedPref
        const val UID_SHARED_PREF = "sharedPrefUid"

        //get unregistered user id
        const val NOT_REGISTERED = "registered"
    }
}
