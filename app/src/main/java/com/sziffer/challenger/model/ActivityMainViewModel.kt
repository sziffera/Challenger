package com.sziffer.challenger.model

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.model.weather.OneCallWeather
import com.sziffer.challenger.model.weather.WeatherRequest
import com.sziffer.challenger.model.weather.WeatherResultListener
import com.sziffer.challenger.sync.startDataDownloaderWorkManager
import com.sziffer.challenger.utils.*
import com.sziffer.challenger.utils.Constants.KEY_WEATHER
import com.sziffer.challenger.utils.Constants.KEY_WEATHER_DATA
import java.text.SimpleDateFormat

class ActivityMainViewModel : ViewModel(), WeatherResultListener {

    //WEATHER
    private val _weatherData = MutableLiveData<OneCallWeather>()

    var shouldShowUvAlert: Boolean = false
        private set
    var shouldShowWindAlert: Boolean = false
        private set

    val weatherLiveData: LiveData<OneCallWeather>
        get() = _weatherData
    private var weatherRequest: WeatherRequest? = null


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


    //region challenges

    fun calculateStatistics(context: Context) {

        if (_statistics.value != null)
            return

        val cyclingStatistics = Statistics()
        val runningStatistics = Statistics()

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

    @SuppressLint("SimpleDateFormat") //works in this situation
    fun fetchChallenges(context: Context, startDownloader: Boolean = true) {
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
            _challengesLiveData.postValue(ArrayList(list))
        }
        dbHelper.close()
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