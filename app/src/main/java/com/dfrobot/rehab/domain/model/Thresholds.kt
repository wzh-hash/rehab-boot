package com.dfrobot.rehab.domain.model

/** 三档负重阈值,单位 kg(由体重 × 百分比换算)。 */
data class Thresholds(
    val p25Kg: Double,
    val p50Kg: Double,
    val p75Kg: Double,
)
