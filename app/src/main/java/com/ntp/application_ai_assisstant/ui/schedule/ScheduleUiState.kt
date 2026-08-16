package com.ntp.application_ai_assisstant.ui.schedule

import com.ntp.application_ai_assisstant.data.AppException
import com.ntp.application_ai_assisstant.data.model.ScheduleItem
import com.ntp.application_ai_assisstant.util.DateTimeUtils

/**
 * Toàn bộ state của màn Lịch trình.
 *
 * Cùng khuôn với DiscoveryUiState — chỉ khác điều kiện lọc: ở đây là ngày đang chọn
 * trên CalendarView thay vì chip danh mục.
 */
data class ScheduleUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val allItems: List<ScheduleItem> = emptyList(),

    /** Ngày đang chọn trên CalendarView. Mặc định là hôm nay. */
    val selectedDateMillis: Long = System.currentTimeMillis(),

    val errorKind: AppException.Kind? = null,
) {
    /**
     * State dẫn xuất: sự kiện của riêng ngày đang chọn.
     *
     * CalendarView là view có state nội tại (nó tự nhớ ô nào đang sáng), nhưng cái quyết định
     * danh sách hiển thị vẫn là [selectedDateMillis] trong state này. View chỉ báo cáo thao tác
     * lên ViewModel, không tự lọc — nhờ vậy xoay màn hình hay tải lại dữ liệu vẫn ra đúng danh sách.
     */
    val visibleItems: List<ScheduleItem>
        get() = allItems.filter { DateTimeUtils.isSameDay(it.startAtMillis, selectedDateMillis) }

    val isEmpty: Boolean
        get() = !isLoading && errorKind == null && visibleItems.isEmpty()
}

sealed interface ScheduleEvent {
    data class ShowError(val kind: AppException.Kind) : ScheduleEvent
    data object SessionExpired : ScheduleEvent
}
