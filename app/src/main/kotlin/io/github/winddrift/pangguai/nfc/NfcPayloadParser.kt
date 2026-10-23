package io.github.winddrift.pangguai.nfc

import io.github.winddrift.pangguai.core.UrlQuery

data class NfcPayload(
    val rawUrl: String,
    val uid: String?,
    val tagId: String?,
    val alipayServiceId: String?,
    val sn: String?,
    val nqt: String?,
    val imei: String?,
)

object NfcPayloadParser {

    fun parse(rawUrl: String, uid: String? = null): NfcPayload {
        val outerQuery = UrlQuery.queryOf(rawUrl)
        val outer = UrlQuery.parse(outerQuery)
        val encodedScheme = UrlQuery.firstIgnoreCase(outer, "scheme")
        val inner = encodedScheme
            ?.let(UrlQuery::decode)
            ?.let(UrlQuery::queryOf)
            ?.let(UrlQuery::parse)
            .orEmpty()

        fun first(vararg keys: String): String? {
            for (key in keys) {
                UrlQuery.firstIgnoreCase(outer, key)?.takeIf { it.isNotBlank() }?.let { return it }
                UrlQuery.firstIgnoreCase(inner, key)?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }

        return NfcPayload(
            rawUrl = rawUrl,
            uid = uid,
            tagId = first("t"),
            alipayServiceId = first("id"),
            sn = first("SN"),
            nqt = first("NQT"),
            imei = first("IMEI"),
        )
    }
}
