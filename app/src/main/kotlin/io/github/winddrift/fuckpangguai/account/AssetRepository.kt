package io.github.winddrift.fuckpangguai.account

import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.dto.ItemsList
import io.github.winddrift.fuckpangguai.net.dto.Ticket
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.net.dto.UserBalance
import io.github.winddrift.fuckpangguai.net.requireSuccess
import io.github.winddrift.fuckpangguai.net.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

data class AssetOverview(
    val localId: String,
    val mergedFromLocalId: String? = null,
    val balance: String = "0",
    val tokenCoin: Int = 0,
    val integral: String = "0",
    val tickets: List<Ticket> = emptyList(),
    val principalAmount: Int = 0,
    val presentAmount: Int = 0,
    val totalAmount: Int = 0,
    val ticketsComplete: Boolean = true,
    val profileLoaded: Boolean = false,
    val balanceLoaded: Boolean = false,
    val ticketsLoaded: Boolean = false,
    val error: String? = null,
)

class AssetRepository(
    private val api: QiekjApi,
    private val accountStore: AccountStore,
    private val accountRepository: AccountRepository,
) {
    suspend fun loadOverview(): AssetOverview = supervisorScope {
        val localId = accountStore.activeLocalId() ?: throw ApiException(-1, "未登录")

        val profileDeferred = async {
            runCatchingSuspend { accountRepository.refreshProfile() }
        }
        val balanceDeferred = async {
            runCatchingSuspend { loadBalance() }
        }

        val profileResult = profileDeferred.await()
        val balanceResult = balanceDeferred.await()
        val profile = profileResult.getOrNull()
        val balance = balanceResult.getOrNull()
        val effectiveLocalId = profile?.account?.localId ?: localId

        var tickets = emptyList<Ticket>()
        var ticketsComplete = true
        var ticketsLoaded = false
        var ticketsError: String? = null

        if (balance != null && balance.tokenCoin > 0) {
            runCatchingSuspend { loadAllTickets() }
                .onSuccess { page ->
                    tickets = page.items
                    ticketsComplete = page.complete
                    ticketsLoaded = true
                }
                .onFailure { ticketsError = it.userMessage() }
        } else if (balance != null) {
            ticketsLoaded = true
        }

        val totals = tickets.totals()
        val errors = listOfNotNull(
            balanceResult.exceptionOrNull()?.userMessage(),
            ticketsError,
        )

        AssetOverview(
            localId = effectiveLocalId,
            mergedFromLocalId = profile?.mergedFromLocalId,
            balance = balance?.balance ?: "0",
            tokenCoin = balance?.tokenCoin ?: 0,
            integral = balance?.integral ?: "0",
            tickets = tickets,
            principalAmount = totals.principal,
            presentAmount = totals.present,
            totalAmount = totals.total,
            ticketsComplete = ticketsComplete,
            profileLoaded = profile != null,
            balanceLoaded = balance != null,
            ticketsLoaded = ticketsLoaded,
            error = errors.firstOrNull(),
        )
    }

    suspend fun loadLogs(
        shopId: String,
        page: Int,
        pageSize: Int = DEFAULT_LOG_PAGE_SIZE,
    ): ItemsList<TicketLog> {
        val response = api.userTokenCoinLogs(
            mapOf(
                "shopId" to shopId,
                "page" to page.toString(),
                "pageSize" to pageSize.toString(),
            ),
        )
        requireSuccess(response.code, response.msg)
        return response.data ?: ItemsList()
    }

    private suspend fun loadBalance(): UserBalance {
        val response = api.userBalance(emptyMap())
        requireSuccess(response.code, response.msg)
        return response.data ?: throw ApiException(-1, "余额响应为空")
    }

    private suspend fun loadAllTickets(): TicketPage {
        var page = 1
        var total = 0
        var complete = true
        val items = mutableListOf<Ticket>()

        while (true) {
            val response = api.userTokenCoins(
                mapOf(
                    "page" to page.toString(),
                    "pageSize" to TICKET_PAGE_SIZE.toString(),
                ),
            )
            requireSuccess(response.code, response.msg)
            val data = response.data ?: ItemsList()
            if (page == 1) total = data.total
            items += data.items

            val reachedEnd = data.items.isEmpty() ||
                data.items.size < TICKET_PAGE_SIZE ||
                items.size >= total
            if (reachedEnd) break
            if (page >= MAX_TICKET_PAGES) {
                complete = items.size >= total
                break
            }
            page++
        }

        return TicketPage(items = items, complete = complete)
    }

    private data class TicketPage(
        val items: List<Ticket>,
        val complete: Boolean,
    )

    private companion object {
        const val TICKET_PAGE_SIZE = 20
        const val MAX_TICKET_PAGES = 25
        const val DEFAULT_LOG_PAGE_SIZE = 15
    }
}

private suspend inline fun <T> runCatchingSuspend(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(throwable)
}
