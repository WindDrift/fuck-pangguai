package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop

/**
 * 把 [TextFieldState] 与外部字符串状态双向同步。
 *
 * 外部值变化（如清除按钮、重置）时写回输入框；用户输入时通过 [onValueChange] 上抛。
 * 跳过初始发射，避免挂载时把外部状态原样回写一遍。
 */
@Composable
fun rememberSyncedTextFieldState(
    value: String,
    onValueChange: (String) -> Unit,
): TextFieldState {
    val state = rememberTextFieldState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) {
        if (value != state.text.toString()) {
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .drop(1)
            .collect { currentOnValueChange(it) }
    }
    return state
}