package io.github.winddrift.fuckpangguai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 相机取景界面的叠层配色。
 *
 * 这类颜色叠在相机画面上，不跟随应用主题：浅色主题下相机会让画面更亮，
 * 深色主题下也不会变暗，所以固定使用黑白双色加分级 alpha，保证两种主题下
 * 文字与遮罩都有足够对比度。
 */
object CameraOverlayColors {
    /** 全屏相机背景，未出画面时占位。 */
    val PreviewBackground = Color.Black

    /** 取景框外的压暗遮罩。 */
    val Scrim = Color.Black.copy(alpha = 0.42f)

    /** 顶部渐变遮罩的起始色。 */
    val HeaderGradientStart = Color.Black.copy(alpha = 0.62f)

    /** 主标题与主要文字。 */
    val PrimaryText = Color.White

    /** 次要说明文字。 */
    val SecondaryText = Color.White.copy(alpha = 0.72f)

    /** 更弱的辅助文字。 */
    val TertiaryText = Color.White.copy(alpha = 0.6f)

    /** 图标默认色。 */
    val Icon = Color.White

    /** 图标弱化色。 */
    val IconMuted = Color.White.copy(alpha = 0.85f)

    /** 工具按钮的底色。 */
    val ToolBackground = Color.Black.copy(alpha = 0.4f)

    /** 提示胶囊的底色。 */
    val PillBackground = Color.Black.copy(alpha = 0.55f)

    /** 权限提示卡片的底色。 */
    val PanelBackground = Color.Black.copy(alpha = 0.38f)

    /** 取景框边角与扫描线。 */
    val FrameAccent = Color.White.copy(alpha = 0.92f)

    /** 取景框提示文字。 */
    val FrameHint = Color.White.copy(alpha = 0.72f)

    /** 关于页图标底衬，叠在流体渐变背景上。 */
    fun aboutIconBackground(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.6f)

    /** 关于页图标描边。 */
    fun aboutIconBorder(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.8f)

    /** 关于页版本号胶囊底衬。 */
    fun aboutVersionPill(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.55f)

    /** 关于页顶栏返回按钮底衬，未滚动时可见。 */
    fun aboutBackButton(dark: Boolean): Color =
        if (dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.55f)
}
