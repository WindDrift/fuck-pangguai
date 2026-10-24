package io.github.winddrift.fuckpangguai.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import io.github.winddrift.fuckpangguai.account.AuthTarget
import io.github.winddrift.fuckpangguai.device.ResolvedDevice
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings

enum class MainTab(val label: String) {
    Scan("扫码"),
    History("历史"),
    Points("积分"),
    Settings("设置"),
}

val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.Scan -> MiuixIcons.Scan
        MainTab.History -> MiuixIcons.Recent
        MainTab.Points -> MiuixIcons.Promotions
        MainTab.Settings -> MiuixIcons.Settings
    }

sealed interface SecondaryPage {
    data class Confirm(val device: ResolvedDevice) : SecondaryPage
    data object Debug : SecondaryPage
    data class AccountAccess(val target: AuthTarget) : SecondaryPage
    data class Tickets(val shopId: String = "", val shopName: String = "") : SecondaryPage
}
