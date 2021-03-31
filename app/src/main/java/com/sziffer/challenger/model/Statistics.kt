package com.sziffer.challenger.model

data class Statistics(
    var totalDistance: Double = 0.0,
    var totalTime: Double = 0.0,
    var thisYear: Double = 0.0,
    var thisMonth: Double = 0.0,
    var thisWeek: Double = 0.0,
    var numberOfActivities: Int = 0,
    var longestDistance: Double = 0.0,
    var elevation: Double = 0.0
)