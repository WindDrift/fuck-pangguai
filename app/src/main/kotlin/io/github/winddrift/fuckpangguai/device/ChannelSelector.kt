package io.github.winddrift.fuckpangguai.device

import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute

object ChannelSelector {
    fun selectIndex(channels: List<PayChannelRoute>, lastMethod: Int?): Int {
        if (channels.isEmpty()) return -1
        lastMethod?.let { method ->
            val index = channels.indexOfFirst { it.method == method }
            if (index >= 0) return index
        }
        val checked = channels.indexOfFirst { it.checked }
        if (checked >= 0) return checked
        val default = channels.indexOfFirst { it.defaultFlag }
        if (default >= 0) return default
        return 0
    }
}
