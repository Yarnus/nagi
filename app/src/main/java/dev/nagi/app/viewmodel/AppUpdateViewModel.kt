package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AppUpdateState
import dev.nagi.app.data.ReleaseUpdateRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: ReleaseUpdateRepository,
) : ViewModel() {
    val state: StateFlow<AppUpdateState> = repository.state

    init {
        checkIfNeeded()
    }

    fun checkIfNeeded() {
        viewModelScope.launch {
            repository.checkIfNeeded()
        }
    }
}
