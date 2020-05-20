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


    companion object {
        private const val NAME = "UserManager"
        private const val KEY_USER = "${NAME}.user"
        private const val KEY_USERNAME = "${NAME}.username"
        private const val KEY_USER_WEIGHT = "${NAME}.weight"
        private const val KEY_USER_HEIGHT = "${NAME}.height"
        private const val KEY_USER_EMAIL = "${NAME}.email"
    }
}