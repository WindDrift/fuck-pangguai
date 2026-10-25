package io.github.winddrift.fuckpangguai.ticket

import io.github.winddrift.fuckpangguai.account.AccountStore
import io.github.winddrift.fuckpangguai.account.StoredAccount
import kotlinx.coroutines.flow.Flow

interface TicketAccountSource {
    val activeAccount: Flow<StoredAccount?>
    fun activeLocalId(): String?
}

class AccountStoreTicketSource(
    private val accountStore: AccountStore,
) : TicketAccountSource {
    override val activeAccount: Flow<StoredAccount?> = accountStore.activeAccount
    override fun activeLocalId(): String? = accountStore.activeLocalId()
}
