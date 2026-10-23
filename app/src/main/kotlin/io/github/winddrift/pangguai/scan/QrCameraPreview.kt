package io.github.winddrift.pangguai.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Composable
fun QrCameraPreview(
    onQr: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnQr = rememberUpdatedState(onQr)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        QrAnalyzer { value -> latestOnQr.value(value) }
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(androidx.camera.view.CameraController.IMAGE_ANALYSIS)
            setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        }
    }

    DisposableEffect(lifecycleOwner, controller, executor, analyzer) {
        controller.bindToLifecycle(lifecycleOwner)
        controller.setImageAnalysisAnalyzer(executor, analyzer)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
    )
}

private class QrAnalyzer(private val onQr: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }
    private var lastText: String? = null
    private var lastAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val frame = image.toLumaFrame() ?: return
            var source: com.google.zxing.LuminanceSource = PlanarYUVLuminanceSource(
                frame.data,
                frame.width,
                frame.height,
                0,
                0,
                frame.width,
                frame.height,
                false,
            )
            repeat(frame.rotationQuarterTurns) {
                source = source.rotateCounterClockwise()
            }
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            val text = result.text
            val now = System.currentTimeMillis()
            if (text.isNotBlank() && (text != lastText || now - lastAt > 2_000)) {
                lastText = text
                lastAt = now
                onQr(text)
            }
        } catch (_: NotFoundException) {
            // No QR in this frame.
        } catch (_: Throwable) {
            // Camera frames can be malformed during rotation; ignore.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

private data class LumaFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val rotationQuarterTurns: Int,
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

    val rotation = ((imageInfo.rotationDegrees % 360) + 360) % 360
    return LumaFrame(
        data = data,
        width = width,
        height = height,
        rotationQuarterTurns = when (rotation) {
            90 -> 1
            180 -> 2
            270 -> 3
            else -> 0
        },
    )
}

object QrImageDecoder {
    fun decode(context: Context, uri: Uri): String? {
        val bitmap: Bitmap = ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri),
        ) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.TRY_HARDER to true,
                    ),
                )
            }
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }
}
