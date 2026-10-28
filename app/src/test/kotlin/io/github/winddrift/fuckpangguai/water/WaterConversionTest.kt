package io.github.winddrift.fuckpangguai.water

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class WaterConversionTest {

    private val conversion = WaterConversion(
        usageBase = BigDecimal("100"),
        waterMl = BigDecimal("500"),
        cupMl = BigDecimal("250"),
    )

    @Test
    fun `pure usage converts to volume and cups`() {
        assertEquals("500 mL · 约 2 杯", formatConvertedWater("100", conversion))
        assertEquals("100 · 500 mL · 约 2 杯", formatAmountWithWater("100", conversion))
        assertEquals("250 mL · 约 1 杯", formatConvertedWater("50", conversion))
    }

    @Test
    fun `unit tagged amounts keep their original text and add cups`() {
        assertEquals("约 4.8 杯", formatConvertedWater("1.2L", conversion))
        assertEquals("1.2L · 约 4.8 杯", formatAmountWithWater("1.2L", conversion))
        assertEquals("约 1.2 杯", formatConvertedWater("300ml", conversion))
        assertEquals("300ml · 约 1.2 杯", formatAmountWithWater("300ml", conversion))
        assertEquals("约 4.8 杯", formatConvertedWater("1.2 升", conversion))
        assertEquals("约 1.2 杯", formatConvertedWater("300 毫升", conversion))
    }

    @Test
    fun `amounts without volume or usage are left untouched`() {
        assertNull(formatConvertedWater("30分钟", conversion))
        assertNull(formatConvertedWater("abc", conversion))
        assertEquals("30分钟", formatAmountWithWater("30分钟", conversion))
        assertEquals("", formatAmountWithWater("", conversion))
    }

    @Test
    fun `conversion disabled leaves original amount`() {
        assertNull(formatConvertedWater("100", null))
        assertEquals("1.2L", formatAmountWithWater("1.2L", null))
    }

    @Test
    fun `cup is optional`() {
        val withoutCup = WaterConversion(
            usageBase = BigDecimal("100"),
            waterMl = BigDecimal("500"),
            cupMl = null,
        )
        assertEquals("500 mL", formatConvertedWater("100", withoutCup))
        assertEquals("100 · 500 mL", formatAmountWithWater("100", withoutCup))
        assertNull(formatConvertedWater("1.2L", withoutCup))
        assertEquals("1.2L", formatAmountWithWater("1.2L", withoutCup))
    }

    @Test
    fun `volume formatting switches between ml and l`() {
        assertEquals("0 mL", formatWaterVolume(BigDecimal.ZERO))
        assertEquals("500 mL", formatWaterVolume(BigDecimal("500")))
        assertEquals("999 mL", formatWaterVolume(BigDecimal("999.4")))
        assertEquals("1 L", formatWaterVolume(BigDecimal("999.6")))
        assertEquals("1 L", formatWaterVolume(BigDecimal("1000")))
        assertEquals("1.25 L", formatWaterVolume(BigDecimal("1250")))
        assertEquals("4.2 L", formatWaterVolume(BigDecimal("4200")))
    }

    @Test
    fun `cup formatting trims zeros and marks tiny amounts`() {
        assertEquals("约 2 杯", formatCupCount(BigDecimal("500"), BigDecimal("250")))
        assertEquals("约 1.2 杯", formatCupCount(BigDecimal("300"), BigDecimal("250")))
        assertEquals("约 0.1 杯", formatCupCount(BigDecimal("25"), BigDecimal("250")))
        assertEquals("不足 0.1 杯", formatCupCount(BigDecimal("10"), BigDecimal("250")))
        assertEquals("约 0 杯", formatCupCount(BigDecimal.ZERO, BigDecimal("250")))
        assertNull(formatCupCount(BigDecimal("100"), BigDecimal.ZERO))
    }

    @Test
    fun `validation accepts complete rules and empty clear`() {
        val empty = validateWaterConversion("", " ", "")
        assertTrue(empty is WaterConversionValidation.Empty)

        val valid = validateWaterConversion("100", "500", "250")
        assertTrue(valid is WaterConversionValidation.Valid)
        valid as WaterConversionValidation.Valid
        assertEquals(0, valid.conversion.usageBase.compareTo(BigDecimal("100")))
        assertEquals(0, valid.conversion.waterMl.compareTo(BigDecimal("500")))
        assertEquals(0, valid.conversion.cupMl!!.compareTo(BigDecimal("250")))

        val withoutCup = validateWaterConversion("100", "500", "")
        assertTrue(withoutCup is WaterConversionValidation.Valid)
        assertNull((withoutCup as WaterConversionValidation.Valid).conversion.cupMl)
    }

    @Test
    fun `validation rejects partial zero negative exponent and comma input`() {
        assertTrue(validateWaterConversion("100", "", "") is WaterConversionValidation.Invalid)
        assertTrue(validateWaterConversion("", "500", "") is WaterConversionValidation.Invalid)
        assertTrue(validateWaterConversion("0", "500", "") is WaterConversionValidation.Invalid)
        assertTrue(validateWaterConversion("100", "0", "") is WaterConversionValidation.Invalid)
        assertTrue(validateWaterConversion("-1", "500", "") is WaterConversionValidation.Invalid)
        assertTrue(validateWaterConversion("1e3", "500", "") is WaterConversionValidation.Invalid)
        assertTrue(validateWaterConversion("1,5", "500", "") is WaterConversionValidation.Invalid)
        assertTrue(validateWaterConversion("100", "500", "0") is WaterConversionValidation.Invalid)
        assertTrue(validateWaterConversion("100", "500", "abc") is WaterConversionValidation.Invalid)
    }

    @Test
    fun `rule and preview summaries include cup when configured`() {
        assertEquals(
            "100 用量 = 500 mL · 一杯 250 mL",
            formatConversionRule(conversion),
        )
        assertEquals(
            "100 用量 ≈ 500 mL · 约 2 杯",
            formatConversionPreview(conversion),
        )
    }
}
