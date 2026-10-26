package io.github.winddrift.fuckpangguai.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class NfcAvailabilityTest {

    @Test
    fun `missing adapter is unsupported`() {
        assertEquals(NfcAvailability.Unsupported, resolveNfcAvailability(false, false))
        assertEquals(NfcAvailability.Unsupported, resolveNfcAvailability(false, true))
    }

    @Test
    fun `existing adapter with nfc off is disabled`() {
        assertEquals(NfcAvailability.Disabled, resolveNfcAvailability(true, false))
    }

    @Test
    fun `existing adapter with nfc on is enabled`() {
        assertEquals(NfcAvailability.Enabled, resolveNfcAvailability(true, true))
    }
}
