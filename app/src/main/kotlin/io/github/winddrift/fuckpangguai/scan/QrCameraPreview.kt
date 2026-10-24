package io.github.winddrift.fuckpangguai.scan

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun QrCameraPreview(
    cameraState: QrCameraState,
    onQr: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnQr = rememberUpdatedState(onQr)
    val executor = remember(cameraState) { Executors.newSingleThreadExecutor() }
    val analyzer = remember(cameraState) {
        QrAnalyzer { value -> latestOnQr.value(value) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, cameraState, executor, analyzer) {
        cameraState.bind()
        cameraState.controller.setImageAnalysisAnalyzer(executor, analyzer)
        onDispose {
            cameraState.controller.clearImageAnalysisAnalyzer()
            executor.shutdown()
            cameraState.unbind()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraState.controller
            }
        },
    )
}

private class QrAnalyzer(private val onQr: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val decoder = QrDecoder()
    private var lastText: String? = null
    private var lastAt = 0L
    private var lastFallbackAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val frame = image.toLumaFrame() ?: return
            val now = System.currentTimeMillis()
            // 二维码检测与方向无关，不能旋转 PlanarYUVLuminanceSource：
            // ZXing 不支持该旋转操作，会抛异常导致所有竖屏帧被丢弃。
            val text = decoder.decodeLuma(frame.data, frame.width, frame.height)
                ?: if (now - lastFallbackAt >= 300) {
                    // 低对比度/圆点样式的码需要更重的预处理，限制在约 3fps 执行。
                    lastFallbackAt = now
                    decoder.decodeLumaPreprocessed(frame.data, frame.width, frame.height)
                } else {
                    null
                }
            if (text.isNullOrBlank()) return
            if (text != lastText || now - lastAt > 2_000) {
                lastText = text
                lastAt = now
                onQr(text)
            }
        } catch (_: Throwable) {
            // Camera frames can be malformed during rotation; ignore.
        } finally {
            image.close()
        }
    }
}

private data class LumaFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
)

private fun ImageProxy.toLumaFrame(): LumaFrame? {
    if (planes.isEmpty()) return null
    val plane = planes[0]
    val width = this.width
    val height = this.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val buffer = plane.buffer.duplicate()
    if (width <= 0 || height <= 0) return null

    val data = ByteArray(width * height)
    var offset = 0
    for (row in 0 until height) {
        var index = row * rowStride
        for (column in 0 until width) {
            data[offset++] = buffer.get(index)
            index += pixelStride
        }
    }
    return LumaFrame(data, width, height)
}

object QrImageDecoder {

    /** 手机照片动辄几十兆像素，直接全尺寸解码容易 OOM，也超出 ZXing 的最佳范围。 */
    private const val MAX_EDGE = 2048

    fun decode(context: android.content.Context, uri: Uri): String? {
        val bitmap = loadBitmap(context, uri) ?: return null
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return null
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            QrDecoder().decodePhotoArgb(pixels, width, height)
        } catch (_: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadBitmap(context: android.content.Context, uri: Uri): Bitmap? =
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val width = info.size.width
                val height = info.size.height
                val longest = maxOf(width, height)
                if (longest > MAX_EDGE) {
                    val scale = MAX_EDGE.toFloat() / longest
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } catch (_: Throwable) {
            null
        }
}
