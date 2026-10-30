package io.github.winddrift.fuckpangguai.net.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class SignInActDto(
    val id: String = "",
    val status: Int = 0,
)

@Serializable
data class TodaySignInDto(
    val isSignIn: Boolean = false,
)

@Serializable
data class IntegralTaskDto(
    val taskCode: String = "",
    val taskType: JsonElement = JsonNull,
    val title: String = "",
    val subTitle: String = "",
    val awardType: Int = 0,
    val awardNumber: Int = 0,
    val completedStatus: Int = 0,
    val completedFreq: Int = 0,
    val dailyTaskLimit: Int = 0,
    val totalTaskLimit: Int = 0,
    val status: Int = 0,
    val jumpLink: String = "",
    val extendMap: JsonElement? = null,
    val subtaskList: List<SubtaskDto> = emptyList(),
)

@Serializable
data class IntegralLimitRuleDto(
    val integralTotal: Int = 0,
    val isUserIntegral: Boolean = false,
    val minUsageCount: Int = 0,
)

@Serializable
data class SubtaskDto(
    val taskCode: String = "",
    val subtaskCode: String = "",
    val subtaskName: String = "",
    val awardNumber: Int = 0,
    val completedStatus: Int = 0,
    val completedFreq: Int = 0,
    val dailyTaskLimit: Int = 0,
    val totalTaskLimit: Int = 0,
)
