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
    },
    ANY {
        override fun drawableName(): String {
            return "any" // unused
        }
    };

    abstract fun drawableName(): String
}