package io.github.winddrift.fuckpangguai.diagnostics.model

import kotlinx.serialization.Serializable

@Serializable
enum class DiagTimeRange(
    val label: String,
    val millis: Long?,
) {
    Last15Minutes("15 分钟", 15 * 60_000L),
    LastHour("1 小时", 60 * 60_000L),
    Last24Hours("24 小时", 24 * 60 * 60_000L),
    Last7Days("7 天", 7 * 24 * 60 * 60_000L),
    All("全部", null),
}

@Serializable
data class EventFilter(
    val query: String = "",
    val levels: Set<DiagLevel> = emptySet(),
    val flows: Set<DiagFlow> = emptySet(),
    val range: DiagTimeRange = DiagTimeRange.All,
    val limit: Int = DEFAULT_LIMIT,
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || levels.isNotEmpty() || flows.isNotEmpty() ||
            range != DiagTimeRange.All

    companion object {
        const val DEFAULT_LIMIT = 200
        const val PAGE_SIZE = 200
    }
}

@Serializable
data class TraceFilter(
    val deviceQuery: String = "",
    val outcomes: Set<TraceOutcome> = emptySet(),
    val range: DiagTimeRange = DiagTimeRange.All,
) {
    val hasActiveFilters: Boolean
        get() = deviceQuery.isNotBlank() || outcomes.isNotEmpty() || range != DiagTimeRange.All
}

fun List<DiagEvent>.applyFilter(
    filter: EventFilter,
    now: Long,
): List<DiagEvent> {
    val query = filter.query.trim()
    val from = filter.range.millis?.let { now - it }
    return asSequence()
        .filter { from == null || it.at >= from }
        .filter { filter.levels.isEmpty() || it.level in filter.levels }
        .filter { filter.flows.isEmpty() || it.flow in filter.flows }
        .filter { event ->
            query.isBlank() || event.searchText().contains(query, ignoreCase = true)
        }
        .take(filter.limit.coerceAtLeast(0))
        .toList()
}

fun List<WaterTrace>.applyFilter(
    filter: TraceFilter,
    now: Long,
): List<WaterTrace> {
    val query = filter.deviceQuery.trim()
    val from = filter.range.millis?.let { now - it }
    return asSequence()
        .filter { from == null || it.startedAt >= from }
        .filter { filter.outcomes.isEmpty() || it.outcome in filter.outcomes }
        .filter { trace ->
            query.isBlank() ||
                trace.deviceName.contains(query, ignoreCase = true) ||
                trace.goodsId.contains(query, ignoreCase = true) ||
                trace.traceId.contains(query, ignoreCase = true)
        }
        .toList()
}

private fun DiagEvent.searchText(): String = buildString {
    append(name)
    append(' ')
    append(message)
    append(' ')
    append(flow.label)
    append(' ')
    append(traceId)
    if (fields.isNotEmpty()) {
        append(' ')
        append(fields.entries.joinToString(" ") { "${it.key}=${it.value}" })
    }
}
