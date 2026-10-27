package io.github.winddrift.fuckpangguai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun copyTextToClipboard(context: Context, label: String, text: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    return runCatching {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }.isSuccess
}
