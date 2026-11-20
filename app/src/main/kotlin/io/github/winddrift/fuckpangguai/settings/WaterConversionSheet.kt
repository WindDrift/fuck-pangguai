package io.github.winddrift.fuckpangguai.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.winddrift.fuckpangguai.water.WaterConversion
import io.github.winddrift.fuckpangguai.water.WaterConversionValidation
import io.github.winddrift.fuckpangguai.water.formatConversionPreview
import io.github.winddrift.fuckpangguai.water.validateWaterConversion
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun WaterConversionSheet(
    show: Boolean,
    conversion: WaterConversion?,
    onDismiss: () -> Unit,
    onSave: (WaterConversion) -> Unit,
    onClear: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val usageBase = rememberTextFieldState()
    val waterMl = rememberTextFieldState()
    val cupMl = rememberTextFieldState()

    LaunchedEffect(show, conversion) {
        if (show) {
            usageBase.setTextAndPlaceCursorAtEnd(conversion?.usageBase?.toPlainInput().orEmpty())
            waterMl.setTextAndPlaceCursorAtEnd(conversion?.waterMl?.toPlainInput().orEmpty())
            cupMl.setTextAndPlaceCursorAtEnd(conversion?.cupMl?.toPlainInput().orEmpty())
        }
    }

    val usageText = usageBase.text.toString()
    val waterText = waterMl.text.toString()
    val cupText = cupMl.text.toString()
    val validation = remember(usageText, waterText, cupText) {
        validateWaterConversion(usageText, waterText, cupText)
    }
    val valid = validation as? WaterConversionValidation.Valid
    val empty = validation is WaterConversionValidation.Empty
    val saveEnabled = valid != null || (empty && conversion != null)

    OverlayBottomSheet(
        show = show,
        title = "水量换算",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "可以先用已知容量的杯子或瓶子接一次水：记下容器容量，再对比接水前后的用量差值，" +
                    "把这两个数字填进来。示例：用量增加 100、实际接水 500 mL，就填 100 和 500。",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            Text(
                text = "如果平时按杯喝水，可再填一杯容量；留空则只显示 mL/L。" +
                    "这套规则对所有账号生效，修改后历史记录会立即重算。",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DecimalField(
                    state = usageBase,
                    label = "用量",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    onKeyboardAction = KeyboardActionHandler {
                        focusManager.moveFocus(FocusDirection.Right)
                    },
                )
                DecimalField(
                    state = waterMl,
                    label = "水量（mL）",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    onKeyboardAction = KeyboardActionHandler {
                        focusManager.moveFocus(FocusDirection.Down)
                    },
                )
            }

            DecimalField(
                state = cupMl,
                label = "一杯容量（mL，可选）",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = KeyboardActionHandler { focusManager.clearFocus() },
            )

            when (validation) {
                WaterConversionValidation.Empty -> Text(
                    text = if (conversion == null) {
                        "还没有设置换算规则。"
                    } else {
                        "留空保存会关闭换算，恢复原始用量显示；也可以点下方的“清除换算”。"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )

                is WaterConversionValidation.Invalid -> Text(
                    text = validation.message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.error,
                )

                is WaterConversionValidation.Valid -> Text(
                    text = "预览：${formatConversionPreview(validation.conversion)}",
                    style = MiuixTheme.textStyles.body1,
                    color = colors.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (conversion != null) {
                    TextButton(
                        text = "清除换算",
                        onClick = onClear,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                )
                Button(
                    onClick = {
                        when {
                            valid != null -> onSave(valid.conversion)
                            empty && conversion != null -> onClear()
                        }
                    },
                    enabled = saveEnabled,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun DecimalField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    val colors = MiuixTheme.colorScheme
    val text = state.text.toString()
    val invalid = text.isNotBlank() && text.toDoubleOrNull() == null
    TextField(
        state = state,
        modifier = modifier,
        label = label,
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = DecimalInputTransformation,
        colors = TextFieldDefaults.textFieldColors(
            labelColor = if (invalid) colors.error else colors.onSurfaceSecondary,
            borderColor = if (invalid) colors.error else colors.outline,
        ),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
    )
}

private val DecimalInputTransformation = InputTransformation.byValue { _, proposed ->
    sanitizeDecimalInput(proposed.toString())
}

private fun sanitizeDecimalInput(raw: String): String {
    val builder = StringBuilder()
    var hasDot = false
    var decimals = 0
    for (char in raw) {
        when {
            char.isDigit() -> {
                if (hasDot) {
                    if (decimals >= MAX_DECIMALS) continue
                    decimals++
                }
                builder.append(char)
            }

            char == '.' && !hasDot -> {
                hasDot = true
                builder.append(char)
            }
        }
        if (builder.length >= MAX_INPUT_LENGTH) break
    }
    return builder.toString()
}

private fun java.math.BigDecimal.toPlainInput(): String =
    if (compareTo(java.math.BigDecimal.ZERO) == 0) {
        "0"
    } else {
        stripTrailingZeros().toPlainString()
    }

private const val MAX_DECIMALS = 3
private const val MAX_INPUT_LENGTH = 12
