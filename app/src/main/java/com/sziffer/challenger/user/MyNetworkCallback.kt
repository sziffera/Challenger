package com.sziffer.challenger.user

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.util.Log

class MyNetworkCallback(
    private val listener: NetworkStateListener,
    private val connectivityManager: ConnectivityManager
) {

    private val networkRequest = NetworkRequest.Builder().build()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            listener.noInternetConnection()
            super.onLost(network)
        }

        override fun onUnavailable() {
            listener.noInternetConnection()
            super.onUnavailable()
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            listener.noInternetConnection()
            super.onLosing(network, maxMsToLive)
        }

        override fun onAvailable(network: Network) {
            listener.connectedToInternet()
            super.onAvailable(network)
        }
    }

    fun registerCallback() {
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    fun unregisterCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.i(
                MyNetworkCallback::class.java.simpleName,
                "NetworkCallback for Wi-fi was not registered or already unregistered"
            )
        }
    }


}