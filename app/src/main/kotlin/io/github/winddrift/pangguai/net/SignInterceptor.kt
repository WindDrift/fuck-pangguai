package io.github.winddrift.pangguai.net

import io.github.winddrift.pangguai.core.AppConfig
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response

class SignInterceptor(
    private val tokenProvider: () -> String,
    private val deviceId: String,
    private val phoneBrand: String,
    private val androidVersion: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider()
        val timestamp = System.currentTimeMillis().toString()
        val path = request.url.encodedPath
        val sign = QiekjSigner.sign(token = token, timestamp = timestamp, encodedPath = path)

        val builder = request.newBuilder()
            .header("Authorization", token)
            .header("Version", AppConfig.QIEKJ_VERSION_NAME)
            .header("channel", AppConfig.CHANNEL)
            .header("phoneBrand", phoneBrand)
            .header("deviceId", deviceId)
            .header("timestamp", timestamp)
            .header("sign", sign)
            .header("User-Agent", userAgent(androidVersion))

        if (path == "/goods/shower/unlock" ||
            path == "/goods/water/unlock" ||
            path == "/trade/create"
        ) {
            val categoryCode = request.header("categoryCode").orEmpty()
            val imei = request.header("imei").orEmpty()
            val lat = request.header("lat").orEmpty()
            val lng = request.header("lng").orEmpty()
            builder.header(
                "orderRiskSign",
                QiekjSigner.riskSign(
                    token = token,
                    timestamp = timestamp,
                    encodedPath = path,
                    categoryCode = categoryCode,
                    imei = imei,
                    lat = lat,
                    lng = lng,
                ),
            )
            builder.header("orderRiskTimestamp", timestamp)
        }

        val body = request.body
        if (body is FormBody) {
            val rebuilt = FormBody.Builder()
            for (index in 0 until body.size) {
                rebuilt.addEncoded(body.encodedName(index), body.encodedValue(index))
            }
            if (token.isNotEmpty()) {
                rebuilt.add("token", token)
            }
            builder.method(request.method, rebuilt.build())
        }

        return chain.proceed(builder.build())
    }

    private fun userAgent(androidVersion: String): String =
        "QEUser/${AppConfig.QIEKJ_VERSION_NAME}" +
            " (${AppConfig.USER_AGENT_PACKAGE}; build:${AppConfig.QIEKJ_BUILD};" +
            " Android $androidVersion; userChannel:${AppConfig.CHANNEL};" +
            " version:${AppConfig.QIEKJ_VERSION_NAME}) OkHttp/4.12.0"
}
