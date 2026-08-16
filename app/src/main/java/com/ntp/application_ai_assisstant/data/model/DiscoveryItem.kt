package com.ntp.application_ai_assisstant.data.model

/**
 * Model domain của một thẻ trên màn Khám phá.
 * Không nullable, không annotation của thư viện parse — tầng UI chỉ làm việc với class này.
 */
data class DiscoveryItem(
    val id: String,
    val title: String,
    val description: String,
    val category: DiscoveryCategory,
    val readingMinutes: Int,
)

/** Danh mục dùng cho dãy chip lọc ở đầu màn Khám phá. */
enum class DiscoveryCategory {
    TECHNOLOGY,
    HEALTH,
    LIFE,
}
