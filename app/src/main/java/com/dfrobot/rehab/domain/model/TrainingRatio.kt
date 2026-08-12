package com.dfrobot.rehab.domain.model

/**
 * 训练负重比例(与掌控板 Mind+ 固件指令一一对应):
 * A=25% B=50% C=75% D=100%。
 */
enum class TrainingRatio(val code: String, val percent: Int) {
    T25("A", 25),
    T50("B", 50),
    T75("C", 75),
    T100("D", 100),
}
