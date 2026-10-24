package io.github.winddrift.fuckpangguai.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QiekjSignerTest {

    @Test
    fun `normal sign matches known vector`() {
        assertEquals(
            "96f2085fe60f5d2c90e9cc5c6541d08530cefbe6c19e6cfb8c47a96cee415e25",
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
            "22453a1675dc8dd312727fb6d01352c9cc584b816567b127da786577bc3410ec",
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
