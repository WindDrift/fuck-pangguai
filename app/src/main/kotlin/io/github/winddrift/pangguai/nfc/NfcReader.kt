package io.github.winddrift.pangguai.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.tech.Ndef
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun NfcReader(onPayload: (NfcPayload) -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val latestOnPayload = rememberUpdatedState(onPayload)

    DisposableEffect(activity, adapter) {
        if (activity == null || adapter == null) {
            return@DisposableEffect onDispose { }
        }

        var lastUrl: String? = null
        var lastAt = 0L
        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            val rawUrl = readNdefUrl(tag)
            val now = System.currentTimeMillis()
            if (rawUrl != null && rawUrl == lastUrl && now - lastAt < 2_000) {
                return@ReaderCallback
            }
            lastUrl = rawUrl
            lastAt = now
            val payload = NfcPayloadParser.parse(rawUrl.orEmpty(), tag.id?.toHex())
            activity.runOnUiThread { latestOnPayload.value(payload) }
        }

        val lifecycleOwner = activity as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching {
                    adapter.enableReaderMode(activity, callback, READER_FLAGS, null)
                }
                Lifecycle.Event.ON_PAUSE -> runCatching {
                    adapter.disableReaderMode(activity)
                }
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)

        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            runCatching { adapter.disableReaderMode(activity) }
        }
    }
}

private fun readNdefUrl(tag: Tag): String? {
    val ndef = Ndef.get(tag) ?: return null
    val message = ndef.cachedNdefMessage ?: ndef.ndefMessage ?: return null
    return message.records.firstNotNullOfOrNull { record ->
        runCatching { record.toUri()?.toString() }.getOrNull()
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private const val READER_FLAGS =
    NfcAdapter.FLAG_READER_NFC_A or
        NfcAdapter.FLAG_READER_NFC_B or
        NfcAdapter.FLAG_READER_NFC_F or
        NfcAdapter.FLAG_READER_NFC_V
