package io.github.winddrift.fuckpangguai

import io.github.winddrift.fuckpangguai.account.AccountRepository
import io.github.winddrift.fuckpangguai.account.AuthTarget
import io.github.winddrift.fuckpangguai.account.displayName
import io.github.winddrift.fuckpangguai.net.SessionExpiryNotifier
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.storage.DisclaimerStore
import io.github.winddrift.fuckpangguai.water.SessionPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppState {
    data object Loading : AppState
    data object Disclaimer : AppState
    data object Login : AppState
    data object Ready : AppState
}

data class AppMessage(
    val text: String,
    val isError: Boolean = false,
)

class AppController(
    private val scope: CoroutineScope,
    private val accountRepository: AccountRepository,
    private val disclaimerStore: DisclaimerStore,
    private val sessionExpiryNotifier: SessionExpiryNotifier,
    private val sessionPhaseProvider: () -> String?,
) {
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _message = MutableStateFlow<AppMessage?>(null)
    val message = _message.asStateFlow()

    private val _codeSent = MutableStateFlow(false)
    val codeSent = _codeSent.asStateFlow()

    private val _authTarget = MutableStateFlow<AuthTarget>(AuthTarget.None)
    val authTarget = _authTarget.asStateFlow()

    private val _reauthPhone = MutableStateFlow("")
    val reauthPhone = _reauthPhone.asStateFlow()

    private val _authCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val authCompleted = _authCompleted.asSharedFlow()

    private val _accountChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val accountChanged = _accountChanged.asSharedFlow()

    val accounts = accountRepository.accounts
    val activeAccount = accountRepository.activeAccount

    init {
        scope.launch {
            sessionExpiryNotifier.events.collect(::handleSessionExpiry)
        }
        scope.launch {
            if (disclaimerStore.isAccepted()) {
                refreshAuth()
            } else {
                _state.value = AppState.Disclaimer
            }
        }
    }

    fun acceptDisclaimer() {
        scope.launch {
            disclaimerStore.accept()
            refreshAuth()
        }
    }

    fun sendCode(phone: String) {
        if (!PHONE_PATTERN.matches(phone)) {
            _message.value = AppMessage("请输入 11 位手机号", isError = true)
            return
        }
        launchBusy {
            accountRepository.sendCode(phone)
            _codeSent.value = true
            _message.value = AppMessage("验证码已发送，请查看短信")
        }
    }

    fun login(phone: String, code: String) {
        if (!PHONE_PATTERN.matches(phone)) {
            _message.value = AppMessage("请输入 11 位手机号", isError = true)
            return
        }
        if (code.isBlank()) {
            _message.value = AppMessage("请输入验证码", isError = true)
            return
        }
        launchBusy {
            accountRepository.loginAndActivate(phone, code)
            completeAuthentication()
        }
    }

    fun importToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            _message.value = AppMessage("token 不能为空", isError = true)
            return
        }
        launchBusy {
            accountRepository.importAndActivate(trimmed)
            completeAuthentication()
        }
    }

    fun beginAddAccount() {
        _authTarget.value = AuthTarget.AddAccount
        _reauthPhone.value = ""
        _codeSent.value = false
        _message.value = null
    }

    fun beginReauth(localId: String) {
        _authTarget.value = AuthTarget.Reauth(localId)
        _codeSent.value = false
        _message.value = null
        scope.launch {
            _reauthPhone.value = accountRepository.getPhone(localId).orEmpty()
        }
    }

    fun cancelAuth() {
        _authTarget.value = AuthTarget.None
        _reauthPhone.value = ""
        _codeSent.value = false
    }

    fun switchAccount(localId: String) {
        if (!canChangeAccount()) return
        launchBusy {
            val account = accountRepository.switchTo(localId)
            if (account == null) {
                _message.value = AppMessage("该账号已失效，请重新登录", isError = true)
                beginReauth(localId)
                return@launchBusy
            }
            _state.value = AppState.Ready
            _authTarget.value = AuthTarget.None
            _codeSent.value = false
            _message.value = AppMessage("已切换到 ${displayName(account)}")
            _authCompleted.tryEmit(Unit)
            _accountChanged.tryEmit(Unit)
            refreshProfileInBackground()
        }
    }

    fun renameAccount(localId: String, alias: String) {
        launchBusy {
            accountRepository.rename(localId, alias)
            _message.value = AppMessage("已重命名")
        }
    }

    fun deleteAccount(localId: String) {
        if (!canChangeAccount()) return
        launchBusy {
            val wasActive = accountRepository.activeLocalId() == localId
            accountRepository.delete(localId)
            val active = accountRepository.hasActiveSession()
            val next = accountRepository.activeAccount.value
            val message = when {
                !wasActive -> "账号已移除"
                active && next != null -> "账号已移除，已切换到 ${displayName(next)}"
                else -> "账号已移除"
            }
            updateStateAfterAccountRemoval(active, message)
            _accountChanged.tryEmit(Unit)
        }
    }

    fun logoutCurrent() {
        if (!canChangeAccount()) return
        launchBusy {
            accountRepository.logoutActive()
            val active = accountRepository.hasActiveSession()
            val next = accountRepository.activeAccount.value
            val message = if (active && next != null) {
                "已退出当前账号，已切换到 ${displayName(next)}"
            } else {
                "已退出当前账号"
            }
            updateStateAfterAccountRemoval(active, message)
            _accountChanged.tryEmit(Unit)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun completeAuthentication() {
        _message.value = null
        _codeSent.value = false
        _authTarget.value = AuthTarget.None
        _state.value = AppState.Ready
        _authCompleted.tryEmit(Unit)
        _accountChanged.tryEmit(Unit)
        refreshProfileInBackground()
    }

    private fun updateStateAfterAccountRemoval(hasActiveSession: Boolean, message: String) {
        _codeSent.value = false
        _message.value = AppMessage(message)
        if (hasActiveSession) {
            _state.value = AppState.Ready
            _authTarget.value = AuthTarget.None
            return
        }
        _state.value = AppState.Login
        val remaining = accountRepository.activeAccount.value
        _authTarget.value = if (remaining != null && remaining.invalid) {
            AuthTarget.Reauth(remaining.localId)
        } else {
            AuthTarget.None
        }
        if (remaining != null && remaining.invalid) {
            scope.launch {
                _reauthPhone.value = accountRepository.getPhone(remaining.localId).orEmpty()
            }
        } else {
            _reauthPhone.value = ""
        }
    }

    private suspend fun handleSessionExpiry(token: String) {
        val account = accountRepository.markInvalidIfActive(token) ?: return
        _state.value = AppState.Login
        _authTarget.value = AuthTarget.Reauth(account.localId)
        _reauthPhone.value = accountRepository.getPhone(account.localId).orEmpty()
        _message.value = AppMessage("登录已失效，请重新登录", isError = true)
        _accountChanged.tryEmit(Unit)
    }

    private fun refreshAuth() {
        scope.launch {
            _state.value = AppState.Loading
            try {
                accountRepository.initialize()
                if (accountRepository.hasActiveSession()) {
                    _state.value = AppState.Ready
                    refreshProfileInBackground()
                } else {
                    val active = accountRepository.activeAccount.value
                    _authTarget.value = if (active != null && active.invalid) {
                        AuthTarget.Reauth(active.localId)
                    } else {
                        AuthTarget.None
                    }
                    _reauthPhone.value = active?.let {
                        accountRepository.getPhone(it.localId).orEmpty()
                    }.orEmpty()
                    _state.value = AppState.Login
                }
            } catch (throwable: Throwable) {
                _message.value = AppMessage(throwable.userMessage(), isError = true)
                _state.value = AppState.Login
            }
        }
    }

    private fun refreshProfileInBackground() {
        scope.launch {
            runCatching { accountRepository.refreshProfile() }
        }
    }

    private fun canChangeAccount(): Boolean {
        if (sessionPhaseProvider() in SessionPhase.ACTIVE) {
            _message.value = AppMessage("接水进行中，暂不能切换账号", isError = true)
            return false
        }
        return true
    }

    private fun launchBusy(block: suspend () -> Unit) {
        scope.launch {
            _busy.value = true
            try {
                block()
            } catch (throwable: Throwable) {
                _message.value = AppMessage(throwable.userMessage(), isError = true)
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        val PHONE_PATTERN = Regex("^1\\d{10}$")
    }
}
