package io.github.winddrift.fuckpangguai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 保持内容视觉尺寸不变，同时把可点击区域扩展到至少 48dp。
 *
 * 选中类控件传入 [selected]，其余操作使用 [Role.Button]。
 */
@Composable
fun MinimumTouchTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    selected: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val interactionModifier = if (selected != null) {
        Modifier.selectable(
            selected = selected,
            role = role,
            onClick = onClick,
        )
    } else {
        Modifier.clickable(role = role, onClick = onClick)
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
