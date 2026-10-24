package io.github.winddrift.fuckpangguai.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    val userName: String = "",
    val phone: String = "",
    val headImageId: String = "",
)

@Serializable
data class UserBalance(
    val balance: String = "0",
    val tokenCoin: Int = 0,
    val integral: String = "0",
    val shopTokenCoinSet: Boolean = false,
)

@Serializable
data class ItemsList<T>(
    val total: Int = 0,
    val items: List<T> = emptyList(),
)

@Serializable
data class Ticket(
    val id: String = "",
    val shopId: String = "",
    val shopName: String = "",
    val totalAmount: Int = 0,
    val principalAmount: Int = 0,
    val presentAmount: Int = 0,
)

@Serializable
data class TicketLog(
    val id: Int = 0,
    val shopId: String = "",
    val shopName: String = "",
    val type: Int = 0,
    val subType: Int = 0,
    val amount: Int = 0,
    val principalAmount: Int = 0,
    val presentAmount: Int = 0,
    val afterAmount: Int = 0,
    val remark: String = "",
    val createdAt: String = "",
)
