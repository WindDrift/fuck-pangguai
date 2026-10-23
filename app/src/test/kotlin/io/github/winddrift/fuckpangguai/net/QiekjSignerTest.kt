package io.github.winddrift.fuckpangguai.net

import org.junit.Assert.assertEquals
import org.junit.Test

class QiekjSignerTest {

    @Test
    fun `normal sign matches known vector`() {
        assertEquals(
            "8628cf5eaf096ae7c0572cce41c2e8b13a2f94af78e981abbb7351b8d41633df",
            QiekjSigner.sign(
                token = "test-token",
                timestamp = "1700000000000",
                encodedPath = "/goods/water/unlock",
            ),
        )
    }

    @Test
    fun `risk sign includes location headers and same timestamp`() {
        assertEquals(
            "2bfe1b76f28b0dd6f1b6d5eb7e7250026c940d71f1020f2edf18aae0822b5a32",
            QiekjSigner.riskSign(
                token = "test-token",
                timestamp = "1700000000000",
                encodedPath = "/goods/water/unlock",
                categoryCode = "04",
                imei = "860456070165314",
                lat = "30.123456",
                lng = "120.123456",
            ),
        )
    }
}
