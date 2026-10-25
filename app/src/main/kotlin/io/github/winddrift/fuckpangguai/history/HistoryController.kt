package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.net.dto.BillOrderDto
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.net.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class HistoryUiState(
    val accountId: String? = null,
    val records: List<HistoryRecord> = emptyList(),
    val loading: Boolean = false,
    val syncing: Boolean = false,
    val syncingIds: Set<Long> = emptySet(),
    val pendingBillCount: Int = 0,
    val error: String? = null,
)

class HistoryController(
    private val scope: CoroutineScope,
    private val historyRepository: HistoryRepository,
    private val billRepository: BillRepository,
) {
    private val _state = MutableStateFlow(HistoryUiState())
    val state = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var generation = 0
    private val logCacheMutex = Mutex()

    fun refresh(accountId: String?, includeOld: Boolean = false) {
        if (accountId.isNullOrBlank()) {
            generation++
            refreshJob?.cancel()
            _state.value = HistoryUiState()
            return
        }
        refreshJob?.cancel()
        val currentGeneration = ++generation
        refreshJob = scope.launch {
            val previous = _state.value
            _state.value = if (previous.accountId == accountId) {
                previous.copy(
                    loading = true,
                    syncing = false,
                    syncingIds = emptySet(),
                    pendingBillCount = 0,
                    error = null,
                )
            } else {
                HistoryUiState(accountId = accountId, loading = true)
            }
            val records = try {
                historyRepository.list(accountId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (_state.value.accountId == accountId) {
                    _state.update {
                        it.copy(loading = false, error = throwable.userMessage())
                    }
                }
                return@launch
            }
            if (currentGeneration != generation || _state.value.accountId != accountId) return@launch
            _state.update { it.copy(records = records, loading = false) }
            syncBills(records, accountId, includeOld, currentGeneration)
        }
    }

    /** 打开详情时调用：只有尚无账单的记录才自动请求，旧记录也能按需匹配。 */
    fun ensureBill(recordId: Long) {
        val record = _state.value.records.firstOrNull { it.id == recordId } ?: return
        if (record.bill != null) return
        requestBill(record)
    }

    /** 用户主动重试，不检查缓存时间。 */
    fun retryBill(recordId: Long) {
        val record = _state.value.records.firstOrNull { it.id == recordId } ?: return
        requestBill(record)
    }

    suspend fun delete(record: HistoryRecord): Boolean {
        val result = runCatching { historyRepository.delete(record.id, record.accountId) }
        if (result.isSuccess) {
            _state.update { state ->
                if (state.accountId != record.accountId) {
                    state
                } else {
                    state.copy(
                        records = state.records.filterNot { it.id == record.id },
                        syncingIds = state.syncingIds - record.id,
                    )
                }
            }
        }
        return result.isSuccess
    }

    private fun requestBill(record: HistoryRecord) {
        if (_state.value.syncingIds.contains(record.id)) return
        val currentGeneration = generation
        scope.launch {
            syncOne(
                record = record,
                accountId = record.accountId,
                generation = currentGeneration,
                logCache = mutableMapOf(),
                recentOrders = null,
                fallbackError = null,
                allowFallback = true,
            )
        }
    }

    private suspend fun syncBills(
        records: List<HistoryRecord>,
        accountId: String,
        includeOld: Boolean,
        generation: Int,
    ) {
        if (generation != this.generation || _state.value.accountId != accountId) return
        val now = System.currentTimeMillis()
        val candidates = records.filter { it.shouldSyncBill(now, includeOld) }
        if (candidates.isEmpty()) return

        val limit = if (includeOld) MANUAL_SYNC_LIMIT else AUTO_SYNC_LIMIT
        val batch = candidates.take(limit)
        val pending = (candidates.size - batch.size).coerceAtLeast(0)

        var recentOrders: List<BillOrderDto>? = null
        var fallbackError: String? = null
        if (batch.any { it.billCandidateKeys().isEmpty() }) {
            try {
                recentOrders = billRepository.loadRecentOrders(
                    if (includeOld) 0 else now - BILL_AUTO_SYNC_WINDOW_MS,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                fallbackError = throwable.userMessage()
                recentOrders = emptyList()
            }
        }

        if (generation != this.generation || _state.value.accountId != accountId) return
        _state.update {
            if (it.accountId == accountId) {
                it.copy(syncing = true, pendingBillCount = pending, error = null)
            } else {
                it
            }
        }

        val logCache = mutableMapOf<String, TicketLogResult>()
        try {
            batch.chunked(SYNC_CONCURRENCY).forEach { chunk ->
                coroutineScope {
                    chunk.map { record ->
                        async {
                            syncOne(
                                record = record,
                                accountId = accountId,
                                generation = generation,
                                logCache = logCache,
                                recentOrders = recentOrders,
                                fallbackError = fallbackError,
                                allowFallback = false,
                            )
                        }
                    }.awaitAll()
                }
            }
        } finally {
            _state.update {
                if (generation == this.generation && it.accountId == accountId) {
                    it.copy(syncing = false)
                } else {
                    it
                }
            }
        }
    }

    private suspend fun syncOne(
        record: HistoryRecord,
        accountId: String,
        generation: Int,
        logCache: MutableMap<String, TicketLogResult>,
        recentOrders: List<BillOrderDto>?,
        fallbackError: String?,
        allowFallback: Boolean,
    ) {
        if (generation != this.generation || _state.value.accountId != accountId) return
        val current = _state.value.records.firstOrNull { it.id == record.id } ?: return
        _state.update {
            if (it.accountId == accountId) {
                it.copy(syncingIds = it.syncingIds + current.id)
            } else {
                it
            }
        }

        try {
            val resolution = resolveBill(record, recentOrders, fallbackError, allowFallback)
            if (generation != this.generation || _state.value.accountId != accountId) return
            when (resolution) {
                is BillResolution.Matched -> {
                    var bill = resolution.bill
                    if (bill.ticket != null) {
                        if (bill.shopId.isNotBlank()) {
                            val logs = ticketLogsFor(bill.shopId, logCache)
                            val keys = (record.billCandidateKeys() + listOf(bill.orderNo, bill.orderId))
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .toSet()
                            bill = bill.withTicketLogs(
                                logs = logs.logs.filter { it.matchesOrder(keys) },
                                logError = logs.error,
                            )
                        } else {
                            bill = bill.withTicketLogs(emptyList())
                        }
                    }
                    bill = bill.copy(fetchedAt = System.currentTimeMillis())
                    historyRepository.saveBill(current.id, accountId, bill)
                    updateRecord(accountId, current.id) {
                        it.copy(
                            billState = BillState.MATCHED,
                            bill = bill,
                            billError = "",
                            billFetchedAt = bill.fetchedAt,
                        )
                    }
                }

                is BillResolution.Unmatched -> {
                    val at = System.currentTimeMillis()
                    if (current.bill != null) {
                        historyRepository.markBillRefreshError(
                            current.id,
                            accountId,
                            resolution.message,
                            at,
                        )
                        updateRecord(accountId, current.id) {
                            it.copy(billError = resolution.message, billFetchedAt = at)
                        }
                    } else {
                        historyRepository.markBillUnmatched(
                            current.id,
                            accountId,
                            resolution.message,
                            at,
                        )
                        updateRecord(accountId, current.id) {
                            it.copy(
                                billState = BillState.UNMATCHED,
                                bill = null,
                                billError = resolution.message,
                                billFetchedAt = at,
                            )
                        }
                    }
                }

                is BillResolution.Failed -> {
                    val at = System.currentTimeMillis()
                    if (current.bill != null) {
                        historyRepository.markBillRefreshError(
                            current.id,
                            accountId,
                            resolution.message,
                            at,
                        )
                        updateRecord(accountId, current.id) {
                            it.copy(billError = resolution.message, billFetchedAt = at)
                        }
                    } else {
                        historyRepository.markBillError(
                            current.id,
                            accountId,
                            resolution.message,
                            at,
                        )
                        updateRecord(accountId, current.id) {
                            it.copy(
                                billState = BillState.ERROR,
                                billError = resolution.message,
                                billFetchedAt = at,
                            )
                        }
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val at = System.currentTimeMillis()
            val message = throwable.userMessage()
            if (current.bill != null) {
                runCatching {
                    historyRepository.markBillRefreshError(
                        current.id,
                        accountId,
                        message,
                        at,
                    )
                }
                updateRecord(accountId, current.id) {
                    it.copy(billError = message, billFetchedAt = at)
                }
            } else {
                runCatching {
                    historyRepository.markBillError(
                        current.id,
                        accountId,
                        message,
                        at,
                    )
                }
                updateRecord(accountId, current.id) {
                    it.copy(
                        billState = BillState.ERROR,
                        billError = message,
                        billFetchedAt = at,
                    )
                }
            }
        } finally {
            _state.update {
                if (generation == this.generation && it.accountId == accountId) {
                    it.copy(syncingIds = it.syncingIds - current.id)
                } else {
                    it
                }
            }
        }
    }

    private suspend fun resolveBill(
        record: HistoryRecord,
        recentOrders: List<BillOrderDto>?,
        fallbackError: String?,
        allowFallback: Boolean,
    ): BillResolution {
        val keys = record.billCandidateKeys().toSet()
        var lastMessage = ""
        var networkFailure = false

        suspend fun fetchByOrderNo(orderNo: String): BillResolution.Matched? {
            if (orderNo.isBlank()) return null
            return try {
                BillResolution.Matched(
                    billRepository.loadDetailByOrderNo(orderNo).toBillInfo(System.currentTimeMillis()),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                lastMessage = throwable.userMessage()
                if (throwable is IOException) networkFailure = true
                null
            }
        }

        suspend fun fetchByOrderId(orderId: String): BillResolution.Matched? {
            if (orderId.isBlank()) return null
            return try {
                BillResolution.Matched(
                    billRepository.loadDetailByOrderId(orderId).toBillInfo(System.currentTimeMillis()),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                lastMessage = throwable.userMessage()
                if (throwable is IOException) networkFailure = true
                null
            }
        }

        // 结算单号优先；只有完全没有结算信息时才退回解锁单号，避免把另一笔订单当成账单。
        val payOrderNo = record.payOrderNo.trim()
        val identify = record.identify.trim()
        val unlockOrderNo = record.orderNo.trim()
        val orderNumbers = buildList {
            when {
                payOrderNo.isNotBlank() -> add(payOrderNo)
                identify.isNotBlank() -> add(identify)
                unlockOrderNo.isNotBlank() -> add(unlockOrderNo)
            }
        }
        for (orderNo in orderNumbers) {
            fetchByOrderNo(orderNo)?.let { return it }
        }
        if (record.payOrderId.isNotBlank()) {
            fetchByOrderId(record.payOrderId)?.let { return it }
        }

        var orders = recentOrders
        var listError = fallbackError
        if (orders == null && allowFallback) {
            try {
                orders = billRepository.loadRecentOrders(0)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                listError = throwable.userMessage()
                orders = emptyList()
            }
        }

        if (orders != null) {
            val order = orders.firstOrNull { it.matchesKeys(keys) }
            if (order != null) {
                fetchByOrderId(order.id)?.let { return it }
                fetchByOrderNo(order.orderNo)?.let { return it }
            }
        }

        return when {
            networkFailure -> BillResolution.Failed(
                lastMessage.ifBlank { "网络异常，请稍后重试" },
            )
            listError != null && orders.isNullOrEmpty() -> BillResolution.Failed(listError)
            lastMessage.isNotBlank() -> BillResolution.Unmatched(lastMessage)
            else -> BillResolution.Unmatched("未找到对应账单")
        }
    }

    private suspend fun ticketLogsFor(
        shopId: String,
        cache: MutableMap<String, TicketLogResult>,
    ): TicketLogResult = logCacheMutex.withLock {
        cache[shopId]?.let { return@withLock it }
        val result = try {
            TicketLogResult(billRepository.loadTicketLogs(shopId), "")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            TicketLogResult(emptyList(), throwable.userMessage())
        }
        cache[shopId] = result
        result
    }

    private fun updateRecord(
        accountId: String,
        recordId: Long,
        transform: (HistoryRecord) -> HistoryRecord,
    ) {
        _state.update { state ->
            if (state.accountId != accountId) {
                state
            } else {
                state.copy(
                    records = state.records.map { record ->
                        if (record.id == recordId) transform(record) else record
                    },
                )
            }
        }
    }

    private data class TicketLogResult(
        val logs: List<TicketLog>,
        val error: String,
    )

    private sealed interface BillResolution {
        data class Matched(val bill: BillInfo) : BillResolution
        data class Unmatched(val message: String) : BillResolution
        data class Failed(val message: String) : BillResolution
    }

    private companion object {
        const val AUTO_SYNC_LIMIT = 30
        const val MANUAL_SYNC_LIMIT = 60
        const val SYNC_CONCURRENCY = 3
    }
}
