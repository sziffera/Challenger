package com.sziffer.challenger.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.LifecycleObserver
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.location.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sziffer.challenger.R
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityMainBinding
import com.sziffer.challenger.model.ActivityMainViewModel
import com.sziffer.challenger.model.UserManager
import com.sziffer.challenger.ui.*
import com.sziffer.challenger.ui.user.UserSettingsActivity
import com.sziffer.challenger.utils.*
import com.sziffer.challenger.utils.extensions.NavigationBottomBarSectionsStateKeeperWorkaround
import okhttp3.*
import java.util.*


class MainActivity : AppCompatActivity(), LifecycleObserver {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userManager: UserManager

    private val viewModel: ActivityMainViewModel by viewModels()


    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var locationRequest: LocationRequest? = null
    private var locationCallback: LocationCallback? = null


    private val navSectionsStateKeeper by lazy {
        NavigationBottomBarSectionsStateKeeperWorkaround(
            activity = this,
            navHostContainerID = R.id.nav_host_fragment,
            navGraphIds = listOf(
                R.navigation.main_navigation
            ),
            bottomNavigationViewID = R.id.nav_view
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userManager = UserManager(this)


        if (userManager.username == null) {
            setUserName()
        }

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)

        if (!locationPermissionCheck(this)) {
            locationPermissionRequest(this, this)
        } else {
            checkLastLocation()
        }

        //if the recorder activity crashed, restores the bool for recording (important for ui init)
        val buttonSharedPreferences = getSharedPreferences("button", 0)
        buttonSharedPreferences.edit().putBoolean("started", false).apply()

        setSupportActionBar(binding.toolbar)

        supportActionBar?.title = if (userManager.username == null)
            "Challenger"
        else {
            if (userManager.username == "Biliczki Judit")
                "Hajrá Cukim!"
            else "${getString(R.string.hey)}, ${userManager.username}!"
        }

        binding.settingsImageButton.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    UserSettingsActivity::class.java
                )
            )
        }

        //navSectionsStateKeeper.onCreate(savedInstanceState)

        setUpBottomNavBar()
    }


    override fun onSupportNavigateUp() =
        navSectionsStateKeeper.onSupportNavigateUp()

    override fun onBackPressed() {
        if (!navSectionsStateKeeper.onSupportNavigateUp())
            super.onBackPressed()
    }


    //region weather

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
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
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
                fetchWeatherData(p0.lastLocation)
            }
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
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
        binding.navView.setOnNavigationItemSelectedListener { item ->
            Log.d("NAV", "feed")
            when (item.itemId) {

                R.id.navigation_feed -> {
                    Log.d("NAV", "feed")
                    supportFragmentManager.commit {
                        replace<FeedFragment>(R.id.nav_host_fragment)
                    }
                    supportActionBar?.title = if (userManager.username == null)
                        "Challenger"
                    else
                        "${getString(R.string.hey)}, ${userManager.username}!"

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

                R.id.navigation_record -> {
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
                        }
                    )
                    finish()
                }

                R.id.navigation_profile -> {
                    supportFragmentManager.commit {
                        replace<ProfileFragment>(R.id.nav_host_fragment)
                    }
                    supportActionBar?.title =
                        "${getString(R.string.profile)} - ${userManager.username}"
                }
            }
            true
        }
        //avoid item reselection
        binding.navView.setOnNavigationItemReselectedListener {
            Log.d("MAIN", "Reselected")
        }
    }

    private fun BottomNavigationView.checkItem(actionId: Int) {
        menu.findItem(actionId)?.isChecked = true
    }

    private fun initBottomNavBar() {
        val navController = findNavController(R.id.nav_host_fragment)


        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_feed,
                R.id.navigation_create,
                R.id.navigation_record,
                R.id.navigation_profile
            )
        )
        //setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

    }

    companion object {
        private const val SHOWCASE_ID = "MainActivity"
        private const val REQUEST = 200
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

        private const val LAST_REFRESH = "$SHOWCASE_ID.LastRefresh"
        private const val LAST_REFRESH_TIME_SYNC = "time"
        private const val LAST_REFRESH_TIME_WEATHER = "weatherTime"

        //get unregistered user id
        const val NOT_REGISTERED = "registered"
    }
}
