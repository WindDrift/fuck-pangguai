package io.github.winddrift.fuckpangguai.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrParserTest {

    @Test
    fun `parses SN from h5 url`() {
        val result = QrParser.parse("https://h5.qiekj.com/skip?SN=860456070165314")
        assertEquals(QrTarget.Device("SN", "860456070165314"), result)
    }

    @Test
    fun `prefers NQT over IMEI and SN`() {
        val result = QrParser.parse("https://example.com/scan?SN=sn-1&IMEI=imei-1&NQT=nqt-1")
        assertEquals(QrTarget.Device("NQT", "nqt-1"), result)
    }

    @Test
    fun `parses raw key value`() {
        val result = QrParser.parse("SN=860456070165314")
        assertEquals(QrTarget.Device("SN", "860456070165314"), result)
    }

    @Test
    fun `rejects unrelated qr`() {
        assertTrue(QrParser.parse("https://example.com/hello") is QrTarget.Unsupported)
    }
}
