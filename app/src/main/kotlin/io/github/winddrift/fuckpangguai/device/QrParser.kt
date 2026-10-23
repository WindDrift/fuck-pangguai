package io.github.winddrift.fuckpangguai.device

import io.github.winddrift.fuckpangguai.core.UrlQuery

sealed interface QrTarget {
    data class Device(val key: String, val value: String) : QrTarget
    data class Unsupported(val raw: String) : QrTarget
}

object QrParser {

    private val DEVICE_KEYS = listOf("NQT", "IMEI", "SN")

    fun parse(raw: String): QrTarget {
        val query = UrlQuery.queryOf(raw)
        if (query.isBlank()) return QrTarget.Unsupported(raw)
        val values = UrlQuery.parse(query)
        for (key in DEVICE_KEYS) {
            val value = UrlQuery.firstIgnoreCase(values, key)
            if (!value.isNullOrBlank()) {
                return QrTarget.Device(key, value)
            }
        }
        return QrTarget.Unsupported(raw)
    }
}
