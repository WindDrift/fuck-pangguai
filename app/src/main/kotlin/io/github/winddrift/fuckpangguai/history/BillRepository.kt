package io.github.winddrift.fuckpangguai.history

import io.github.winddrift.fuckpangguai.net.ApiException
import io.github.winddrift.fuckpangguai.net.QiekjApi
import io.github.winddrift.fuckpangguai.net.dto.BillOrderDto
import io.github.winddrift.fuckpangguai.net.dto.TicketLog
import io.github.winddrift.fuckpangguai.net.requireSuccess
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BillRepository(private val api: QiekjApi) {

    suspend fun loadDetailByOrderNo(orderNo: String): BillOrderDto {
        if (orderNo.isBlank()) throw ApiException(-1, "订单号为空")
        return loadDetail(mapOf("orderNo" to orderNo))
    }

    suspend fun loadDetailByOrderId(orderId: String): BillOrderDto {
        if (orderId.isBlank()) throw ApiException(-1, "订单 ID 为空")
        return loadDetail(mapOf("orderId" to orderId))
    }

    suspend fun loadRecentOrders(sinceMillis: Long): List<BillOrderDto> {
        val result = mutableListOf<BillOrderDto>()
        var page = 1
        while (page <= MAX_ORDER_PAGES) {
            val response = api.orderList(
                mapOf(
                    "page" to page.toString(),
                    "pageSize" to ORDER_PAGE_SIZE.toString(),
                    "orderStatus" to "",
                ),
            )
            requireSuccess(response.code, response.msg)
            val data = response.data ?: break
            if (data.items.isEmpty()) break

            val pageItems = data.items
            result += if (sinceMillis <= 0) {
                pageItems
            } else {
                pageItems.filter { item ->
                    val time = item.createTime.toEpochMillis()
                    time == null || time >= sinceMillis
                }
            }

            val reachedEnd = data.items.size < ORDER_PAGE_SIZE ||
                (data.total > 0 && result.size >= data.total)
            val pageIsOld = sinceMillis > 0 && pageItems.all { item ->
                val time = item.createTime.toEpochMillis()
                time != null && time < sinceMillis
            }
            if (reachedEnd || pageIsOld) break
            page++
        }
        return result
    }

    suspend fun loadTicketLogs(shopId: String): List<TicketLog> {
        if (shopId.isBlank()) return emptyList()
        val result = mutableListOf<TicketLog>()
        var page = 1
        while (page <= MAX_LOG_PAGES) {
            val response = api.userTokenCoinLogs(
                mapOf(
                    "shopId" to shopId,
                    "page" to page.toString(),
                    "pageSize" to LOG_PAGE_SIZE.toString(),
                ),
            )
            requireSuccess(response.code, response.msg)
            val data = response.data ?: break
            if (data.items.isEmpty()) break
            result += data.items
            val reachedEnd = data.items.size < LOG_PAGE_SIZE ||
                (data.total > 0 && result.size >= data.total)
            if (reachedEnd) break
            page++
        }
        return result
    }

    private suspend fun loadDetail(body: Map<String, String>): BillOrderDto {
        val response = api.orderDetail(body)
        requireSuccess(response.code, response.msg)
        val data = response.data ?: throw ApiException(-1, "账单响应为空")
        if (data.id.isBlank() && data.orderNo.isBlank()) {
            throw ApiException(-1, "未找到对应账单")
        }
        return data
    }

    private fun String.toEpochMillis(): Long? {
        val value = trim()
        if (value.isBlank()) return null
        return runCatching {
            LocalDateTime.parse(value, TIME_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private companion object {
        const val ORDER_PAGE_SIZE = 20
        const val MAX_ORDER_PAGES = 30
        const val LOG_PAGE_SIZE = 15
        const val MAX_LOG_PAGES = 12

        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
