package com.dfrobot.rehab.domain

import com.dfrobot.rehab.domain.model.Thresholds

/**
 * 体重百分比 → 阈值 kg 换算。
 * 约束:0 < p25 <= p50 <= p75 <= 100;体重 10..300 kg。违规抛 [IllegalArgumentException]。
 */
object ThresholdCalculator {

    fun fromPercentages(bodyWeightKg: Double, p25: Int, p50: Int, p75: Int): Thresholds {
        require(bodyWeightKg in 10.0..300.0) { "体重必须在 10~300 kg 之间" }
        require(p25 > 0 && p25 <= p50 && p50 <= p75 && p75 <= 100) {
            "百分比必须满足 0 < 25% <= 50% <= 75% <= 100%"
        }
        return Thresholds(
            p25Kg = round1(bodyWeightKg * p25 / 100.0),
            p50Kg = round1(bodyWeightKg * p50 / 100.0),
            p75Kg = round1(bodyWeightKg * p75 / 100.0),
        )
    }

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0
}
