package io.github.winddrift.fuckpangguai.core

import android.content.Context
import android.content.Intent
import android.net.Uri

fun Context.openExternalUrl(url: String): Boolean = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}.isSuccess
