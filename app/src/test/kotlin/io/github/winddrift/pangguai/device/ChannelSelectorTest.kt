package io.github.winddrift.pangguai.device

import io.github.winddrift.pangguai.net.dto.PayChannelRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelSelectorTest {

    private val channels = listOf(
        PayChannelRoute(method = 13, methodName = "支付宝"),
        PayChannelRoute(method = 2, methodName = "钱包", defaultFlag = true),
        PayChannelRoute(method = 15, methodName = "支付宝免密", checked = true),
    )

    @Test
    fun `last successful channel wins`() {
        assertEquals(0, ChannelSelector.selectIndex(channels, lastMethod = 13))
    }

    @Test
    fun `checked wins when no last channel`() {
        assertEquals(2, ChannelSelector.selectIndex(channels, lastMethod = null))
    }

    @Test
    fun `default wins when nothing checked`() {
        val onlyDefault = listOf(
            PayChannelRoute(method = 13),
            PayChannelRoute(method = 2, defaultFlag = true),
        )
        assertEquals(1, ChannelSelector.selectIndex(onlyDefault, lastMethod = null))
    }

    @Test
    fun `falls back to first`() {
        val plain = listOf(PayChannelRoute(method = 13), PayChannelRoute(method = 2))
        assertEquals(0, ChannelSelector.selectIndex(plain, lastMethod = 99))
    }
}
