package io.github.winddrift.fuckpangguai.water

import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute

object WaterDefaults {
    const val PROMOTIONS =
        "[{\"promotionId\":\"0\",\"promotionType\":\"-6\",\"assetId\":\"0\",\"oldPromotionId\":\"\",\"orgId\":\"0\"}," +
            "{\"promotionId\":\"0\",\"promotionType\":\"-7\",\"assetId\":\"0\",\"oldPromotionId\":\"\",\"orgId\":\"0\"}," +
            "{\"promotionId\":\"0\",\"promotionType\":\"-8\",\"assetId\":\"0\",\"oldPromotionId\":\"0\",\"orgId\":\"0\"}]"

    fun payTypeFor(channel: PayChannelRoute): String =
        if (channel.method == 1501 && (channel.thirdMethod == 1506 || channel.thirdMethod == 1507)) {
            "1501"
        } else {
            ""
        }
}
