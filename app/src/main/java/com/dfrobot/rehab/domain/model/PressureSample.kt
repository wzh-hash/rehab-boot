package com.dfrobot.rehab.domain.model

/** 一次压力采样。 */
data class PressureSample(
    val valueKg: Double,
    val timestampMillis: Long,
)
