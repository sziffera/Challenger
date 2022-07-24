package com.sziffer.challenger.model.heartrate

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class HeartRateZones(
    var relaxed: Double = 0.0,
    var moderate: Double = 0.0,
    var weightControl: Double = 0.0,
    var aerobic: Double = 0.0,
    var anaerobic: Double = 0.0,
    var vo2Max: Double = 0.0
) : Parcelable
