package com.sziffer.challenger.utils

interface NetworkStateListener {
    fun noInternetConnection()
    fun connectedToInternet()
}