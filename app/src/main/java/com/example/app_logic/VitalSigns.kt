package com.example.app_logic

data class Vitals(
    val bpm: Int,
    val spo2: Int
)

data class Measurement(
    val bpmMeasured: Int,
    val bpmReference: Int,
    val spo2Measured: Int,
    val spo2Reference: Int
) {
    companion object {
        const val MAX_ERROR_SPO2 = 6.0
        const val MAX_ERROR_BPM = 50.0
    }

    fun bpmError(): Double =
        if (bpmReference != 0) 100.0 * kotlin.math.abs(bpmMeasured - bpmReference) / bpmReference else 0.0

    fun spo2Error(): Double =
        if (spo2Reference != 0) 100.0 * kotlin.math.abs(spo2Measured - spo2Reference) / spo2Reference else 0.0

    fun isValid(): Boolean =
        bpmError() <= MAX_ERROR_BPM && spo2Error() <= MAX_ERROR_SPO2
}
