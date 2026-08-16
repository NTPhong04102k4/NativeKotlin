package com.ntp.application_ai_assisstant.data

import com.ntp.application_ai_assisstant.data.model.DiscoveryItem
import com.ntp.application_ai_assisstant.data.remote.ApiService
import com.ntp.application_ai_assisstant.data.remote.dto.toDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nguồn sự thật (single source of truth) cho dữ liệu màn Khám phá.
 *
 * VÌ SAO CACHE Ở TẦNG REPOSITORY CHỨ KHÔNG Ở VIEWMODEL:
 * ViewModel chỉ sống bằng Activity của nó. Nó sống sót qua xoay màn hình và qua việc chuyển tab
 * (AppRouter dùng FLAG_ACTIVITY_REORDER_TO_FRONT nên tab không bị huỷ), nhưng KHÔNG sống sót khi
 * hệ thống thu hồi Activity ở background, hay khi màn Chi tiết cần cùng dữ liệu đó.
 *
 * Repository nằm trong AppContainer (sống theo Application) nên nó là nơi duy nhất giữ sự thật:
 * mọi ViewModel đọc chung một cache, không màn nào phải gọi lại API cho dữ liệu màn khác đã có.
 */
class DiscoveryRepository(private val api: ApiService) {

    private val _items = MutableStateFlow<List<DiscoveryItem>>(emptyList())

    /** Dữ liệu đã cache. ViewModel hiển thị cái này ngay, rồi gọi [refresh] ngầm phía sau. */
    val items: StateFlow<List<DiscoveryItem>> = _items.asStateFlow()

    val hasCache: Boolean
        get() = _items.value.isNotEmpty()

    /** Tải lại danh sách từ API và ghi đè cache. */
    suspend fun refresh(): Result<Unit> = safeApiCall {
        _items.value = api.getDiscoveryItems().map { it.toDomain() }
    }

    /**
     * Lấy chi tiết một mục.
     * Ưu tiên cache để mở màn chi tiết không phải chờ mạng; không có thì mới gọi API
     * (trường hợp app bị hệ thống kill rồi khôi phục thẳng vào màn chi tiết).
     */
    suspend fun getItem(id: String): Result<DiscoveryItem> {
        _items.value.firstOrNull { it.id == id }?.let { return Result.Success(it) }
        return safeApiCall { api.getDiscoveryItem(id).toDomain() }
    }

    /** Xoá cache — gọi khi đăng xuất để dữ liệu của user cũ không lẫn sang user mới. */
    fun clear() {
        _items.value = emptyList()
    }
}
