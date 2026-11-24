package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AppUpdateState
import dev.nagi.app.data.PreferenceStore
import dev.nagi.app.data.ReleaseUpdateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: ReleaseUpdateRepository,
    private val preferences: PreferenceStore,
) : ViewModel() {
    val state: StateFlow<AppUpdateState> = repository.state
    private var checkJob: Job? = null

    init {
        checkIfNeeded()
    }

    fun checkIfNeeded() {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            val autoCheck = preferences.preferences.first().autoCheckUpdates
            repository.checkIfNeeded(allowNetwork = autoCheck)
        }
    }

    fun checkNow() {
        if (state.value.checking) return
        checkJob = viewModelScope.launch {
            repository.checkNow()
        }
    }

    fun resetManualStatus() {
        repository.resetManualStatus()
    }
}
