package com.example.app_logic

enum class AsthmaLevel {
    NORMAL,
    MODERATE,
    SEVERE
}

object AsthmaDetector {

    /**
     * Detect asthma severity based on BPM and SpO2.
     * Thresholds based on referenced article and clinical standards:
     * - Severe: SpO2 < 90% OR BPM > 120 (severe hypoxia or tachycardia)
     * - Moderate: SpO2 90-94% OR BPM 101-120 (mild hypoxia or mild tachycardia)
     * - Normal: SpO2 >= 95% AND BPM <= 100
     */
    fun detect(vitals: Vitals): AsthmaLevel {
        return when {
            vitals.spo2 < 90 || vitals.bpm > 120 -> AsthmaLevel.SEVERE
            (vitals.spo2 in 90..94) || (vitals.bpm in 101..120) -> AsthmaLevel.MODERATE
            else -> AsthmaLevel.NORMAL
        }
    }
}
