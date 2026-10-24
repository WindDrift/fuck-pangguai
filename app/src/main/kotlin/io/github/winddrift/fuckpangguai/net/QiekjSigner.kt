package io.github.winddrift.fuckpangguai.net

import io.github.winddrift.fuckpangguai.core.AppConfig
import java.security.MessageDigest

object QiekjSigner {

    fun sign(
        token: String,
        timestamp: String,
        encodedPath: String,
        versionName: String = AppConfig.QIEKJ_VERSION_NAME,
        appSecret: String = AppConfig.APP_SECRET,
    ): String = sha256Hex(
        "appSecret=$appSecret" +
            "&channel=${AppConfig.CHANNEL}" +
            "&timestamp=$timestamp" +
            "&token=$token" +
            "&version=$versionName" +
            "&$encodedPath",
    )

    fun riskSign(
        token: String,
        timestamp: String,
        encodedPath: String,
        categoryCode: String,
        imei: String,
        lat: String,
        lng: String,
        versionName: String = AppConfig.QIEKJ_VERSION_NAME,
        locationSecret: String = AppConfig.LOCATION_SECRET,
    ): String = sha256Hex(
        "appSecret=$locationSecret" +
            "&categoryCode=$categoryCode" +
            "&channel=${AppConfig.CHANNEL}" +
            "&imei=$imei" +
            "&lat=$lat" +
            "&lng=$lng" +
            "&timestamp=$timestamp" +
            "&token=$token" +
            "&version=$versionName" +
            "&$encodedPath",
    )

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                append(HEX[(byte.toInt() ushr 4) and 0x0f])
                append(HEX[byte.toInt() and 0x0f])
            }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
