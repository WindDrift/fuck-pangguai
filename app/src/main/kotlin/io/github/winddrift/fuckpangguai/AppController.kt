package io.github.winddrift.fuckpangguai

import io.github.winddrift.fuckpangguai.auth.AuthRepository
import io.github.winddrift.fuckpangguai.net.userMessage
import io.github.winddrift.fuckpangguai.storage.DisclaimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val authRepository: AuthRepository,
    private val disclaimerStore: DisclaimerStore,
) {
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _message = MutableStateFlow<AppMessage?>(null)
    val message = _message.asStateFlow()

    private val _codeSent = MutableStateFlow(false)
    val codeSent = _codeSent.asStateFlow()

    init {
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
            authRepository.sendCode(phone)
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
            authRepository.login(phone, code)
            _message.value = null
            _state.value = AppState.Ready
        }
    }

    fun importToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            _message.value = AppMessage("token 不能为空", isError = true)
            return
        }
        launchBusy {
            authRepository.importToken(trimmed)
            _message.value = AppMessage("token 已导入")
            _state.value = AppState.Ready
        }
    }

    fun logout() {
        scope.launch {
            authRepository.logout()
            _codeSent.value = false
            _message.value = null
            _state.value = AppState.Login
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun refreshAuth() {
        scope.launch {
            _state.value = AppState.Loading
            try {
                _state.value = if (authRepository.checkLogin()) {
                    AppState.Ready
                } else {
                    AppState.Login
                }
            } catch (throwable: Throwable) {
                _message.value = AppMessage(throwable.userMessage(), isError = true)
                _state.value = AppState.Login
            }
        }
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
