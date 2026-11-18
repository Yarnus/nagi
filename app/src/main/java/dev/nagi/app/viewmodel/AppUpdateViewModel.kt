package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AppUpdateState
import dev.nagi.app.data.ReleaseUpdateRepository
import dev.nagi.app.data.UpdateDownloadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: ReleaseUpdateRepository,
) : ViewModel() {
    val state: StateFlow<AppUpdateState> = repository.state
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    init {
        checkIfNeeded()
    }

    fun checkIfNeeded() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            repository.checkIfNeeded()
        }
    }

    fun checkNow() {
        if (state.value.checking) return
        checkJob = viewModelScope.launch {
            repository.checkNow()
        }
    }

    fun downloadUpdate(onReadyToInstall: ((String) -> Unit)? = null) {
        if (downloadJob?.isActive == true) return
        val existing = state.value.download as? UpdateDownloadState.ReadyToInstall
        if (existing != null && !existing.needsInstallPermission) {
            onReadyToInstall?.invoke(existing.apkPath)
            return
        }
        downloadJob = viewModelScope.launch {
            val result = repository.downloadUpdate()
            if (result is UpdateDownloadState.ReadyToInstall && !result.needsInstallPermission) {
                onReadyToInstall?.invoke(result.apkPath)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun refreshInstallPermission() {
        repository.refreshInstallPermission()
    }

    fun resetManualStatus() {
        repository.resetManualStatus()
    }
}
