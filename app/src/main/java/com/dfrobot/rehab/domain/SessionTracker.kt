package com.dfrobot.rehab.domain

import com.dfrobot.rehab.domain.model.PressureSample
import com.dfrobot.rehab.domain.model.TrainingSession

enum class SessionPhase { Idle, Running, Paused }

data class SessionStats(
    val elapsedMillis: Long,
    val sampleCount: Int,
    val avgPressureKg: Double,
    val peakPressureKg: Double,
)

/**
 * 训练会话状态机(纯类,无协程无 Android):
 * Idle → Running ⇄ Paused → Idle(finish 后回到 Idle,产出 [TrainingSession])。
 * 仅 Running 期间计入样本;暂停冻结计时,恢复平移基准时间。
 */
class SessionTracker(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    var phase: SessionPhase = SessionPhase.Idle
        private set

    var stats: SessionStats = SessionStats(0, 0, 0.0, 0.0)
        private set

    private var baseStartMillis = 0L
    private var frozenElapsedMillis = 0L
    private var sumPressureKg = 0.0
    private var lastStartMillis = 0L

    fun start() {
        check(phase == SessionPhase.Idle) { "会话已在进行中" }
        phase = SessionPhase.Running
        lastStartMillis = nowMillis()
        baseStartMillis = lastStartMillis
        frozenElapsedMillis = 0L
        sumPressureKg = 0.0
        stats = SessionStats(0, 0, 0.0, 0.0)
    }

    fun pause() {
        check(phase == SessionPhase.Running) { "会话未在训练中" }
        phase = SessionPhase.Paused
        frozenElapsedMillis = elapsedSince(lastStartMillis)
    }

    fun resume() {
        check(phase == SessionPhase.Paused) { "会话未暂停" }
        phase = SessionPhase.Running
        lastStartMillis = nowMillis()
        baseStartMillis = lastStartMillis - frozenElapsedMillis
    }

    fun ingest(sample: PressureSample) {
        if (phase != SessionPhase.Running) return
        sumPressureKg += sample.valueKg
        stats = SessionStats(
            elapsedMillis = elapsedSince(lastStartMillis),
            sampleCount = stats.sampleCount + 1,
            avgPressureKg = sumPressureKg / (stats.sampleCount + 1),
            peakPressureKg = maxOf(stats.peakPressureKg, sample.valueKg),
        )
    }

    /** 刷新计时(无样本到达时由 UI 周期调用,保证时长持续走动)。 */
    fun tick() {
        if (phase == SessionPhase.Running) {
            stats = stats.copy(elapsedMillis = elapsedSince(lastStartMillis))
        }
    }

    fun finish(): TrainingSession {
        check(phase != SessionPhase.Idle) { "没有进行中的会话" }
        val endMillis = nowMillis()
        val duration = frozenElapsedMillis + if (phase == SessionPhase.Running) elapsedSince(lastStartMillis) else 0L
        val session = TrainingSession(
            startTimeMillis = baseStartMillis,
            endTimeMillis = endMillis,
            durationMillis = duration,
            avgPressureKg = stats.avgPressureKg,
            peakPressureKg = stats.peakPressureKg,
        )
        reset()
        return session
    }

    private fun elapsedSince(since: Long): Long =
        (nowMillis() - since).coerceAtLeast(0L)

    private fun reset() {
        phase = SessionPhase.Idle
        baseStartMillis = 0L
        frozenElapsedMillis = 0L
        sumPressureKg = 0.0
        stats = SessionStats(0, 0, 0.0, 0.0)
    }
}
