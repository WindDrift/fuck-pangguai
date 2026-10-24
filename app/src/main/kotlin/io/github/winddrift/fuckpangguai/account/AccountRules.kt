package io.github.winddrift.fuckpangguai.account

import io.github.winddrift.fuckpangguai.net.QiekjSigner

internal fun sortAccounts(accounts: List<StoredAccount>): List<StoredAccount> =
    accounts.sortedWith(
        compareByDescending<StoredAccount> { it.lastUsedAt }
            .thenBy { it.localId },
    )

internal fun displayName(account: StoredAccount): String =
    account.alias.trim().ifBlank {
        account.nickname.trim().ifBlank {
            account.phoneMasked.ifBlank { "未知账号" }
        }
    }

internal fun maskPhone(phone: String): String {
    val trimmed = phone.trim()
    return if (trimmed.length >= 7) trimmed.replaceRange(3, 7, "****") else trimmed
}

internal fun phoneHash(phone: String): String =
    phone.trim().takeIf { it.isNotBlank() }?.let(QiekjSigner::sha256Hex).orEmpty()

internal fun findMergeTarget(
    accounts: List<StoredAccount>,
    serverId: String,
    phoneHash: String,
    excludeLocalId: String? = null,
): StoredAccount? =
    accounts.firstOrNull {
        it.localId != excludeLocalId && serverId.isNotBlank() && it.serverId == serverId
    } ?: accounts.firstOrNull {
        it.localId != excludeLocalId && phoneHash.isNotBlank() && it.phoneHash == phoneHash
    }

internal fun mergedAccount(target: StoredAccount, source: StoredAccount): StoredAccount =
    target.copy(
        serverId = target.serverId.ifBlank { source.serverId },
        nickname = source.nickname.ifBlank { target.nickname },
        avatarUrl = source.avatarUrl.ifBlank { target.avatarUrl },
        phoneMasked = source.phoneMasked.ifBlank { target.phoneMasked },
        phoneHash = source.phoneHash.ifBlank { target.phoneHash },
        phoneCipher = source.phoneCipher.ifBlank { target.phoneCipher },
        phoneIv = source.phoneIv.ifBlank { target.phoneIv },
        tokenCipher = source.tokenCipher.ifBlank { target.tokenCipher },
        tokenIv = source.tokenIv.ifBlank { target.tokenIv },
        invalid = false,
        pendingProfile = false,
        lastUsedAt = maxOf(target.lastUsedAt, source.lastUsedAt),
        lastSyncedAt = maxOf(target.lastSyncedAt, source.lastSyncedAt),
    )

internal fun nextAccountAfterDelete(
    accounts: List<StoredAccount>,
    deletingLocalId: String,
): StoredAccount? =
    accounts
        .filter {
            it.localId != deletingLocalId &&
                !it.invalid &&
                it.tokenCipher.isNotBlank() &&
                it.tokenIv.isNotBlank()
        }
        .maxByOrNull { it.lastUsedAt }
