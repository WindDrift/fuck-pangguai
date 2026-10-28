package io.github.winddrift.fuckpangguai.account

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.winddrift.fuckpangguai.storage.appDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

class AccountStore(
    private val context: Context,
    private val cipher: AccountCipher = AccountCipher(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()

    private val _accounts = MutableStateFlow<List<StoredAccount>>(emptyList())
    val accounts = _accounts.asStateFlow()

    private val _activeAccount = MutableStateFlow<StoredAccount?>(null)
    val activeAccount = _activeAccount.asStateFlow()

    @Volatile
    var currentToken: String? = null
        private set

    private var initialized = false

    suspend fun initialize() = mutex.withLock { initializeLocked() }

    fun activeLocalId(): String? = _activeAccount.value?.localId

    suspend fun hasActiveSession(): Boolean = mutex.withLock {
        initializeLocked()
        currentToken != null && _activeAccount.value?.invalid == false
    }

    suspend fun addOrMerge(token: String, phoneHint: String): StoredAccount = mutex.withLock {
        require(token.isNotBlank()) { "token must not be blank" }
        initializeLocked()

        val now = System.currentTimeMillis()
        val phone = phoneHint.trim()
        val hash = phoneHash(phone)
        val phoneEncrypted = phone.takeIf { it.isNotBlank() }?.let(cipher::encrypt)
        val tokenEncrypted = cipher.encrypt(token)
        val existing = findMergeTarget(_accounts.value, serverId = "", phoneHash = hash)

        val account = if (existing != null) {
            existing.copy(
                tokenCipher = tokenEncrypted.cipherText,
                tokenIv = tokenEncrypted.iv,
                invalid = false,
                loggedOut = false,
                lastUsedAt = now,
                phoneMasked = phone.takeIf { it.isNotBlank() }?.let(::maskPhone)
                    ?: existing.phoneMasked,
                phoneHash = hash.ifBlank { existing.phoneHash },
                phoneCipher = phoneEncrypted?.cipherText ?: existing.phoneCipher,
                phoneIv = phoneEncrypted?.iv ?: existing.phoneIv,
                pendingProfile = existing.serverId.isBlank(),
            )
        } else {
            StoredAccount(
                localId = UUID.randomUUID().toString(),
                phoneMasked = phone.takeIf { it.isNotBlank() }?.let(::maskPhone).orEmpty(),
                phoneHash = hash,
                phoneCipher = phoneEncrypted?.cipherText.orEmpty(),
                phoneIv = phoneEncrypted?.iv.orEmpty(),
                tokenCipher = tokenEncrypted.cipherText,
                tokenIv = tokenEncrypted.iv,
                pendingProfile = true,
                lastUsedAt = now,
            )
        }

        val updated = _accounts.value.filterNot { it.localId == account.localId } + account
        writeLocked(updated, account.localId)
        account
    }

    suspend fun activate(localId: String): StoredAccount? = mutex.withLock {
        initializeLocked()
        val account = _accounts.value.firstOrNull { it.localId == localId }
            ?: return@withLock null
        if (account.invalid || decryptToken(account) == null) {
            if (!account.invalid) {
                val updated = _accounts.value.map {
                    if (it.localId == localId) it.copy(invalid = true) else it
                }
                writeLocked(updated, _activeAccount.value?.localId ?: localId)
            }
            return@withLock null
        }

        val updated = _accounts.value.map {
            if (it.localId == localId) it.copy(lastUsedAt = System.currentTimeMillis()) else it
        }
        writeLocked(updated, localId)
        _activeAccount.value
    }

    suspend fun updateProfile(
        localId: String,
        serverId: String,
        nickname: String,
        phone: String,
        avatarUrl: String,
    ): ProfileUpdateResult? = mutex.withLock {
        initializeLocked()
        val local = _accounts.value.firstOrNull { it.localId == localId }
            ?: return@withLock null
        val normalizedPhone = phone.trim()
        val hash = phoneHash(normalizedPhone)
        val phoneEncrypted = normalizedPhone.takeIf { it.isNotBlank() }?.let(cipher::encrypt)

        val updatedLocal = local.copy(
            serverId = serverId.trim().ifBlank { local.serverId },
            nickname = nickname.trim().ifBlank { local.nickname },
            avatarUrl = avatarUrl.trim().ifBlank { local.avatarUrl },
            phoneMasked = normalizedPhone.takeIf { it.isNotBlank() }?.let(::maskPhone)
                ?: local.phoneMasked,
            phoneHash = hash.ifBlank { local.phoneHash },
            phoneCipher = phoneEncrypted?.cipherText ?: local.phoneCipher,
            phoneIv = phoneEncrypted?.iv ?: local.phoneIv,
            pendingProfile = serverId.isBlank() && local.serverId.isBlank(),
            lastSyncedAt = System.currentTimeMillis(),
        )

        val mergeTarget = findMergeTarget(
            accounts = _accounts.value.filterNot { it.localId == localId },
            serverId = updatedLocal.serverId,
            phoneHash = updatedLocal.phoneHash,
        )
        val activeId = _activeAccount.value?.localId

        if (mergeTarget != null) {
            val merged = mergedAccount(mergeTarget, updatedLocal)
            val updated = _accounts.value
                .filterNot { it.localId == localId || it.localId == mergeTarget.localId } + merged
            writeLocked(updated, if (activeId == localId) merged.localId else activeId)
            return@withLock ProfileUpdateResult(merged, localId)
        }

        val updated = _accounts.value.map {
            if (it.localId == localId) updatedLocal else it
        }
        writeLocked(updated, activeId ?: localId)
        ProfileUpdateResult(updatedLocal)
    }

    suspend fun renameAlias(localId: String, alias: String) = mutex.withLock {
        initializeLocked()
        val updated = _accounts.value.map {
            if (it.localId == localId) it.copy(alias = alias.trim()) else it
        }
        writeLocked(updated, _activeAccount.value?.localId)
    }

    suspend fun delete(localId: String): StoredAccount? = mutex.withLock {
        initializeLocked()
        if (_accounts.value.none { it.localId == localId }) {
            return@withLock _activeAccount.value
        }
        val remaining = _accounts.value.filterNot { it.localId == localId }
        val activeId = _activeAccount.value?.localId
        val nextActiveId = if (activeId == localId) {
            nextAccountAfterDelete(remaining, localId)?.localId
        } else {
            activeId
        }
        writeLocked(remaining, nextActiveId)
        _activeAccount.value
    }

    suspend fun deleteActive(): StoredAccount? {
        val activeId = _activeAccount.value?.localId ?: return null
        return delete(activeId)
    }

    /** 退出登录但保留账号资料与接水记录，清除 token 并标记为已退出。 */
    suspend fun logoutKeep(localId: String): StoredAccount? = mutex.withLock {
        initializeLocked()
        if (_accounts.value.none { it.localId == localId }) {
            return@withLock _activeAccount.value
        }
        val updated = _accounts.value.map {
            if (it.localId == localId) {
                it.copy(
                    tokenCipher = "",
                    tokenIv = "",
                    invalid = true,
                    loggedOut = true,
                )
            } else {
                it
            }
        }
        val next = nextAccountAfterDelete(updated, localId)
            ?: updated.first { it.localId == localId }
        writeLocked(updated, next.localId)
        _activeAccount.value
    }

    suspend fun markInvalidIfActive(token: String): StoredAccount? = mutex.withLock {
        initializeLocked()
        if (token.isBlank() || currentToken != token) return@withLock null
        val active = _activeAccount.value ?: return@withLock null
        if (active.invalid) return@withLock null

        val updated = _accounts.value.map {
            if (it.localId == active.localId) it.copy(invalid = true) else it
        }
        writeLocked(updated, active.localId)
        _activeAccount.value
    }

    suspend fun getPhone(localId: String): String? = mutex.withLock {
        initializeLocked()
        val account = _accounts.value.firstOrNull { it.localId == localId }
            ?: return@withLock null
        if (account.phoneCipher.isBlank() || account.phoneIv.isBlank()) return@withLock null
        runCatching { cipher.decrypt(account.phoneCipher, account.phoneIv) }.getOrNull()
    }

    private suspend fun initializeLocked() {
        if (initialized) return

        val prefs = context.appDataStore.data.first()
        var accounts = decodeAccounts(prefs[ACCOUNTS])
        var activeId = prefs[ACTIVE]
        var needsWrite = false

        if (!prefs.contains(ACCOUNTS)) {
            val legacyCipher = prefs[LEGACY_CIPHER]
            val legacyIv = prefs[LEGACY_IV]
            if (legacyCipher != null && legacyIv != null) {
                val legacyToken = runCatching { cipher.decrypt(legacyCipher, legacyIv) }.getOrNull()
                if (!legacyToken.isNullOrBlank()) {
                    val account = StoredAccount(
                        localId = UUID.randomUUID().toString(),
                        tokenCipher = legacyCipher,
                        tokenIv = legacyIv,
                        pendingProfile = true,
                        lastUsedAt = System.currentTimeMillis(),
                    )
                    accounts = listOf(account)
                    activeId = account.localId
                }
            }
            needsWrite = true
        }

        var active = accounts.firstOrNull { it.localId == activeId }
            ?: accounts.filter { !it.invalid && it.tokenCipher.isNotBlank() }
                .maxByOrNull { it.lastUsedAt }
            ?: accounts.maxByOrNull { it.lastUsedAt }
        if (active != null && active.localId != activeId) {
            activeId = active.localId
            needsWrite = true
        }

        if (active != null && !active.invalid && decryptToken(active) == null) {
            accounts = accounts.map {
                if (it.localId == active.localId) it.copy(invalid = true) else it
            }
            active = accounts.first { it.localId == active.localId }
            needsWrite = true
        }

        if (needsWrite || !prefs.contains(ACCOUNTS)) {
            writeLocked(accounts, active?.localId)
        } else {
            applyInMemory(accounts, active?.localId)
            if (active != null && !active.invalid && currentToken == null) {
                val updated = accounts.map {
                    if (it.localId == active.localId) it.copy(invalid = true) else it
                }
                writeLocked(updated, active.localId)
            }
        }

        initialized = true
    }

    private suspend fun writeLocked(accounts: List<StoredAccount>, activeId: String?) {
        val encoded = json.encodeToString(
            ListSerializer(StoredAccount.serializer()),
            accounts,
        )
        context.appDataStore.edit { prefs ->
            prefs[ACCOUNTS] = encoded
            if (activeId == null) {
                prefs.remove(ACTIVE)
            } else {
                prefs[ACTIVE] = activeId
            }
            prefs.remove(LEGACY_CIPHER)
            prefs.remove(LEGACY_IV)
        }
        applyInMemory(accounts, activeId)
    }

    private fun applyInMemory(accounts: List<StoredAccount>, activeId: String?) {
        val sorted = sortAccounts(accounts)
        _accounts.value = sorted
        val active = sorted.firstOrNull { it.localId == activeId }
        _activeAccount.value = active
        currentToken = active?.takeIf { !it.invalid }?.let(::decryptToken)
    }

    private fun decodeAccounts(raw: String?): List<StoredAccount> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(StoredAccount.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun decryptToken(account: StoredAccount): String? {
        if (account.tokenCipher.isBlank() || account.tokenIv.isBlank()) return null
        return runCatching { cipher.decrypt(account.tokenCipher, account.tokenIv) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        val ACCOUNTS = stringPreferencesKey("accounts_v1")
        val ACTIVE = stringPreferencesKey("active_account_id")
        val LEGACY_CIPHER = stringPreferencesKey("token_cipher")
        val LEGACY_IV = stringPreferencesKey("token_iv")
    }
}
