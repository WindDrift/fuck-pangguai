package io.github.winddrift.fuckpangguai.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlin.math.roundToInt

/**
 * ZXing 解码封装，供相机帧和相册图片共用。
 *
 * 二维码检测本身与方向无关，这里直接按原始方向解码。注意 ZXing 的
 * [PlanarYUVLuminanceSource] 不支持旋转，调用 rotateCounterClockwise 会抛
 * UnsupportedOperationException；之前相机路径因此丢弃了所有竖屏帧。
 *
 * 胖乖设备使用的是低对比度、圆点描边的二维码，普通二值化经常找不到码。
 * 快速路径失败后会再做一次自适应二值化，并把深色模块向外膨胀，补上圆点
 * 之间的白色缝隙，再交给 ZXing。
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
        return decodePlanar(data, width, height)
    }

    /** 相册路径：先尝试常规二值化，再走增强路径。 */
    fun decodePhotoArgb(pixels: IntArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val source = RGBLuminanceSource(width, height, pixels)
        val fast = decodeWith(BinaryBitmap(HybridBinarizer(source)))
            ?: decodeWith(BinaryBitmap(GlobalHistogramBinarizer(source)))
            ?: decodeWith(BinaryBitmap(HybridBinarizer(source.invert())))
        if (fast != null) return fast
        return decodeLumaPreprocessed(pixels.toLuma(width, height), width, height)
    }

    /**
     * 增强路径：自适应二值化 + 深色模块膨胀。
     *
     * 先按原尺寸尝试，再缩到几个固定长边重试。现场设备码的圆点缝隙会随照片
     * 分辨率一起放大，固定半径的膨胀在高分辨率原图上补不住，缩图后反而稳定。
     * 只应在快速路径失败后调用。
     */
    fun decodeLumaPreprocessed(data: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || data.size < width * height) return null
        decodeLumaAtCurrentScale(data, width, height)?.let { return it }
        for (maxEdge in intArrayOf(800, 640, 512)) {
            if (maxOf(width, height) <= maxEdge) continue
            val scaled = scaleLumaNearest(data, width, height, maxEdge) ?: continue
            decodeLumaAtCurrentScale(scaled.data, scaled.width, scaled.height)?.let { return it }
        }
        return null
    }

    private fun decodeLumaAtCurrentScale(data: ByteArray, width: Int, height: Int): String? {
        val dilateRadius = if (maxOf(width, height) < 300) 1 else 2
        for (windowRadius in intArrayOf(12, 24)) {
            val prepared = adaptiveBinary(
                data = data,
                width = width,
                height = height,
                windowRadius = windowRadius,
                offset = 8,
                dilateRadius = dilateRadius,
            )
            decodePlanar(prepared, width, height)?.let { return it }
        }
        return null
    }

    private fun scaleLumaNearest(
        data: ByteArray,
        width: Int,
        height: Int,
        maxEdge: Int,
    ): ScaledLuma? {
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return null
        val scale = maxEdge.toFloat() / longest
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val xMap = IntArray(targetWidth) {
            (((it + 0.5f) * width / targetWidth).toInt()).coerceIn(0, width - 1)
        }
        val yMap = IntArray(targetHeight) {
            (((it + 0.5f) * height / targetHeight).toInt()).coerceIn(0, height - 1)
        }
        val out = ByteArray(targetWidth * targetHeight)
        var index = 0
        for (targetY in 0 until targetHeight) {
            val base = yMap[targetY] * width
            for (targetX in 0 until targetWidth) {
                out[index++] = data[base + xMap[targetX]]
            }
        }
        return ScaledLuma(out, targetWidth, targetHeight)
    }

    private data class ScaledLuma(
        val data: ByteArray,
        val width: Int,
        val height: Int,
    )

    private fun decodePlanar(data: ByteArray, width: Int, height: Int): String? =
        decodeWith(
            BinaryBitmap(
                HybridBinarizer(
                    PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false),
                ),
            ),
        )

    private fun decodeWith(bitmap: BinaryBitmap): String? =
        try {
            reader.decodeWithState(bitmap).text.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        } finally {
            reader.reset()
        }

    private fun IntArray.toLuma(width: Int, height: Int): ByteArray =
        ByteArray(width * height) { index ->
            val pixel = this[index]
            val red = (pixel shr 16) and 0xff
            val green = (pixel shr 8) and 0xff
            val blue = pixel and 0xff
            ((red + 2 * green + blue) / 4).toByte()
        }

    private fun adaptiveBinary(
        data: ByteArray,
        width: Int,
        height: Int,
        windowRadius: Int,
        offset: Int,
        dilateRadius: Int,
    ): ByteArray {
        val stride = width + 1
        val integral = LongArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            val rowBase = (y + 1) * stride
            val previousBase = y * stride
            val dataBase = y * width
            for (x in 0 until width) {
                rowSum += data[dataBase + x].toInt() and 0xff
                integral[rowBase + x + 1] = integral[previousBase + x + 1] + rowSum
            }
        }

        val binary = ByteArray(width * height)
        for (y in 0 until height) {
            val top = (y - windowRadius).coerceAtLeast(0)
            val bottom = (y + windowRadius).coerceAtMost(height - 1)
            val bottomBase = (bottom + 1) * stride
            val topBase = top * stride
            val dataBase = y * width
            for (x in 0 until width) {
                val left = (x - windowRadius).coerceAtLeast(0)
                val right = (x + windowRadius).coerceAtMost(width - 1)
                val sum = integral[bottomBase + right + 1] -
                    integral[topBase + right + 1] -
                    integral[bottomBase + left] +
                    integral[topBase + left]
                val count = (right - left + 1) * (bottom - top + 1)
                val threshold = sum / count - offset
                val value = data[dataBase + x].toInt() and 0xff
                binary[dataBase + x] = if (value <= threshold) 0 else 0xFF.toByte()
            }
        }
        return dilateDark(binary, width, height, dilateRadius)
    }

    /** 对深色像素做可分离的最小值滤波，把圆点之间的细缝连起来。 */
    private fun dilateDark(source: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        if (radius <= 0) return source
        val horizontal = ByteArray(source.size)
        val deque = IntArray(maxOf(width, height))
        for (y in 0 until height) {
            val rowBase = y * width
            var head = 0
            var tail = 0
            var next = 0
            for (x in 0 until width) {
                val until = minOf(width - 1, x + radius)
                while (next <= until) {
                    val value = source[rowBase + next].toInt() and 0xff
                    while (tail > head) {
                        val last = deque[tail - 1]
                        if ((source[rowBase + last].toInt() and 0xff) < value) break
                        tail--
                    }
                    deque[tail++] = next
                    next++
                }
                while (deque[head] < x - radius) head++
                horizontal[rowBase + x] = source[rowBase + deque[head]]
            }
        }

        val out = ByteArray(source.size)
        for (x in 0 until width) {
            var head = 0
            var tail = 0
            var next = 0
            for (y in 0 until height) {
                val until = minOf(height - 1, y + radius)
                while (next <= until) {
                    val value = horizontal[next * width + x].toInt() and 0xff
                    while (tail > head) {
                        val last = deque[tail - 1]
                        if ((horizontal[last * width + x].toInt() and 0xff) < value) break
                        tail--
                    }
                    deque[tail++] = next
                    next++
                }
                while (deque[head] < y - radius) head++
                out[y * width + x] = horizontal[deque[head] * width + x]
            }
        }
        return out
    }
}
