package io.github.winddrift.pangguai.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NfcPayloadParserTest {

    private val alipayUrl =
        "https://render.alipay.com/p/s/ulink/ysj?s=dc&scheme=alipay%3A%2F%2Fnfc%2Fapp%3Fid%3D20002153%26t%3Dts022jc02a8h"

    @Test
    fun `parses alipay nfc t and service id`() {
        val payload = NfcPayloadParser.parse(alipayUrl, uid = "01020304")
        assertEquals("ts022jc02a8h", payload.tagId)
        assertEquals("20002153", payload.alipayServiceId)
        assertEquals("01020304", payload.uid)
        assertNull(payload.sn)
    }

    @Test
    fun `parses generic device url`() {
        val payload = NfcPayloadParser.parse("https://h5.qiekj.com/skip?SN=860456070165314")
        assertEquals("860456070165314", payload.sn)
        assertNull(payload.tagId)
    }
}
