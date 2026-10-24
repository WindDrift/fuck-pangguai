package io.github.winddrift.fuckpangguai.scan

import io.github.winddrift.fuckpangguai.nfc.NfcPayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStateTest {

    @Test
    fun `accepts camera scan when idle`() {
        assertTrue(ScanState.Idle.acceptsCameraScan())
    }

    @Test
    fun `accepts camera scan while waiting for nfc binding`() {
        val payload = NfcPayload(
            rawUrl = "alipays://platformapi/startapp?appId=test&t=tag-1",
            uid = null,
            tagId = "tag-1",
            alipayServiceId = "test",
            sn = null,
            nqt = null,
            imei = null,
        )

        assertTrue(ScanState.NeedsBind("tag-1", payload).acceptsCameraScan())
    }

    @Test
    fun `ignores camera scan while resolving or showing a message`() {
        assertFalse(ScanState.Resolving(ScanSource.Qr).acceptsCameraScan())
        assertFalse(ScanState.Message("无法识别该二维码").acceptsCameraScan())
    }
}
