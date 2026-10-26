package io.github.winddrift.fuckpangguai.nfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class NfcAvailability {
    Unsupported,
    Disabled,
    Enabled,
}

internal fun resolveNfcAvailability(
    adapterAvailable: Boolean,
    enabled: Boolean,
): NfcAvailability = when {
    !adapterAvailable -> NfcAvailability.Unsupported
    enabled -> NfcAvailability.Enabled
    else -> NfcAvailability.Disabled
}

@Composable
fun rememberNfcAvailability(): NfcAvailability {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current
    val adapter = remember(appContext) { NfcAdapter.getDefaultAdapter(appContext) }
    var availability by remember(adapter) {
        mutableStateOf(adapter.availability())
    }

    DisposableEffect(appContext, lifecycleOwner, adapter) {
        if (adapter == null) {
            return@DisposableEffect onDispose { }
        }

        fun refresh() {
            availability = adapter.availability()
        }

        // 只读取适配器的真实状态，不信任广播内容。
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                refresh()
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            appContext.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return availability
}

private fun NfcAdapter?.availability(): NfcAvailability =
    resolveNfcAvailability(adapterAvailable = this != null, enabled = this?.isEnabled == true)
