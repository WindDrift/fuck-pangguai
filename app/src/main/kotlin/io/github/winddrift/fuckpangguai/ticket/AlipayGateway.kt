package io.github.winddrift.fuckpangguai.ticket

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.alipay.sdk.app.PayTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AlipayOutcome(
    val resultStatus: String,
    val memo: String? = null,
)

fun interface AlipayGateway {
    suspend fun pay(orderInfo: String): AlipayOutcome
}

class AlipaySdkGateway(private val activity: Activity) : AlipayGateway {
    override suspend fun pay(orderInfo: String): AlipayOutcome = withContext(Dispatchers.IO) {
        try {
            val result = PayTask(activity).payV2(orderInfo, true) ?: emptyMap()
            AlipayOutcome(
                resultStatus = result["resultStatus"].orEmpty(),
                memo = result["memo"],
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AlipayOutcome(resultStatus = RESULT_SDK_ERROR, memo = throwable.message)
        }
    }

    companion object {
        const val RESULT_SDK_ERROR = "SDK_ERROR"
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
