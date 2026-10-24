package io.github.winddrift.fuckpangguai.account

import io.github.winddrift.fuckpangguai.net.dto.Ticket
import java.math.BigDecimal

data class TicketTotals(
    val total: Int,
    val principal: Int,
    val present: Int,
)

internal fun List<Ticket>.totals(): TicketTotals = TicketTotals(
    total = sumOf { it.totalAmount },
    principal = sumOf { it.principalAmount },
    present = sumOf { it.presentAmount },
)

internal fun pointsToYuan(points: Int): String =
    BigDecimal(points).movePointLeft(2).setScale(2).toPlainString()

internal fun formatBalance(balance: String): String =
    balance.trim().ifBlank { "0.00" }

internal fun formatIntegral(integral: String): String =
    integral.trim().ifBlank { "0" }

internal fun ticketLogSign(type: Int): String = if (type == 100) "+" else "-"

internal fun formatLogAmount(type: Int, amount: Int): String =
    ticketLogSign(type) + pointsToYuan(amount)
