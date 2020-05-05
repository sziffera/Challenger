package com.sziffer.challenger.user

interface NetworkStateListener {
    fun noInternetConnection()
    fun connectedToInternet()
}