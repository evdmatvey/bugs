package com.example.bugs.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bugs.data.BugsRepository
import com.example.bugs.data.DeviceData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val device: DeviceData,
    val selecting: Boolean = false,
    val selectionError: Boolean = false
)

class ProfileViewModel(private val repository: BugsRepository) : ViewModel() {

    private val state = MutableStateFlow(ProfileUiState(repository.device.value))

    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.device.collect { device ->
                state.value = state.value.copy(device = device)
            }
        }
    }

    fun reload() {
        state.value = state.value.copy(selectionError = false)
        repository.reload()
    }

    fun selectPlayer(id: Long) {
        val current = state.value
        if (current.selecting || current.device.loading || current.device.error) return

        state.value = current.copy(selecting = true, selectionError = false)

        viewModelScope.launch {
            try {
                repository.selectPlayer(id)
                state.value = state.value.copy(selecting = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value = state.value.copy(selecting = false, selectionError = true)
            }
        }
    }
}
