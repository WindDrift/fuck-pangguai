package io.github.winddrift.fuckpangguai.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExpiryTest {

    @Test
    fun `official expiry codes are recognized`() {
        assertTrue(isSessionExpiredCode(2))
        assertTrue(isSessionExpiredCode(10))
        assertTrue(isSessionExpiredCode(126))
    }

    @Test
    fun `other business codes are not expiry`() {
        assertFalse(isSessionExpiredCode(0))
        assertFalse(isSessionExpiredCode(401))
        assertFalse(isSessionExpiredCode(5134))
        assertFalse(isSessionExpiredCode(1014))
    }

    @Test
    fun `auth endpoints are excluded`() {
        assertTrue(isAuthPath("/common/sms/sendCode"))
        assertTrue(isAuthPath("/user/reg"))
        assertTrue(isAuthPath("/user/checkLogin"))
        assertFalse(isAuthPath("/user/info"))
        assertFalse(shouldReportSessionExpiry("/user/info", ""))
        assertTrue(shouldReportSessionExpiry("/user/info", "token"))
    }
}
