package io.github.winddrift.fuckpangguai.points

import io.github.winddrift.fuckpangguai.net.ApiException
import retrofit2.HttpException
import java.io.IOException

/**
 * 判断一次任务请求失败是否属于"网络/限流"类错误。
 *
 * Retrofit 对非 2xx 抛 [HttpException]，业务失败抛 [ApiException]；只有网络与限流
 * （405/408/425/429/5xx）才计入连续网络错误并触发熔断，登录失效与验证码不在此列。
 */
internal fun isNetworkFailure(throwable: Throwable): Boolean = when (throwable) {
    is IOException -> true
    is HttpException -> throwable.code() in RATE_LIMIT_CODES || throwable.code() in 500..599
    is ApiException -> throwable.code in 500..599
    else -> false
}

/** 405 在服务端表示"本轮任务已不可完成"，与限流同等处理。 */
private val RATE_LIMIT_CODES = setOf(405, 408, 425, 429)