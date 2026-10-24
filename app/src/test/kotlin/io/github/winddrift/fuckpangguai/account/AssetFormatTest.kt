package io.github.winddrift.fuckpangguai.account

import io.github.winddrift.fuckpangguai.net.dto.Ticket
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetFormatTest {

    @Test
    fun `points are formatted as yuan`() {
        assertEquals("12.34", pointsToYuan(1234))
        assertEquals("0.05", pointsToYuan(5))
        assertEquals("0.00", pointsToYuan(0))
    }

    @Test
    fun `empty balance and integral fall back to zero`() {
        assertEquals("0.00", formatBalance(""))
        assertEquals("12.30", formatBalance("12.30"))
        assertEquals("0", formatIntegral(""))
        assertEquals("42", formatIntegral("42"))
    }

    @Test
    fun `ticket totals sum all amount types`() {
        val totals = listOf(
            Ticket(totalAmount = 100, principalAmount = 80, presentAmount = 20),
            Ticket(totalAmount = 250, principalAmount = 200, presentAmount = 50),
        ).totals()

        assertEquals(350, totals.total)
        assertEquals(280, totals.principal)
        assertEquals(70, totals.present)
    }

    @Test
    fun `ticket log type 100 is credit and others are debit`() {
        assertEquals("+1.23", formatLogAmount(100, 123))
        assertEquals("-1.23", formatLogAmount(101, 123))
    }

    @Test
    fun `snapshot round trips with ticket list`() {
        val json = Json { ignoreUnknownKeys = true }
        val snapshot = AssetSnapshot(
            fetchedAt = 123L,
            balance = "1.00",
            tokenCoin = 250,
            integral = "9",
            tickets = listOf(
                Ticket(
                    id = "1",
                    shopId = "10",
                    shopName = "一号店",
                    totalAmount = 250,
                    principalAmount = 200,
                    presentAmount = 50,
                ),
            ),
            principalAmount = 200,
            presentAmount = 50,
            totalAmount = 250,
            ticketsComplete = false,
        )

        val decoded = json.decodeFromString<AssetSnapshot>(
            json.encodeToString(AssetSnapshot.serializer(), snapshot),
        )

        assertEquals(snapshot, decoded)
    }
}
