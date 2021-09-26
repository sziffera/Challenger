package com.sziffer.challenger.model.challenge

enum class ChallengeType {
    RUNNING {
        override fun drawableName(): String {
            return "running"
        }
    },
    CYCLING {
        override fun drawableName(): String {
            return "cycling"
        }
    };

    abstract fun drawableName(): String
}