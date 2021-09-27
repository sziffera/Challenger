package com.sziffer.challenger.viewmodels

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.model.Statistics
import com.sziffer.challenger.model.challenge.Challenge
import com.sziffer.challenger.model.weather.OneCallWeather
import com.sziffer.challenger.model.weather.WeatherRequest
import com.sziffer.challenger.model.weather.WeatherResultListener
import com.sziffer.challenger.sync.startDataDownloaderWorkManager
import com.sziffer.challenger.utils.*
import com.sziffer.challenger.utils.Constants.KEY_WEATHER
import com.sziffer.challenger.utils.Constants.KEY_WEATHER_DATA
import java.text.SimpleDateFormat
import java.util.concurrent.Executors

class MainViewModel() :
    ViewModel(), WeatherResultListener {

    //WEATHER
    private val _weatherData = MutableLiveData<OneCallWeather>()

    var shouldShowUvAlert: Boolean = false
        private set
    var shouldShowWindAlert: Boolean = false
        private set

    val weatherLiveData: LiveData<OneCallWeather>
        get() = _weatherData
    private var weatherRequest: WeatherRequest? = null

    //var challengeRecyclerViewAdapter: ChallengeRecyclerViewAdapter? = null

    private val _profilePhotoUri = MutableLiveData<Uri>()
    val profilePhotoUriLiveData: LiveData<Uri> get() = _profilePhotoUri


    //CHALLENGES
    private val _challengesLiveData = MutableLiveData<ArrayList<Challenge>>()
    val challengesLiveData: LiveData<ArrayList<Challenge>>
        get() = _challengesLiveData
    private val _callObserveWork = MutableLiveData<Boolean>()
    val callObserveWork: LiveData<Boolean>
        get() = _callObserveWork
    private val _statistics = MutableLiveData<ArrayList<Statistics>>()
    val statisticsLiveData: LiveData<ArrayList<Statistics>>
        get() = _statistics

    private val _isTraining = MutableLiveData<Boolean>()
    val isTrainingLiveData: LiveData<Boolean> get() = _isTraining

    private val _avgSpeed = MutableLiveData<Double>()
    private val _distance = MutableLiveData<Double>()

    val avgSpeed: LiveData<Double> get() = _avgSpeed
    val distance: LiveData<Double> get() = _distance

    fun setAvgSpeed(value: Double) = _avgSpeed.postValue(value)
    fun setDistance(value: Double) = _distance.postValue(value)
    fun setIsTraining(value: Boolean) = _isTraining.postValue(value)


    fun requestPhotoUri() {
        if (_profilePhotoUri.value == null) {
            FirebaseManager.mAuth.currentUser?.photoUrl?.let {

                val betterQualityPhoto = it.toString().replace("s96-c", "s492-c")
                _profilePhotoUri.postValue(betterQualityPhoto.toUri())
            }
        }
    }


    //region challenges

    fun calculateStatistics(context: Context) {

        if (_statistics.value != null)
            return

        val cyclingStatistics = Statistics()
        val runningStatistics = Statistics()

        Executors.newSingleThreadExecutor().execute {
            _challengesLiveData.value?.let {
                for (challenge in it) {

                    if (challenge.type == context.getString(R.string.running)) {

                        with(runningStatistics) {
                            totalDistance += challenge.dst
                            totalTime += challenge.dur
                            ++numberOfActivities
                            if (challenge.dst > longestDistance)
                                longestDistance = challenge.dst
                            val format = SimpleDateFormat("dd-MM-yyyy. HH:mm")
                            if (sameMonth(format.parse(challenge.date)!!)) {
                                thisMonth += challenge.dst
                            }
                            if (sameWeek(format.parse(challenge.date)!!))
                                thisWeek += challenge.dst
                            if (sameYear(format.parse(challenge.date)!!))
                                thisYear += challenge.dst
                        }

                    } else {

                        with(cyclingStatistics) {
                            totalDistance += challenge.dst
                            totalTime += challenge.dur
                            ++numberOfActivities
                            if (challenge.dst > longestDistance)
                                longestDistance = challenge.dst
                            val format = SimpleDateFormat("dd-MM-yyyy. HH:mm")
                            if (sameMonth(format.parse(challenge.date)!!)) {
                                thisMonth += challenge.dst
                            }
                            if (sameWeek(format.parse(challenge.date)!!))
                                thisWeek += challenge.dst
                            if (sameYear(format.parse(challenge.date)!!))
                                thisYear += challenge.dst
                        }
                    }


                }
            }

            _statistics.postValue(arrayListOf(cyclingStatistics, runningStatistics))
        }


    }

    @SuppressLint("SimpleDateFormat") //works in this situation
    fun fetchChallenges(context: Context, startDownloader: Boolean = true) {

        Executors.newSingleThreadExecutor().execute {
//            challengesLiveData.value?.let {
//                if (it.isNotEmpty())
//                    return@execute
//            }

            val dbHelper = ChallengeDbHelper(context)
            val list = dbHelper.getAllChallenges() as MutableList<Challenge>
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
            //if the list is empty, it means that no Challenge is stored in db - downloading from firebase
            if (list.isEmpty() && startDownloader) {
                //starting data downloader,
                startDataDownloaderWorkManager(context)
                _callObserveWork.postValue(true)
            } else {
                Log.d("VIEWMODEL", "challenges fetched ${list.count()}")
                _challengesLiveData.postValue(ArrayList(list))
            }
            dbHelper.close()
        }
    }


    fun turnOfObserver() {
        _callObserveWork.postValue(false)
    }

    //endregion challenges


    //region weather

    fun fetchWeatherData(location: Location, context: Context) {

        val storedWeather = getWeatherFromSharedPreferences(context)

        if (storedWeather != null && !shouldRefreshDataSet(
                UpdateTypes.WEATHER,
                45,
                context
            )
        ) {
            Log.d("WEATHER", "the weather is fresh")
            setWeatherData(storedWeather)
        } else {
            if (weatherRequest == null) {
                weatherRequest = WeatherRequest(
                    this,
                    location,
                    context
                )
            }
            weatherRequest?.fetchWeatherData()
        }
    }

    private fun setWeatherData(weatherData: OneCallWeather) {
        checkNeedOfAlerts(weatherData)
        _weatherData.postValue(weatherData)
    }

    override fun weatherFetched(oneCallWeather: OneCallWeather) {
        setWeatherData(oneCallWeather)
    }

    private fun checkNeedOfAlerts(oneCallWeather: OneCallWeather) {
        shouldShowUvAlert = oneCallWeather.current.uvi >= 8
        shouldShowWindAlert = oneCallWeather.current.wind_speed * 3.6 >= 30
    }

    private fun getWeatherFromSharedPreferences(context: Context): OneCallWeather? {
        val sharedPreferences = context.getSharedPreferences(KEY_WEATHER, 0)
        val weatherDataString = sharedPreferences.getString(KEY_WEATHER_DATA, null) ?: return null
        val typeJson = object : TypeToken<OneCallWeather>() {}.type
        return Gson().fromJson(weatherDataString, typeJson)
    }

    //endregion weather

}