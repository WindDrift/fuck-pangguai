package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Miuix 图标集没有手电筒，这里保持同一套 24dp 线性视觉。 */
val TorchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Torch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 22f)
            lineTo(14f, 22f)
            lineTo(14f, 9f)
            lineTo(16f, 6f)
            lineTo(16f, 3f)
            lineTo(8f, 3f)
            lineTo(8f, 6f)
            lineTo(10f, 9f)
            close()
        }
    }.build()
}
