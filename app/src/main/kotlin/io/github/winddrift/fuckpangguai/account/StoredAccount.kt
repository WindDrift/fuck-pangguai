package io.github.winddrift.fuckpangguai.account

import kotlinx.serialization.Serializable

@Serializable
data class StoredAccount(
    val localId: String,
    val serverId: String = "",
    val nickname: String = "",
    val alias: String = "",
    val avatarUrl: String = "",
    val phoneMasked: String = "",
    val phoneHash: String = "",
    val phoneCipher: String = "",
    val phoneIv: String = "",
    val tokenCipher: String = "",
    val tokenIv: String = "",
    val invalid: Boolean = false,
    val loggedOut: Boolean = false,
    val pendingProfile: Boolean = true,
    val lastUsedAt: Long = 0L,
    val lastSyncedAt: Long = 0L,
)

data class ProfileUpdateResult(
    val account: StoredAccount,
    val mergedFromLocalId: String? = null,
)
