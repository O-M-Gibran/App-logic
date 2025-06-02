package com.example.app_logic

enum class AsthmaLevel {
    NORMAL,
    MODERATE,
    SEVERE
}

object AsthmaDetector {
    fun detect(vitals: Vitals): AsthmaLevel {
        return when {
            vitals.bpm > 120 && vitals.spo2 < 90 -> AsthmaLevel.SEVERE
            vitals.bpm in 100..120 && vitals.spo2 in 90..95 -> AsthmaLevel.MODERATE
            else -> AsthmaLevel.NORMAL
        }
    }
}
