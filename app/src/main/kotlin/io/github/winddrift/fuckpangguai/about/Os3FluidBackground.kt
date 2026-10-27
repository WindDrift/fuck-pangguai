/*
 * Portions Copyright 2026 compose-miuix-ui contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from InstallerX-Revived:
 * ui/library/effect/OS3BgFrag.kt and BgEffectConfig.kt
 */
package io.github.winddrift.fuckpangguai.about

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.isActive

@Composable
fun Os3FluidBackground(
    dark: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    Os3FluidBackground33(
        dark = dark,
        playing = playing,
        modifier = modifier,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Os3FluidBackground33(
    dark: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val preset = remember(dark) { if (dark) DarkPreset else LightPreset }
    val shaderState = remember(preset) {
        runCatching { Os3ShaderState(preset) }.getOrNull()
    } ?: return

    var time by remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME,
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> resumed =
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(playing, resumed, animatorsEnabled) {
        if (!playing || !resumed || !animatorsEnabled) return@LaunchedEffect
        var lastFrame = 0L
        var lastEmit = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (lastEmit == 0L || now - lastEmit >= FRAME_INTERVAL_NANOS) {
                    if (lastFrame != 0L) {
                        time += (now - lastFrame) / 1_000_000_000f
                    }
                    lastFrame = now
                    lastEmit = now
                }
            }
        }
    }

    ComposeCanvas(modifier = modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            shaderState.draw(
                canvas = canvas.nativeCanvas,
                width = size.width,
                height = size.height,
                time = time,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Os3ShaderState(
    private val preset: Os3Preset,
) {
    private val shader = RuntimeShader(OS3_BG_FRAG)
    private val paint = Paint().apply { isAntiAlias = true }
    private val resolution = FloatArray(2)
    private val pointsAnim = FloatArray(8)
    private val colors = FloatArray(16)
    private var lastWidth = Float.NaN
    private var lastHeight = Float.NaN

    init {
        shader.setFloatUniform("uBound", floatArrayOf(0f, 0f, 1f, 1f))
        shader.setFloatUniform("uTranslateY", 0f)
        shader.setFloatUniform("uAlphaMulti", 1f)
        shader.setFloatUniform("uNoiseScale", 1.5f)
        shader.setFloatUniform("uPointRadiusMulti", 1f)
        shader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        shader.setFloatUniform("uLightOffset", preset.lightOffset)
        shader.setFloatUniform("uPoints", preset.points)
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        time: Float,
    ) {
        if (width != lastWidth || height != lastHeight) {
            resolution[0] = width
            resolution[1] = height
            shader.setFloatUniform("uResolution", resolution)
            lastWidth = width
            lastHeight = height
        }

        shader.setFloatUniform("uAnimTime", time)
        updatePoints(time)
        updateColors(time)
        shader.setFloatUniform("uPointsAnim", pointsAnim)
        shader.setFloatUniform("uColors", colors)

        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun updatePoints(time: Float) {
        val offset = preset.pointOffset
        for (index in 0 until POINT_COUNT) {
            val x = preset.points[index * 3]
            val y = preset.points[index * 3 + 1]
            val animatedX = x + sin(time + y) * offset
            val animatedY = y + cos(time + animatedX) * offset
            pointsAnim[index * 2] = animatedX
            pointsAnim[index * 2 + 1] = animatedY
        }
    }

    private fun updateColors(time: Float) {
        val stageValue = time / preset.colorInterpPeriod
        val stage = floor(stageValue).toInt()
        val progress = smoothStep(stageValue - floor(stageValue))
        val from: FloatArray
        val to: FloatArray
        when (stage % COLOR_STAGE_COUNT) {
            0 -> {
                from = preset.colors1
                to = preset.colors2
            }

            1 -> {
                from = preset.colors2
                to = preset.colors3
            }

            else -> {
                from = preset.colors3
                to = preset.colors1
            }
        }
        for (index in 0 until COLOR_COMPONENT_COUNT) {
            colors[index] = from[index] + (to[index] - from[index]) * progress
        }
    }

    private companion object {
        const val POINT_COUNT = 4
        const val COLOR_STAGE_COUNT = 3
        const val COLOR_COMPONENT_COUNT = 16
    }
}

private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

private data class Os3Preset(
    val points: FloatArray,
    val colors1: FloatArray,
    val colors2: FloatArray,
    val colors3: FloatArray,
    val colorInterpPeriod: Float,
    val lightOffset: Float,
    val saturateOffset: Float,
    val pointOffset: Float,
)

private val LightPreset = Os3Preset(
    points = floatArrayOf(
        0.8f, 0.2f, 1.0f,
        0.8f, 0.9f, 1.0f,
        0.2f, 0.9f, 1.0f,
        0.2f, 0.2f, 1.0f,
    ),
    colors1 = floatArrayOf(
        1.0f, 0.9f, 0.94f, 1.0f,
        1.0f, 0.84f, 0.89f, 1.0f,
        0.97f, 0.73f, 0.82f, 1.0f,
        0.64f, 0.65f, 0.98f, 1.0f,
    ),
    colors2 = floatArrayOf(
        0.58f, 0.74f, 1.0f, 1.0f,
        1.0f, 0.9f, 0.93f, 1.0f,
        0.74f, 0.76f, 1.0f, 1.0f,
        0.97f, 0.77f, 0.84f, 1.0f,
    ),
    colors3 = floatArrayOf(
        0.98f, 0.86f, 0.9f, 1.0f,
        0.6f, 0.73f, 0.98f, 1.0f,
        0.92f, 0.93f, 1.0f, 1.0f,
        0.56f, 0.69f, 1.0f, 1.0f,
    ),
    colorInterpPeriod = 5.0f,
    lightOffset = 0.1f,
    saturateOffset = 0.2f,
    pointOffset = 0.2f,
)

private val DarkPreset = Os3Preset(
    points = floatArrayOf(
        0.8f, 0.2f, 1.0f,
        0.8f, 0.9f, 1.0f,
        0.2f, 0.9f, 1.0f,
        0.2f, 0.2f, 1.0f,
    ),
    colors1 = floatArrayOf(
        0.2f, 0.06f, 0.88f, 0.4f,
        0.3f, 0.14f, 0.55f, 0.5f,
        0.0f, 0.64f, 0.96f, 0.5f,
        0.11f, 0.16f, 0.83f, 0.4f,
    ),
    colors2 = floatArrayOf(
        0.07f, 0.15f, 0.79f, 0.5f,
        0.62f, 0.21f, 0.67f, 0.5f,
        0.06f, 0.25f, 0.84f, 0.5f,
        0.0f, 0.2f, 0.78f, 0.5f,
    ),
    colors3 = floatArrayOf(
        0.58f, 0.3f, 0.74f, 0.4f,
        0.27f, 0.18f, 0.6f, 0.5f,
        0.66f, 0.26f, 0.62f, 0.5f,
        0.12f, 0.16f, 0.7f, 0.6f,
    ),
    colorInterpPeriod = 8.0f,
    lightOffset = 0.0f,
    saturateOffset = 0.17f,
    pointOffset = 0.4f,
)

private const val FRAME_INTERVAL_NANOS = 33_000_000L

private const val OS3_BG_FRAG = """
uniform vec2 uResolution;
uniform float uAnimTime;
uniform vec4 uBound;
uniform float uTranslateY;
uniform vec3 uPoints[4];
uniform vec2 uPointsAnim[4];
uniform vec4 uColors[4];
uniform float uAlphaMulti;
uniform float uNoiseScale;
uniform float uPointRadiusMulti;
uniform float uSaturateOffset;
uniform float uLightOffset;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.13);
    p3 += dot(p3, p3.yzx + 3.333);
    return fract((p3.x + p3.y) * p3.z);
}

float perlin(vec2 x) {
    vec2 i = floor(x); vec2 f = fract(x);

    float a = hash(i); float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0)); float d = hash(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float gradientNoise(in vec2 uv) {
    return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
}

vec4 main(vec2 fragCoord){
    vec2 vUv = fragCoord/uResolution;
    vUv.y = 1.0-vUv.y;
    vec2 uv = vUv;
    uv -= vec2(0., uTranslateY);
    uv.xy -= uBound.xy;
    uv.xy /= uBound.zw;

    vec4 color = vec4(0.0);
    float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));

    for (int i = 0; i < 4; i++){
        vec4 pointColor = uColors[i];
        pointColor.rgb *= pointColor.a;
        vec2 point = uPointsAnim[i];
        float rad = uPoints[i].z * uPointRadiusMulti;

        float d = distance(uv, point);
        float pct = smoothstep(rad, 0., d);
        color.rgb = mix(color.rgb, pointColor.rgb, pct);
        color.a = mix(color.a, pointColor.a, pct);
    }

    float oppositeNoise = smoothstep(0., 1., noiseValue);
    color.rgb /= color.a;
    vec3 hsv = rgb2hsv(color.rgb);
    hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
    color.rgb = hsv2rgb(hsv);
    color.rgb += oppositeNoise * uLightOffset;

    color.a = clamp(color.a, 0., 1.);
    color.a *= uAlphaMulti;

    color += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);
    return vec4(color.rgb * color.a, color.a);
}
"""
