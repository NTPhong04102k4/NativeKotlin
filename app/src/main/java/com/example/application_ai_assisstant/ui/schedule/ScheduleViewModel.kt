package com.example.application_ai_assisstant.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.application_ai_assisstant.data.AppException
import com.example.application_ai_assisstant.data.Result
import com.example.application_ai_assisstant.data.ScheduleRepository
import com.example.application_ai_assisstant.data.SessionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel của màn Lịch trình — cùng khuôn với DiscoveryViewModel.
 */
class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _events = Channel<ScheduleEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        _uiState.update { it.copy(allItems = repository.items.value) }
        load(isUserTriggered = false)
    }

    fun refresh() = load(isUserTriggered = true)

    fun retry() = load(isUserTriggered = false)

    /** Người dùng chạm một ngày trên CalendarView. */
    fun onDateSelected(dateMillis: Long) {
        _uiState.update { it.copy(selectedDateMillis = dateMillis) }
    }

    private fun load(isUserTriggered: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isUserTriggered && !repository.hasCache,
                    isRefreshing = isUserTriggered,
                    errorKind = null,
                )
            }

            when (val result = repository.refresh()) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        allItems = repository.items.value,
                    )
                }

                is Result.Error -> handleError(result.exception)
            }
        }
    }

    private suspend fun handleError(exception: Exception) {
        val kind = (exception as? AppException)?.kind ?: AppException.Kind.UNKNOWN

        if (kind == AppException.Kind.UNAUTHORIZED) {
            sessionRepository.logout()
            _events.send(ScheduleEvent.SessionExpired)
            return
        }

        val hasData = repository.hasCache
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                // Còn dữ liệu cũ thì giữ nguyên màn hình, chỉ báo lỗi bằng toast.
                errorKind = if (hasData) null else kind,
            )
        }
        if (hasData) {
            _events.send(ScheduleEvent.ShowError(kind))
        }
    }
}
