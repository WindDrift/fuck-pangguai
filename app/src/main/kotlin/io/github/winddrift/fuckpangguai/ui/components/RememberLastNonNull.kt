package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class LastValueHolder<T>(var value: T? = null)

/**
 * 在弹层退场动画期间保留最后一次非空值。
 *
 * Miuix 的 Overlay 组件在 show 变为 false 后仍会组合内容直到隐藏动画结束，
 * 而调用方此时通常已清空选中项。直接读取外部状态会让内容在退场瞬间变空白。
 */
@Composable
fun <T : Any> rememberLastNonNull(value: T?): T? {
    val holder = remember { LastValueHolder<T>() }
    if (value != null) holder.value = value
    return holder.value
}
