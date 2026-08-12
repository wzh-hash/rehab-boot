package com.dfrobot.rehab.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dfrobot.rehab.R

/** 短时长(分:秒),供训练中/历史详情使用。 */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** 解析今日总时长为(时, 分, 是否采用 时:分 显示)。 */
fun parseTotalDuration(millis: Long): TotalDurationParts {
    val totalMinutes = millis / 60_000
    val hours = (totalMinutes / 60).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return TotalDurationParts(hours = hours, minutes = minutes, useHoursMinutes = hours > 0)
}

data class TotalDurationParts(val hours: Int, val minutes: Int, val useHoursMinutes: Boolean)

/** 把 TotalDurationParts 渲染为本地化字符串(由 UI 层调用)。 */
@Composable
fun TotalDurationParts.localized(): String =
    if (useHoursMinutes) {
        stringResource(R.string.today_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.today_duration_minutes_only, minutes)
    }