package io.github.winddrift.fuckpangguai.nfc

import android.content.Context
import android.content.Intent
import android.provider.Settings

fun openNfcSettings(context: Context): Boolean = runCatching {
    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
}.isSuccess
