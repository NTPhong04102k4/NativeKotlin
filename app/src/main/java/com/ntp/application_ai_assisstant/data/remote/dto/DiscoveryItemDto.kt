package com.ntp.application_ai_assisstant.data.remote.dto

import com.ntp.application_ai_assisstant.data.model.DiscoveryCategory
import com.ntp.application_ai_assisstant.data.model.DiscoveryItem
import com.google.gson.annotations.SerializedName

/**
 * DTO — ánh xạ 1-1 với JSON của backend.
 *
 * DTO KHÔNG được rò lên tầng UI: mọi annotation của Gson, tên field kiểu snake_case,
 * kiểu nullable "vì server có thể thiếu"… đều dừng lại ở đây. Tầng UI chỉ nhìn thấy
 * [DiscoveryItem] — model domain sạch, không nullable, không phụ thuộc thư viện parse.
 */
data class DiscoveryItemDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("userId") val userId: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("body") val body: String?,
)

/** Chuyển DTO -> domain model, đồng thời đặt giá trị mặc định cho field thiếu. */
fun DiscoveryItemDto.toDomain(): DiscoveryItem {
    val content = body.orEmpty().trim()
    return DiscoveryItem(
        id = id?.toString().orEmpty(),
        title = title.orEmpty().trim(),
        description = content,
        // API demo (jsonplaceholder) không có trường category/thời lượng đọc.
        // Tạm suy ra để minh hoạ bộ lọc — thay bằng field thật khi nối backend của bạn.
        category = DiscoveryCategory.entries[(userId ?: 0) % DiscoveryCategory.entries.size],
        readingMinutes = (content.length / 200).coerceAtLeast(1),
    )
}
