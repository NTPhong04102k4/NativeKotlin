package com.example.application_ai_assisstant.data.model

/**
 * Một sự kiện trên màn Lịch trình.
 *
 * Thời điểm lưu dưới dạng epoch millis chứ không phải chuỗi đã format: format phụ thuộc
 * Locale của máy và là việc của tầng UI. Domain model chỉ giữ dữ liệu thô.
 */
data class ScheduleItem(
    val id: String,
    val title: String,
    /** Có thể rỗng — adapter sẽ ẩn dòng địa điểm khi không có. */
    val location: String,
    val startAtMillis: Long,
    val isDone: Boolean,
)
