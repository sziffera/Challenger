package com.sziffer.challenger

object ChallengeManager {
    var currentChallenge: Challenge? = null
    var previousChallenge: Challenge? = null
    var isUpdate: Boolean = false
        set(value) {
            if (value)
                isChallenge = false
            field = value
        }
    var isChallenge: Boolean = false
        set(value) {
            if (value)
                isUpdate = false
            field = value
        }
}