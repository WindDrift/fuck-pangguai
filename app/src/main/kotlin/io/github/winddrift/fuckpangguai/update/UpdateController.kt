package io.github.winddrift.fuckpangguai.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateController internal constructor(
    private val scope: CoroutineScope,
    private val source: ReleaseSource,
    private val currentVersion: String,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkedOnStart = false
    private var job: Job? = null

    /**
     * 每个进程只自动检查一次。所有失败保持静默，不打扰用户。
     */
    fun checkOnStart() {
        if (checkedOnStart) return
        checkedOnStart = true
        launchCheck(silent = true)
    }

    /**
     * 用户主动检查。失败时会进入 [UpdateState.Failed]，供关于页展示原因。
     */
    fun checkManually() {
        launchCheck(silent = false)
    }

    private fun launchCheck(silent: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            val previous = _state.value
            _state.value = UpdateState.Checking
            try {
                val result = evaluate(source.latest())
                _state.value = if (silent && result is UpdateState.Failed) {
                    previous.takeIf { it is UpdateState.Available } ?: UpdateState.Idle
                } else {
                    result
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.value = if (silent) {
                    previous.takeIf { it is UpdateState.Available } ?: UpdateState.Idle
                } else {
                    UpdateState.Failed(throwable.toUpdateError())
                }
            }
        }
    }

    private fun evaluate(release: GithubRelease): UpdateState {
        if (release.draft || release.prerelease) return UpdateState.UpToDate

        val remote = parseReleaseVersion(release.tagName)
            ?: return UpdateState.Failed(UpdateError.Unknown)
        val local = parseReleaseVersion(currentVersion)
            ?: return UpdateState.Failed(UpdateError.Unknown)
        if (remote <= local) return UpdateState.UpToDate

        return UpdateState.Available(
            version = release.tagName.trim().removePrefix("v").removePrefix("V"),
            tag = release.tagName,
            notes = releaseNotesToPlainText(release.body),
            releaseUrl = release.htmlUrl,
        )
    }
}
