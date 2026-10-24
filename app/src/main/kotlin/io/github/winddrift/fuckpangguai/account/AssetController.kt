package io.github.winddrift.fuckpangguai.account

import io.github.winddrift.fuckpangguai.net.dto.ItemsList
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.storage.AppSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountAssetState(
    val accountId: String? = null,
    val snapshot: AssetSnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val hidden: Boolean = false,
)

class AssetController(
    private val scope: CoroutineScope,
    private val accountStore: AccountStore,
    private val assetRepository: AssetRepository,
    private val snapshotStore: AssetSnapshotStore,
    private val settingsStore: AppSettingsStore,
) {
    private val _state = MutableStateFlow(AccountAssetState())
    val state = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        scope.launch {
            accountStore.activeAccount
                .map { account -> account?.let { it.localId to it.invalid } }
                .distinctUntilChanged()
                .collect { active ->
                    refreshJob?.cancel()
                    if (active == null) {
                        _state.update {
                            AccountAssetState(hidden = it.hidden)
                        }
                        return@collect
                    }
                    val (localId, invalid) = active
                    _state.update {
                        it.copy(
                            accountId = localId,
                            snapshot = null,
                            loading = !invalid,
                            error = null,
                        )
                    }
                    val snapshot = snapshotStore.load(localId)
                    _state.update {
                        it.copy(snapshot = snapshot, loading = false)
                    }
                    if (!invalid) refresh()
                }
        }
        scope.launch {
            settingsStore.assetsHidden.collect { hidden ->
                _state.update { it.copy(hidden = hidden) }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val localId = accountStore.activeLocalId() ?: return@launch
            _state.update { it.copy(loading = true, error = null) }
            try {
                val overview = assetRepository.loadOverview()
                val currentActive = accountStore.activeLocalId()
                if (currentActive != localId && currentActive != overview.localId) {
                    return@launch
                }

                val previous = snapshotStore.load(overview.mergedFromLocalId ?: overview.localId)
                val snapshot = mergeSnapshot(previous, overview)
                snapshotStore.save(overview.localId, snapshot)
                overview.mergedFromLocalId?.let { snapshotStore.delete(it) }

                _state.update {
                    it.copy(
                        accountId = overview.localId,
                        snapshot = snapshot,
                        loading = false,
                        error = overview.error,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(loading = false, error = throwable.userMessage())
                }
            }
        }
    }

    fun toggleHidden() {
        scope.launch {
            settingsStore.setAssetsHidden(!_state.value.hidden)
        }
    }

    suspend fun loadLogs(
        shopId: String,
        page: Int,
        pageSize: Int = 15,
    ): ItemsList<TicketLog> = assetRepository.loadLogs(shopId, page, pageSize)

    private fun mergeSnapshot(previous: AssetSnapshot?, overview: AssetOverview): AssetSnapshot =
        AssetSnapshot(
            fetchedAt = System.currentTimeMillis(),
            balance = if (overview.balanceLoaded) overview.balance else previous?.balance.orEmpty(),
            tokenCoin = if (overview.balanceLoaded) overview.tokenCoin else previous?.tokenCoin ?: 0,
            integral = if (overview.balanceLoaded) overview.integral else previous?.integral.orEmpty(),
            tickets = if (overview.ticketsLoaded) overview.tickets else previous?.tickets.orEmpty(),
            principalAmount = if (overview.ticketsLoaded) {
                overview.principalAmount
            } else {
                previous?.principalAmount ?: 0
            },
            presentAmount = if (overview.ticketsLoaded) {
                overview.presentAmount
            } else {
                previous?.presentAmount ?: 0
            },
            totalAmount = if (overview.ticketsLoaded) {
                overview.totalAmount
            } else {
                previous?.totalAmount ?: 0
            },
            ticketsComplete = if (overview.ticketsLoaded) {
                overview.ticketsComplete
            } else {
                previous?.ticketsComplete ?: true
            },
        )
}
