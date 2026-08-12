package com.dfrobot.rehab.domain

import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.domain.model.TrainingSession

enum class SessionPhase { Idle, Training }

data class SessionStats(
    val elapsedMillis: Long,
    val ratio: TrainingRatio? = null,
)

/**
 * 训练会话状态机(纯类,无协程无 Android),事件驱动:
 * Idle →(start(ratio))→ Training →(3×onRepCompleted)finish 落库 → Idle。
 * 固件无暂停/停止指令,无压力数据。
 */
class SessionTracker(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val stepProvider: () -> Int = { 0 },
) {
    var phase: SessionPhase = SessionPhase.Idle
        private set

    var repsCompleted: Int = 0
        private set

    var stats: SessionStats = SessionStats(0)
        private set

    private var startMillis = 0L
    private var baseSteps = 0

    fun start(ratio: TrainingRatio) {
        check(phase == SessionPhase.Idle) { "会话已在进行中" }
        phase = SessionPhase.Training
        repsCompleted = 0
        startMillis = nowMillis()
        baseSteps = stepProvider()
        stats = SessionStats(0, ratio)
    }

    /** 固件发布一次 "plus" 即完成一次重复。 */
    fun onRepCompleted() {
        check(phase == SessionPhase.Training) { "没有进行中的会话" }
        repsCompleted += 1
        stats = stats.copy(elapsedMillis = elapsedSince(startMillis))
    }

    /** 刷新计时(无事件时由 UI 周期调用,保证时长持续走动)。 */
    fun tick() {
        if (phase == SessionPhase.Training) {
            stats = stats.copy(elapsedMillis = elapsedSince(startMillis))
        }
    }

    /** 结束会话;completed = 3 次重复全部完成(超时/中断则为 false)。 */
    fun finish(): TrainingSession {
        check(phase == SessionPhase.Training) { "没有进行中的会话" }
        val ratio = stats.ratio ?: TrainingRatio.T25
        val session = TrainingSession(
            startTimeMillis = startMillis,
            endTimeMillis = nowMillis(),
            durationMillis = elapsedSince(startMillis),
            ratio = ratio,
            repsCompleted = repsCompleted,
            completed = repsCompleted >= 3,
            steps = (stepProvider() - baseSteps).coerceAtLeast(0),
        )
        reset()
        return session
    }

    private fun elapsedSince(since: Long): Long =
        (nowMillis() - since).coerceAtLeast(0L)

    private fun reset() {
        phase = SessionPhase.Idle
        repsCompleted = 0
        startMillis = 0L
        baseSteps = 0
        stats = SessionStats(0)
    }
}
