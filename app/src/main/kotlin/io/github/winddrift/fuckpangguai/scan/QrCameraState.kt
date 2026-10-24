package io.github.winddrift.fuckpangguai.scan

import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 相机控制器的 UI 侧状态：手电筒开关与可用性。
 * 预览、分析器和生命周期绑定仍由 QrCameraPreview 负责。
 */
class QrCameraState internal constructor(
    context: Context,
    internal val lifecycleOwner: LifecycleOwner,
) {
    internal val controller = LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        // 640×480 对稍远或较小的设备码不够，720p 明显更容易识别。
        setImageAnalysisResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                    ),
                )
                .build(),
        )
    }

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    private val _flashAvailable = MutableStateFlow(
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH),
    )
    val flashAvailable: StateFlow<Boolean> = _flashAvailable.asStateFlow()

    private var torchObserver: Observer<Int>? = null

    internal fun bind() {
        controller.bindToLifecycle(lifecycleOwner)
        controller.cameraInfo?.let { info ->
            _flashAvailable.value = info.hasFlashUnit()
        }
        torchObserver = Observer { state ->
            _torchOn.value = state == TorchState.ON
        }.also { observer ->
            controller.torchState.observe(lifecycleOwner, observer)
        }
    }

    internal fun unbind() {
        torchObserver?.let { controller.torchState.removeObserver(it) }
        torchObserver = null
        runCatching { controller.enableTorch(false) }
        _torchOn.value = false
        controller.clearImageAnalysisAnalyzer()
        controller.unbind()
    }

    fun toggleTorch() {
        if (!_flashAvailable.value) return
        runCatching { controller.enableTorch(!_torchOn.value) }
    }
}

@Composable
fun rememberQrCameraState(): QrCameraState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return remember(context, lifecycleOwner) {
        QrCameraState(context, lifecycleOwner)
    }
}
