package com.ntp.application_ai_assisstant.ui.discovery

import com.ntp.application_ai_assisstant.data.AppException
import com.ntp.application_ai_assisstant.data.model.DiscoveryCategory
import com.ntp.application_ai_assisstant.data.model.DiscoveryItem

/**
 * TOÀN BỘ state của màn Khám phá gói trong MỘT object.
 *
 * Vì sao không tách thành nhiều LiveData rời (isLoading, items, error…):
 * các biến rời cho phép những tổ hợp vô nghĩa (đang loading mà vẫn hiện lỗi cũ),
 * và View phải tự ghép chúng lại — mỗi chỗ ghép một kiểu. Một object thì View chỉ có
 * một hàm render() duy nhất, nhận state và vẽ ra đúng màn hình tương ứng.
 *
 * Dùng data class (thay vì sealed interface) vì màn này có nhiều thứ chạy song song:
 * vừa có danh sách, vừa có thể đang refresh, vừa đang lọc theo chip + ô tìm kiếm.
 */
data class DiscoveryUiState(
    /** Lần tải đầu tiên, chưa có gì để hiển thị -> che cả màn bằng ProgressBar. */
    val isLoading: Boolean = false,

    /** Đang tải lại trong khi vẫn có dữ liệu cũ -> chỉ hiện vòng xoay của SwipeRefreshLayout. */
    val isRefreshing: Boolean = false,

    /** Dữ liệu gốc từ repository, chưa lọc. */
    val allItems: List<DiscoveryItem> = emptyList(),

    /** null = chip "Tất cả". */
    val selectedCategory: DiscoveryCategory? = null,

    val searchQuery: String = "",

    /** Lỗi dai dẳng cần hiển thị trong layout (khác với lỗi thoáng qua -> dùng DiscoveryEvent). */
    val errorKind: AppException.Kind? = null,
) {
    /**
     * STATE DẪN XUẤT — tính từ các field trên chứ KHÔNG lưu riêng.
     *
     * Nếu lưu `visibleItems` thành một field riêng thì mỗi lần đổi chip hay gõ tìm kiếm
     * đều phải nhớ cập nhật nó; quên một chỗ là UI sai. Tính tại chỗ thì không thể sai lệch.
     */
    val visibleItems: List<DiscoveryItem>
        get() = allItems.filter { item ->
            val matchesCategory = selectedCategory == null || item.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }

    /** Chỉ hiện "không có kết quả" khi đã tải xong và thực sự rỗng. */
    val isEmpty: Boolean
        get() = !isLoading && errorKind == null && visibleItems.isEmpty()
}

/**
 * Sự kiện CHỈ XẢY RA MỘT LẦN: toast, điều hướng, mở dialog.
 *
 * Vì sao phải tách khỏi [DiscoveryUiState]: state được phát lại cho observer mới mỗi khi
 * Activity được tạo lại (xoay màn hình). Nếu nhét thông báo lỗi vào state thì cứ xoay máy
 * là toast lại nhảy ra một lần nữa. Event đi qua Channel nên chỉ được nhận đúng một lần.
 */
sealed interface DiscoveryEvent {
    data class ShowError(val kind: AppException.Kind) : DiscoveryEvent
    data class OpenDetail(val itemId: String) : DiscoveryEvent
    data object SessionExpired : DiscoveryEvent
}
