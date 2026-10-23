package io.github.winddrift.fuckpangguai.water

object SettlementDecider {

    sealed interface Decision {
        data object NoCharge : Decision
        data object AlreadyAttempted : Decision
        data class CreateOrder(val identify: String) : Decision
    }

    fun decide(session: WaterSession): Decision {
        val amount = session.amount.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0 || session.identify.isBlank()) return Decision.NoCharge
        if (session.settleAttempted) return Decision.AlreadyAttempted
        return Decision.CreateOrder(session.identify)
    }
}
