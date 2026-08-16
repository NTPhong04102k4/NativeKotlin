package com.ntp.application_ai_assisstant.data

import com.ntp.application_ai_assisstant.data.model.ScheduleItem
import com.ntp.application_ai_assisstant.data.remote.ApiService
import com.ntp.application_ai_assisstant.data.remote.dto.toDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nguồn sự thật cho màn Lịch trình. Cùng khuôn với [DiscoveryRepository]:
 * cache trong StateFlow, `refresh()` bọc bằng [safeApiCall], `clear()` cho lúc đăng xuất.
 */
class ScheduleRepository(private val api: ApiService) {

    private val _items = MutableStateFlow<List<ScheduleItem>>(emptyList())
    val items: StateFlow<List<ScheduleItem>> = _items.asStateFlow()

    val hasCache: Boolean
        get() = _items.value.isNotEmpty()

    suspend fun refresh(): Result<Unit> = safeApiCall {
        // Sắp xếp ngay ở tầng data để mọi màn dùng chung một thứ tự,
        // ViewModel không phải nhớ sort lại.
        _items.value = api.getScheduleItems()
            .map { it.toDomain() }
            .sortedBy { it.startAtMillis }
    }

    fun clear() {
        _items.value = emptyList()
    }
}
