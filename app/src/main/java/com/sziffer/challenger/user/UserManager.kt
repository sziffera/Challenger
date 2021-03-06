package com.sziffer.challenger.user

import android.content.Context
import android.util.Log

class UserManager(
    private val context: Context
) {

    private val sharedPreferences =
        context.getSharedPreferences(KEY_USER, 0)

    var username: String? = null
        get() {
            return sharedPreferences.getString(KEY_USERNAME, null)
        }
        set(value) {
            Log.i("USERMANAGER", value.toString())
            with(sharedPreferences.edit()) {
                putString(KEY_USERNAME, value)
                apply()
            }
            field = value
        }
    var email: String? = null
        get() {
            return sharedPreferences.getString(KEY_USER_EMAIL, null)
        }
        set(value) {
            Log.i("USERMANAGER", value.toString())
            with(sharedPreferences.edit()) {
                putString(KEY_USER_EMAIL, value)
                apply()
            }
            field = value
        }
    var height: Int = 0
        get() {
            return sharedPreferences.getInt(KEY_USER_HEIGHT, 0)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putInt(KEY_USER_HEIGHT, value)
                apply()
            }
            field = value
        }

    var weight: Int = 0
        get() {
            return sharedPreferences.getInt(KEY_USER_WEIGHT, 0)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putInt(KEY_USER_WEIGHT, value)
                apply()
            }
            field = value
        }

    var autoPause: Boolean = true
        get() {
            return sharedPreferences.getBoolean(KEY_USER_AUTO_PAUSE, false)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(KEY_USER_AUTO_PAUSE, value)
                apply()
            }
            field = value
        }

    var preventScreenLock: Boolean = false
        get() {
            return sharedPreferences.getBoolean(KEY_USER_PREVENT_SCREEN_LOCK, false)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(KEY_USER_PREVENT_SCREEN_LOCK, value)
                apply()
            }
            field = value
        }

    var startStop: Boolean = false
        get() {
            return sharedPreferences.getBoolean(KEY_USER_START_STOP, false)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(KEY_USER_START_STOP, value)
                apply()
            }
            field = value
        }
    var distance: Boolean = false
        get() {
            return sharedPreferences.getBoolean(KEY_USER_DISTANCE, false)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(KEY_USER_DISTANCE, value)
                apply()
            }
            field = value
        }
    var duration: Boolean = false
        get() {
            return sharedPreferences.getBoolean(KEY_USER_DURATION, false)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(KEY_USER_DURATION, value)
                apply()
            }
            field = value
        }
    var difference: Boolean = false
        get() {
            return sharedPreferences.getBoolean(KEY_USER_DIFFERENCE, false)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(KEY_USER_DIFFERENCE, value)
                apply()
            }
            field = value
        }
    var avgSpeed: Boolean = false
        get() {
            return sharedPreferences.getBoolean(KEY_USER_AVG, false)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(KEY_USER_AVG, value)
                apply()
            }
            field = value
        }

    var bmi: Float = 0f
        get() {
            return sharedPreferences.getFloat(KEY_USER_BMI, 0f)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putFloat(KEY_USER_BMI, value)
                apply()
            }
            field = value
        }

    var bodyFat: Float = 0f
        get() {
            return sharedPreferences.getFloat(KEY_USER_BODY_FAT, 0f)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putFloat(KEY_USER_BODY_FAT, value)
                apply()
            }
            field = value
        }
    var isFemale: Boolean = true
        get() {
            return sharedPreferences.getBoolean(KEY_USER_IS_FEMALE, true)
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(KEY_USER_IS_FEMALE, value)
                apply()
            }
            field = value
        }

    companion object {
        private const val NAME = "UserManager"
        private const val KEY_USER = "${NAME}.user"
        private const val KEY_USERNAME = "${NAME}.username"
        private const val KEY_USER_WEIGHT = "${NAME}.weight"
        private const val KEY_USER_AUTO_PAUSE = "${NAME}.autoPause"
        private const val KEY_USER_PREVENT_SCREEN_LOCK = "${NAME}.screenLock"
        private const val KEY_USER_HEIGHT = "${NAME}.height"
        private const val KEY_USER_EMAIL = "${NAME}.email"
        private const val KEY_USER_AVG = "${NAME}.avg"
        private const val KEY_USER_DURATION = "${NAME}.dur"
        private const val KEY_USER_DISTANCE = "${NAME}.dist"
        private const val KEY_USER_DIFFERENCE = "${NAME}.diff"
        private const val KEY_USER_START_STOP = "${NAME}.startStop"
        private const val KEY_USER_BMI = "${NAME}.bmi"
        private const val KEY_USER_BODY_FAT = "${NAME}.bodyFat"
        private const val KEY_USER_IS_FEMALE = "${NAME}.isFemale"
    }
}