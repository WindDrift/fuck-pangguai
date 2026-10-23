package io.github.winddrift.fuckpangguai.core

import java.net.URLDecoder

object UrlQuery {

    fun queryOf(raw: String): String {
        val trimmed = raw.trim()
        return when {
            '?' in trimmed -> trimmed.substringAfter('?')
            '=' in trimmed && "://" !in trimmed -> trimmed
            else -> ""
        }
    }

    fun parse(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery
            .split('&')
            .filter { it.isNotBlank() }
            .associate { part ->
                val index = part.indexOf('=')
                val key = if (index >= 0) part.substring(0, index) else part
                val value = if (index >= 0) part.substring(index + 1) else ""
                decode(key) to decode(value)
            }
    }

    fun firstIgnoreCase(values: Map<String, String>, key: String): String? =
        values.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
