package io.github.winddrift.fuckpangguai.account

sealed interface AuthTarget {
    data object None : AuthTarget
    data object AddAccount : AuthTarget
    data class Reauth(val localId: String) : AuthTarget
}
