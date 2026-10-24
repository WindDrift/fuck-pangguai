package io.github.winddrift.fuckpangguai.core

import io.github.winddrift.fuckpangguai.BuildConfig

object AppConfig {
    const val API_BASE = "https://userapi.qiekj.com/"
    const val QIEKJ_VERSION_NAME = "1.144.0"
    const val QIEKJ_BUILD = 294
    const val CHANNEL = "android_app"
    val APP_SECRET: String = BuildConfig.QIEKJ_APP_SECRET
    val LOCATION_SECRET: String = BuildConfig.QIEKJ_LOCATION_SECRET
    const val WATER_CATEGORY = "04"
    const val USER_AGENT_PACKAGE = "com.qiekj.user"
    const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded;charset=UTF-8"
}
