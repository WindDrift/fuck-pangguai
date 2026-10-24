package io.github.winddrift.fuckpangguai.ui.animation

import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import top.yukonga.miuix.kmp.anim.folmeSpring

/** 页面级转场：接近 Miuix 页面切换的临界阻尼弹簧。 */
internal val PageSlideSpec: SpringSpec<IntOffset> =
    folmeSpring(damping = 1f, response = 0.4f, visibilityThreshold = IntOffset(1, 1))

internal val PageFadeSpec: SpringSpec<Float> =
    folmeSpring(damping = 1f, response = 0.3f, visibilityThreshold = 0.01f)

/** 页面内部元素的状态切换：略带动量，避免生硬。 */
internal val ContentSlideSpec: SpringSpec<IntOffset> =
    folmeSpring(damping = 0.9f, response = 0.35f, visibilityThreshold = IntOffset(1, 1))

internal val ContentFadeSpec: SpringSpec<Float> =
    folmeSpring(damping = 0.9f, response = 0.28f, visibilityThreshold = 0.01f)

internal val ColorSpringSpec: SpringSpec<Color> =
    folmeSpring(damping = 1f, response = 0.3f)

/** 数值滚动：用量、计数等。 */
internal val ValueSpringSpec: SpringSpec<Float> =
    folmeSpring(damping = 1f, response = 0.35f, visibilityThreshold = 0.01f)

internal val SizeSpringSpec: SpringSpec<IntSize> =
    folmeSpring(damping = 1f, response = 0.35f, visibilityThreshold = IntSize(1, 1))


