package com.ntp.application_ai_assisstant.ui.personal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntp.application_ai_assisstant.data.SessionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel màn Cá nhân — ví dụ về việc BIẾN ĐỔI global state thành UI state.
 *
 * Không có MutableStateFlow nào ở đây: [uiState] được DẪN XUẤT trực tiếp từ
 * `sessionRepository.currentUser` bằng `map` + `stateIn`. Hệ quả: đăng nhập ở màn khác,
 * hay `logout()` từ bất kỳ đâu, màn này tự cập nhật mà không cần một dòng code đồng bộ nào.
 *
 * Đây chính là thứ LiveData làm rất vụng (phải Transformations.map + MediatorLiveData) còn
 * Flow làm bằng một toán tử.
 */
class PersonalViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<PersonalUiState> = sessionRepository.currentUser
        .map { user ->
            PersonalUiState(
                displayName = user?.displayName.orEmpty(),
                userId = user?.userId.orEmpty(),
                isLoggedIn = user != null,
            )
        }
        .stateIn(
            scope = viewModelScope,
            // WhileSubscribed(5000): ngừng thu thập khi không còn ai quan sát, nhưng chờ 5 giây
            // để việc xoay màn hình không làm huỷ rồi khởi động lại upstream một cách vô ích.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PersonalUiState(),
        )

    private val _events = Channel<PersonalEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Xoá phiên TRƯỚC rồi mới báo View điều hướng.
     * Nếu làm ngược lại, LoginActivity có thể khởi động trong lúc token cũ vẫn còn.
     */
    fun onLogoutClicked() {
        viewModelScope.launch {
            sessionRepository.logout()
            _events.send(PersonalEvent.NavigateToLogin)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
