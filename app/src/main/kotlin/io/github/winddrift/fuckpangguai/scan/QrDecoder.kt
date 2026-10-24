package io.github.winddrift.fuckpangguai.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/**
 * ZXing 解码封装，供相机帧和相册图片共用。
 *
 * 二维码检测本身与方向无关，这里直接按原始方向解码。注意 ZXing 的
 * [PlanarYUVLuminanceSource] 不支持旋转，调用 rotateCounterClockwise 会抛
 * UnsupportedOperationException；之前相机路径因此丢弃了所有竖屏帧。
 */
internal class QrDecoder {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    /** 相机路径：Y 平面连续亮度数据，单次 Hybrid 解码，保证逐帧速度。 */
    fun decodeLuma(data: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || data.size < width * height) return null
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        return decodeWith(BinaryBitmap(HybridBinarizer(source)))
    }

    /** 相册路径：可承担更重的解码，逐级尝试不同二值化与反色。 */
    fun decodePhotoArgb(pixels: IntArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val source = RGBLuminanceSource(width, height, pixels)
        return decodeWith(BinaryBitmap(HybridBinarizer(source)))
            ?: decodeWith(BinaryBitmap(GlobalHistogramBinarizer(source)))
            ?: decodeWith(BinaryBitmap(HybridBinarizer(source.invert())))
    }

    private fun decodeWith(bitmap: BinaryBitmap): String? =
        try {
            reader.decodeWithState(bitmap).text.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        } finally {
            reader.reset()
        }
}
