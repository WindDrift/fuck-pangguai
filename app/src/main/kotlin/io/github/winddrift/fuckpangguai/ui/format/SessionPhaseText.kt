package io.github.winddrift.fuckpangguai.ui.format

import io.github.winddrift.fuckpangguai.water.SessionPhase

fun sessionPhaseLabel(phase: String): String = when (phase) {
    SessionPhase.USING -> "接水中"
    SessionPhase.ENDING -> "正在结束"
    SessionPhase.SETTLING -> "正在结算"
    SessionPhase.PAID -> "已支付"
    SessionPhase.FINISHED_NO_CHARGE -> "已结束（无费用）"
    SessionPhase.PAY_UNKNOWN -> "支付状态待确认"
    SessionPhase.FAULT -> "设备异常"
    else -> phase
}

fun sessionStatusLabel(phase: String): String =
    if (phase == SessionPhase.USING) "接水进行中" else sessionPhaseLabel(phase)
