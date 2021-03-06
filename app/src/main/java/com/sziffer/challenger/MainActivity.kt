package com.sziffer.challenger

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityMainBinding
import com.sziffer.challenger.model.Challenge
import com.sziffer.challenger.sync.DATA_DOWNLOADER_TAG
import com.sziffer.challenger.sync.startDataDownloaderWorkManager
import com.sziffer.challenger.user.*
import com.sziffer.challenger.utils.*
import com.sziffer.challenger.weather.UvIndex
import com.sziffer.challenger.weather.WeatherData
import okhttp3.*
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*


class MainActivity : AppCompatActivity(), LifecycleObserver,
    SwipeRefreshLayout.OnRefreshListener,
    NetworkStateListener {

    private lateinit var userIdSharedPreferences: SharedPreferences
    private lateinit var lastRefreshSharedPreferences: SharedPreferences
    private var dbHelper: ChallengeDbHelper? = null
    private lateinit var userManager: UserManager
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var lastLocation: Location? = null
    private lateinit var okHttpClient: OkHttpClient

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var myNetworkCallback: MyNetworkCallback
    private var connected = true

    private lateinit var binding: com.sziffer.challenger.databinding.ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.i("MAIN", "OnCreate")

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(this, connectivityManager)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        okHttpClient = OkHttpClient()

        if (!locationPermissionCheck(this)) {
            locationPermissionRequest(this, this)
        } else {
            try {
                fusedLocationProviderClient.lastLocation.addOnCompleteListener {
                    if (it.isSuccessful) {
                        lastLocation = it.result
                        if (lastLocation != null) {
                            fetchWeatherData()
                        }
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

        }

        userManager = UserManager(applicationContext)

        userIdSharedPreferences = getSharedPreferences(UID_SHARED_PREF, Context.MODE_PRIVATE)
        lastRefreshSharedPreferences = getSharedPreferences(LAST_REFRESH, Context.MODE_PRIVATE)

        if (FirebaseManager.isUserValid) {
            Log.i("MAIN", "the user is registered")
            if (userManager.username != null) {
                Log.i("MAIN", "set from usermanager")
                binding.heyUserTextView.text = "Hi " + userManager.username + "!"
            } else {
                FirebaseManager.currentUserRef
                    ?.child("username")?.addListenerForSingleValueEvent(object :
                        ValueEventListener {
                        override fun onCancelled(p0: DatabaseError) {
                        }

                        override fun onDataChange(p0: DataSnapshot) {
                            Log.i("FIREBASE", p0.toString())
                            val name = p0.getValue(String::class.java) as String
                            binding.heyUserTextView.text = "Hey, " + name + "!"
                            //also store the name for further usage
                            userManager.username = name
                        }
                    })
            }


        } else {

            binding.heyUserTextView.visibility = View.GONE
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this.applicationContext)

        binding.userProfileimageButton.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        binding.takeATourButton.setOnClickListener {
            appTour()
        }

        binding.createChallengeButton.setOnClickListener {
            startActivity(Intent(this, CreateChallengeActivity::class.java))
        }

        binding.recordActivityButton.setOnClickListener {
            if (locationPermissionCheck(this)) {
                setRequestingLocationUpdates(this, false)
                val buttonSettings = getSharedPreferences("button", 0)
                with(buttonSettings.edit()) {
                    putBoolean("started", false)
                    apply()
                }

                val intent = Intent(applicationContext, ChallengeRecorderActivity::class.java)
                intent.putExtra(ChallengeRecorderActivity.CHALLENGE, false)
                startActivity(intent)
            } else {
                locationPermissionRequest(this, this)
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener(this)

        binding.showMoreButton.setOnClickListener {

            startActivity(
                Intent(this, AllChallengeActivity::class.java),
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        }
    }

    override fun onStart() {

        if (connectivityManager.allNetworks.isEmpty()) {
            connected = false
        }
        myNetworkCallback.registerCallback()

        Log.i("MAIN", "OnStart")
        super.onStart()
        setUpView()

    }

    override fun onStop() {
        Log.i("MAIN", "OnStop")
        myNetworkCallback.unregisterCallback()
        dbHelper?.close()
        dbHelper = null
        super.onStop()
    }

    private fun setUpView() {
        Log.i("MAIN", "SetUpView called")
        dbHelper = ChallengeDbHelper(this)
        val list = dbHelper?.getAllChallenges() as MutableList<Challenge>

        //sorts the challenge list based on date
        list.sortWith { o1, o2 ->
            if (o1.date.isEmpty() || o2.date.isEmpty()) 0
            else {
                val format = SimpleDateFormat("dd-MM-yyyy. HH:mm")
                val date1 = format.parse(o1.date)!!
                val date2 = format.parse(o2.date)!!
                date1
                    .compareTo(date2)
            }
        }
        list.reverse()

        if (list.isEmpty()) {
            //starting data downloader,
            startDataDownloaderWorkManager(applicationContext)
            observeWork()
            with(binding) {
                chooseChallenge.text = getText(R.string.it_s_empty_here_let_s_do_some_sports)
                recyclerView.visibility = View.INVISIBLE
                takeATourTextView.visibility = View.VISIBLE
                emptyImageView.visibility = View.VISIBLE
                takeATourButton.visibility = View.VISIBLE
                showMoreButton.visibility = View.INVISIBLE
            }

        } else {
            with(binding) {
                chooseChallenge.text = getText(R.string.choose_a_challenge)
                recyclerView.visibility = View.VISIBLE
                takeATourTextView.visibility = View.GONE
                emptyImageView.visibility = View.GONE
                takeATourButton.visibility = View.GONE
                showMoreButton.visibility = View.VISIBLE
                with(recyclerView) {
                    adapter =
                        ChallengeRecyclerViewAdapter(
                            list as ArrayList<Challenge>,
                            this@MainActivity
                        )
                    addItemDecoration(
                        DividerItemDecoration(
                            recyclerView.context,
                            DividerItemDecoration.VERTICAL
                        )
                    )
                }
            }
        }
        dbHelper?.close()
    }

    private fun observeWork() {
        WorkManager.getInstance(applicationContext)
            .getWorkInfosByTagLiveData(DATA_DOWNLOADER_TAG)
            .observe(this, Observer { workInfo ->
                if (workInfo != null && workInfo[0].state == WorkInfo.State.SUCCEEDED) {
                    Log.i("MAIN", "WorkManager succeeded")
                    setUpView()
                    binding.swipeRefreshLayout.isRefreshing = false
                    updateRefreshDate(UpdateTypes.DATA_SYNC)
                }
            })
    }

    private fun appTour() {
        MaterialShowcaseView.resetSingleUse(this, SHOWCASE_ID)
        val config = ShowcaseConfig()
        config.delay = 500
        val sequence = MaterialShowcaseSequence(this, SHOWCASE_ID)
        sequence.setConfig(config)

        sequence.addSequenceItem(
            MaterialShowcaseView.Builder(this)
                .setTarget(binding.recordActivityButton)
                .setDismissText(getString(R.string.got_it))
                .setContentText(getString(R.string.record_activity_showcase_text))
                .withRectangleShape(true)
                .build()
        )

        sequence.addSequenceItem(
            MaterialShowcaseView.Builder(this)
                .setTarget(binding.createChallengeButton)
                .setDismissText(getString(R.string.let_s_do_it))
                .setContentText(getString(R.string.create_challenge_showcase_text))
                .withRectangleShape(true)
                .build()
        )
        sequence.start()
    }

    //region Weather

    /** Sets windDirectionImage's color based on wind speed according to Beaufort Scala*/
    private fun setBeaufortWindColor(windSpeed: Int) {
        when (windSpeed) {
            in 0..38 -> binding.windDirectionImageView.setColorFilter(android.R.color.white)
            in 39..49 -> binding.windDirectionImageView.setColorFilter(R.color.colorWindYellow)
            in 50..61 -> binding.windDirectionImageView.setColorFilter(android.R.color.holo_orange_light)
            in 62..74 -> binding.windDirectionImageView.setColorFilter(android.R.color.holo_orange_dark)
            in 75..88 -> binding.windDirectionImageView.setColorFilter(android.R.color.holo_red_light)
            in 89..102 -> binding.windDirectionImageView.setColorFilter(android.R.color.holo_red_dark)
            in 103..117 -> binding.windDirectionImageView.setColorFilter(R.color.colorStop)
            else -> binding.windDirectionImageView.setColorFilter(android.R.color.black)
        }
    }

    /** Downloads weather data based on lastKnownLocation */
    private fun fetchWeatherData() {

        // if the last location is null or the weather has just been fetched, no need to update
        if (lastLocation == null || !shouldRefreshDataSet(UpdateTypes.WEATHER, 20))
            return

        //variable to help deciding whether it is dark or not outside
        var shouldShowUv: Boolean

        //fetching current weather data
        val request = Request.Builder()
            .url("${WEATHER_URL}lat=${lastLocation!!.latitude}&lon=${lastLocation!!.longitude}")
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.i("WEATHER", e.toString())
            }


            @SuppressLint("SimpleDateFormat")
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val data = response.body?.string()
                    if (data != null) {
                        val typeJson = object : TypeToken<WeatherData>() {}.type
                        val weatherData = Gson()
                            .fromJson<WeatherData>(data, typeJson)
                        Log.i("WEATHER", weatherData.toString())

                        val cal = Calendar.getInstance()
                        val tz = cal.timeZone
                        val format = SimpleDateFormat("HH:mm")
                        format.timeZone = tz
                        val localSunset = format.format(Date(weatherData.sys.sunset * 1000))
                        val localSunrise = format.format(Date(weatherData.sys.sunrise * 1000))
                        Log.i("DATENOW", format.format(Date().time))

                        shouldShowUv = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            LocalTime.now().isAfter(LocalTime.parse(localSunrise)) &&
                                    LocalTime.now().isBefore(LocalTime.parse(localSunset))
                        } else {
                            val localTimeString = format.format(Date())
                            val localTime = format.parse(localTimeString)
                            val sunrise: Date = format.parse(localSunrise)
                            val sunset: Date = format.parse(localSunset)
                            //returns -1 if the date is before the compared
                            sunrise.compareTo(localTime) == -1 && localTime?.compareTo(sunset) == -1
                        }

                        //if its not cloudy and not night, fetching UV index
                        if (shouldShowUv && weatherData.clouds.all < 95)
                            fetchUvIndex()

                        Log.d("date", "$localSunset $localSunrise and the bool is: $shouldShowUv")

                        runOnUiThread {
                            with(binding) {
                                weatherDegreesTextView.visibility = View.VISIBLE
                                windSpeedTextView.visibility = View.VISIBLE
                                windDirectionImageView.visibility = View.VISIBLE
                                weatherDegreesTextView.text = "${weatherData.main.temp.toInt()}°C"
                                val windSpeed = weatherData.wind.speed.times(3.6)
                                windSpeedTextView.text = "${windSpeed.toInt()}km/h"
                                windDirectionImageView.rotation = -90f + weatherData.wind.deg

                                setBeaufortWindColor(windSpeed.toInt())
                            }
                            // updating weather update time for weather
                            updateRefreshDate(UpdateTypes.WEATHER)
                        }
                    }
                } else {
                    Log.i("WEATHER", "WAS NOT SUCCESSFUL")
                }
            }
        })
    }

    private fun fetchUvIndex() {
        val request = Request.Builder()
            .url("${UV_INDEX_URL}lat=${lastLocation!!.latitude}&lon=${lastLocation!!.longitude}")
            .build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                //do nothing
                Log.i("UVINDEX", "FAILED")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val data = response.body?.string()
                    if (data != null) {
                        val typeJson = object : TypeToken<UvIndex>() {}.type
                        val uvIndex = Gson()
                            .fromJson<UvIndex>(data, typeJson)
                        Log.i("UVINDEX", uvIndex.value.toString())
                        runOnUiThread {
                            binding.uvIndexTextView.text =
                                getStringFromNumber(1, uvIndex.value)
                            binding.uvLinearLayout.visibility = View.VISIBLE
                            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                setUvIndexColor(uvIndex = uvIndex.value)
                            }
                        }
                    }
                }
            }
        })
    }

    /** Sets the background color for UV index based on its strength */
    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("ResourceAsColor")
    //api level
    private fun setUvIndexColor(uvIndex: Double) {
        when (uvIndex) {
            in 0.0..2.9 -> {
                binding.uvIndexTextView.backgroundTintList = resources.getColorStateList(
                    R.color.colorGreen,
                    null
                )
            }
            in 3.0..5.9 -> {
                binding.uvIndexTextView.backgroundTintList =
                    resources.getColorStateList(R.color.colorWindYellow, null)
            }
            in 6.0..7.9 -> {
                binding.uvIndexTextView.backgroundTintList =
                    resources.getColorStateList(android.R.color.holo_orange_dark, null)
            }
            in 8.0..10.9 -> {
                binding.uvIndexTextView.backgroundTintList =
                    resources.getColorStateList(android.R.color.holo_red_dark, null)
            }
            else -> {
                binding.uvIndexTextView.backgroundTintList =
                    resources.getColorStateList(android.R.color.holo_purple, null)
            }
        }
    }

    //endregion Weather

    // swipe refresh layout helper method
    override fun onRefresh() {

        if (!shouldRefreshDataSet(UpdateTypes.DATA_SYNC, 10)) {
            binding.swipeRefreshLayout.isRefreshing = false
            Toast.makeText(
                this, getString(R.string.last_update_warning),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        startDataDownloaderWorkManager(applicationContext)
        observeWork()
        if (!connected) {
            Toast.makeText(
                this, getString(R.string.no_internet_connection_will_update),
                Toast.LENGTH_LONG
            ).show()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun shouldRefreshDataSet(type: UpdateTypes, minutes: Int): Boolean {

        val lastRefreshString = if (type == UpdateTypes.DATA_SYNC) lastRefreshSharedPreferences
            .getString(LAST_REFRESH_TIME_SYNC, null)
        else
            lastRefreshSharedPreferences
                .getString(LAST_REFRESH_TIME_WEATHER, null)

        return if (lastRefreshString == null)
            true
        else {
            val lastRefreshDate: Date? = SimpleDateFormat("dd-MM-yyyy. HH:mm")
                .parse(lastRefreshString)
            val currentTime = Calendar.getInstance().time
            //difference in sec
            val difference = (currentTime.time - (lastRefreshDate?.time ?: 0))
                .div(1000).also {
                    Log.i("MAIN", "$it is the difference")
                }
            difference > minutes * 10
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun updateRefreshDate(type: UpdateTypes) {
        val currentDate: String
        currentDate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val current = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy. HH:mm")
            current.format(formatter)

        } else {
            val date = Date();
            val formatter = SimpleDateFormat("dd-MM-yyyy. HH:mm")
            formatter.format(date)
        }
        with(lastRefreshSharedPreferences.edit()) {
            if (type == UpdateTypes.DATA_SYNC)
                putString(LAST_REFRESH_TIME_SYNC, currentDate)
            else
                putString(LAST_REFRESH_TIME_WEATHER, currentDate)
            apply()
        }
    }

    override fun noInternetConnection() {
        connected = false
    }

    override fun connectedToInternet() {
        connected = true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST) {
            Log.i("MAIN", grantResults.toString())
            try {
                fusedLocationProviderClient.lastLocation.addOnCompleteListener {
                    if (it.isSuccessful) {
                        lastLocation = it.result
                        if (lastLocation != null) {
                            fetchWeatherData()
                        }
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private enum class UpdateTypes {
        WEATHER, DATA_SYNC
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

        private const val LAST_REFRESH = "${SHOWCASE_ID}.LastRefresh"
        private const val LAST_REFRESH_TIME_SYNC = "time"
        private const val LAST_REFRESH_TIME_WEATHER = "weatherTime"

        //get unregistered user id
        const val NOT_REGISTERED = "registered"
    }
}
