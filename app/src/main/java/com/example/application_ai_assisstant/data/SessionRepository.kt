package com.example.application_ai_assisstant.data

import com.example.application_ai_assisstant.data.local.SessionManager
import com.example.application_ai_assisstant.data.model.LoggedInUser
import kotlinx.coroutines.flow.StateFlow

/**
 * Cửa vào duy nhất của tầng UI tới state phiên đăng nhập.
 *
 * VÌ SAO CẦN LỚP NÀY thay vì để ViewModel gọi thẳng SessionManager:
 * "đăng xuất" không chỉ là xoá token — còn phải dọn cache của MỌI repository, nếu không
 * người dùng tiếp theo sẽ thấy dữ liệu của người trước. Gom vào [logout] thì thêm một
 * repository mới chỉ cần sửa đúng một chỗ, không phải đi rà lại từng màn hình.
 *
 * Đây cũng là lý do AppRouter.logout() chỉ lo điều hướng: xoá dữ liệu là việc của tầng data.
 */
class SessionRepository(
    private val discoveryRepository: DiscoveryRepository,
    private val scheduleRepository: ScheduleRepository,
) {

    /** User hiện tại, hoặc null nếu chưa đăng nhập. Mọi màn hình đều `collect` cái này. */
    val currentUser: StateFlow<LoggedInUser?> = SessionManager.currentUser

    /** Xoá phiên + toàn bộ cache gắn với user. Gọi khi bấm Đăng xuất hoặc khi API trả 401. */
    fun logout() {
        SessionManager.clear()
        discoveryRepository.clear()
        scheduleRepository.clear()
    }
}
