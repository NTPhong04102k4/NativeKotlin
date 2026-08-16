package com.ntp.application_ai_assisstant.ui.personal

/**
 * State màn Cá nhân.
 *
 * Khác hai màn kia: ở đây KHÔNG có isLoading/error vì màn này không gọi API — nó chỉ phản chiếu
 * state toàn cục [com.ntp.application_ai_assisstant.data.SessionRepository.currentUser].
 * State nào không thể xảy ra thì đừng khai báo, nếu không View sẽ phải xử lý những nhánh chết.
 */
data class PersonalUiState(
    val displayName: String = "",
    val userId: String = "",
    val isLoggedIn: Boolean = false,
)

sealed interface PersonalEvent {
    /** Đã xoá xong phiên, View chỉ còn việc điều hướng. */
    data object NavigateToLogin : PersonalEvent
}
