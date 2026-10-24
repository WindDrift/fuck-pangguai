package io.github.winddrift.fuckpangguai.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QiekjSignerTest {

    @Test
    fun `normal sign matches known vector`() {
        assertEquals(
            "162ae72e81057aa63d00b9cbfdd5e90d6949970cc131d44a060b89a0867466aa",
            QiekjSigner.sign(
                token = "test-token",
                timestamp = "1700000000000",
                encodedPath = "/goods/water/unlock",
                appSecret = "test-secret",
            ),
        )
    }

    @Test
    fun `risk sign includes location headers and same timestamp`() {
        assertEquals(
            "057de19e35175ed2c8193c813559faa4927e7324541daed606efc3bc7068092b",
            QiekjSigner.riskSign(
                token = "test-token",
                timestamp = "1700000000000",
                encodedPath = "/goods/water/unlock",
                categoryCode = "04",
                imei = "860456070165314",
                lat = "30.123456",
                lng = "120.123456",
                locationSecret = "test-secret",
            ),
        )
    }

    @Test
    fun `sign changes when secret changes`() {
        val first = QiekjSigner.sign(
            token = "test-token",
            timestamp = "1700000000000",
            encodedPath = "/goods/water/unlock",
            appSecret = "test-secret",
        )
        val second = QiekjSigner.sign(
            token = "test-token",
            timestamp = "1700000000000",
            encodedPath = "/goods/water/unlock",
            appSecret = "another-secret",
        )

        assertNotEquals(first, second)
    }
}
