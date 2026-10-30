package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.net.dto.IntegralLimitRuleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsDeductPolicyTest {

    @Test
    fun `no balance is not deductible`() {
        val result = PointsDeductPolicy.evaluateRule(IntegralLimitRuleDto(integralTotal = 0, isUserIntegral = true))
        assertEquals(PointsDeductReason.NoIntegral, (result as PointsDeductEligibility.Unavailable).reason)
        assertFalse(result.usable)
    }

    @Test
    fun `account without automatic integral is not supported`() {
        val result = PointsDeductPolicy.evaluateRule(
            IntegralLimitRuleDto(integralTotal = 800, isUserIntegral = false, minUsageCount = 500),
        )
        assertEquals(PointsDeductReason.NotSupported, (result as PointsDeductEligibility.Unavailable).reason)
    }

    @Test
    fun `risk turns an available rule into unavailable`() {
        val rule = PointsDeductPolicy.evaluateRule(IntegralLimitRuleDto(integralTotal = 800, isUserIntegral = true))
        assertTrue(rule.usable)
        val risky = PointsDeductPolicy.applyRisk(rule, risk = true)
        assertEquals(PointsDeductReason.Risk, (risky as PointsDeductEligibility.Unavailable).reason)
    }

    @Test
    fun `available rule stays available without risk`() {
        val rule = PointsDeductPolicy.evaluateRule(IntegralLimitRuleDto(integralTotal = 800, isUserIntegral = true))
        assertEquals(rate(800), PointsDeductPolicy.applyRisk(rule, risk = false))
    }

    private fun rate(total: Int) = PointsDeductEligibility.Available(total)
}