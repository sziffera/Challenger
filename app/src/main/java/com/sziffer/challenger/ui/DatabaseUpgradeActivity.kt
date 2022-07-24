package com.sziffer.challenger.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.psambit9791.jdsp.signal.Smooth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sziffer.challenger.R
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.database.FirebaseManager
import com.sziffer.challenger.databinding.ActivityDatabaseUpgradeBinding
import com.sziffer.challenger.model.challenge.MyLocation
import com.sziffer.challenger.utils.Constants
import com.sziffer.challenger.utils.databaseUpgradeDone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

class DatabaseUpgradeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDatabaseUpgradeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDatabaseUpgradeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.upgradeButton.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            binding.upgradeButton.isEnabled = false
            upgradeWithFlow()
        }
    }

    private fun upgradeWithFlow() {
        CoroutineScope(Dispatchers.Default).launch {
            processChallenges().collect {
                withContext(Dispatchers.Main) {
                    when (it) {
                        is UpgradeStatus.Success -> successfulUpgrade()
                        is UpgradeStatus.Progress -> binding.progressBar.setProgress(
                            it.progress,
                            true
                        )
                        is UpgradeStatus.Error -> upgradeFailed()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        Toast.makeText(
            this,
            "Please do not leave this screen without finishing the upgrade",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun upgradeFailed() {
        binding.upgradeButton.isEnabled = true
        binding.upgradeButton.text = getString(R.string.upgrade_failed_retry)
    }

    private fun successfulUpgrade() {
        databaseUpgradeDone(this)
        startActivity(
            Intent(
                this, MainActivity::class.java
            )
        )
        this.finish()
    }

    private suspend fun processChallenges(): Flow<UpgradeStatus> {

        return flow {


            val dbHelper = ChallengeDbHelper(applicationContext)

            val challenges = dbHelper.getAllChallenges()
            val size = challenges.count().toDouble()

            var processed = 0.0
            val mRef = FirebaseManager.currentUsersChallenges

            for (challenge in challenges) {
                Log.d("UPGRADE", "$processed/$size")
                val typeJson = object : TypeToken<ArrayList<MyLocation>>() {}.type
                val route =
                    Gson().fromJson<ArrayList<MyLocation>>(challenge.routeAsString, typeJson)
                if (route.size > Constants.MIN_ROUTE_SIZE) {
                    val doubleArray = route.map { it.altitude.toDouble() }
                    val elevationArray = doubleArray.toDoubleArray()
                    var windowSize = elevationArray.size.div(Constants.WINDOW_SIZE_HELPER)
                    if (windowSize > Constants.MAX_WINDOW_SIZE)
                        windowSize = Constants.MAX_WINDOW_SIZE
                    Log.d("ELEVATION", "the calculated window size is: $windowSize")
                    try {
                        val s1 = Smooth(elevationArray, windowSize, Constants.SMOOTH_MODE)
                        val filteredElevation = s1.smoothSignal()
                        var elevGain = 0.0
                        var elevLoss = 0.0
                        for (i in 0..filteredElevation.size - 2) {
                            route[i].altitude = filteredElevation[i]
                            if (filteredElevation[i] < filteredElevation[i + 1]) {
                                elevGain += abs(filteredElevation[i] - filteredElevation[i + 1])
                            } else {
                                elevLoss += abs(filteredElevation[i] - filteredElevation[i + 1])
                            }
                        }
                        route.last().altitude = filteredElevation.last()
                        challenge.elevLoss = elevLoss.roundToInt()
                        challenge.elevGain = elevGain.roundToInt()
                        dbHelper.updateChallenge(challenge.id.toInt(), challenge)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                    }
                }
                processed++
                emit(UpgradeStatus.Progress(((processed / (size * 2)) * 100.0).roundToInt()))
            }

            dbHelper.getAllChallenges().forEach {
                mRef?.child(it.firebaseId)?.setValue(it)?.await()
                processed++
                emit(UpgradeStatus.Progress(((processed / (size * 2)) * 100.0).roundToInt()))
            }

            dbHelper.close()
            emit(UpgradeStatus.Success)
        }.catch {
            Log.e("UPGRADE", it.localizedMessage ?: "no message")
            emit(UpgradeStatus.Error(it.localizedMessage ?: "Something happened"))
        }


    }


    sealed class UpgradeStatus {

        object Success : UpgradeStatus()

        data class Error(val message: String) : UpgradeStatus()

        data class Progress(val progress: Int) : UpgradeStatus()

    }
}