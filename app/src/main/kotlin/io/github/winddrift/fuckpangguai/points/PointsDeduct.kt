package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.net.dto.IntegralLimitRuleDto

enum class PointsDeductReason(val label: String) {
    NoIntegral("没有可抵扣积分"),
    NotSupported("账号未开通积分自动抵扣"),
    Risk("账号需要验证后才能使用积分"),
    Error("积分抵扣检查失败"),
}

sealed interface PointsDeductEligibility {
    data class Available(val integralTotal: Int) : PointsDeductEligibility
    data class Unavailable(val reason: PointsDeductReason) : PointsDeductEligibility

    val usable: Boolean get() = this is Available
}

interface PointsDeductSource {
    suspend fun loadEligibility(): PointsDeductEligibility
}

object NoopPointsDeductSource : PointsDeductSource {
    override suspend fun loadEligibility(): PointsDeductEligibility =
        PointsDeductEligibility.Unavailable(PointsDeductReason.Error)
}

object PointsDeductPolicy {
    /** 与官方收银台一致：有积分且 isUserIntegral 为真时才展示并允许“自动使用积分”。 */
    fun evaluateRule(rule: IntegralLimitRuleDto): PointsDeductEligibility = when {
        rule.integralTotal <= 0 -> PointsDeductEligibility.Unavailable(PointsDeductReason.NoIntegral)
        !rule.isUserIntegral -> PointsDeductEligibility.Unavailable(PointsDeductReason.NotSupported)
        else -> PointsDeductEligibility.Available(rule.integralTotal)
    }

    fun applyRisk(
        ruleResult: PointsDeductEligibility,
        risk: Boolean,
    ): PointsDeductEligibility = when {
        !ruleResult.usable -> ruleResult
        risk -> PointsDeductEligibility.Unavailable(PointsDeductReason.Risk)
        else -> ruleResult
    }
}