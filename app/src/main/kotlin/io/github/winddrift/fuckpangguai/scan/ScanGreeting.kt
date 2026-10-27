package io.github.winddrift.fuckpangguai.scan

import io.github.winddrift.fuckpangguai.account.StoredAccount

internal fun greetingForHour(hour: Int): String = when (hour) {
    in 0..5 -> "凌晨好"
    in 6..10 -> "早上好"
    in 11..13 -> "中午好"
    in 14..17 -> "下午好"
    else -> "晚上好"
}

internal fun greetingName(account: StoredAccount?): String {
    if (account == null) return "同学"
    return account.alias.trim().takeIf { it.isNotEmpty() }
        ?: account.nickname.trim().takeIf { it.isNotEmpty() }
        ?: account.phoneMasked.trim().takeIf { it.isNotEmpty() }
        ?: "同学"
}

internal fun greetingText(account: StoredAccount?, hour: Int): String =
    "${greetingForHour(hour)}，${greetingName(account)}"
