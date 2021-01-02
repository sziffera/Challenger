package com.sziffer.challenger.ble

import android.app.Service
import android.content.Intent
import android.os.IBinder


class CadenceSensorService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


}