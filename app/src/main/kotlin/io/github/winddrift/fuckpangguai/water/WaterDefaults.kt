package io.github.winddrift.fuckpangguai.water

import io.github.winddrift.fuckpangguai.net.dto.PayChannelRoute

object WaterDefaults {
    fun promotions(usePoints: Boolean): String {
        val integralType = if (usePoints) "8" else "-8"
        return "[{\"promotionId\":\"0\",\"promotionType\":\"-6\",\"assetId\":\"0\",\"oldPromotionId\":\"\",\"orgId\":\"0\"}," +
            "{\"promotionId\":\"0\",\"promotionType\":\"-7\",\"assetId\":\"0\",\"oldPromotionId\":\"\",\"orgId\":\"0\"}," +
            "{\"promotionId\":\"0\",\"promotionType\":\"$integralType\",\"assetId\":\"0\",\"oldPromotionId\":\"0\",\"orgId\":\"0\"}]"
    }

    val PROMOTIONS: String = promotions(usePoints = false)

    fun payTypeFor(channel: PayChannelRoute): String =
        if (channel.method == 1501 && (channel.thirdMethod == 1506 || channel.thirdMethod == 1507)) {
            "1501"
        } else {
            ""
        }
}