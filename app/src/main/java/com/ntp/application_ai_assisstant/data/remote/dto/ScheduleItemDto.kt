package com.ntp.application_ai_assisstant.data.remote.dto

import com.ntp.application_ai_assisstant.data.model.ScheduleItem
import com.ntp.application_ai_assisstant.util.DateTimeUtils
import com.google.gson.annotations.SerializedName

data class ScheduleItemDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("userId") val userId: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("completed") val completed: Boolean?,
)

/**
 * DTO -> domain.
 *
 * `title` và `completed` là field THẬT của API demo (/todos). API đó không có giờ bắt đầu
 * lẫn địa điểm, nên [ScheduleItem.startAtMillis] tạm suy ra từ id để CalendarView có gì mà lọc,
 * còn `location` để rỗng. Khi nối backend thật, xoá phần suy diễn này và đọc field tương ứng.
 */
fun ScheduleItemDto.toDomain(nowMillis: Long = System.currentTimeMillis()): ScheduleItem {
    val safeId = id ?: 0
    val day = DateTimeUtils.plusDays(DateTimeUtils.startOfDay(nowMillis), safeId % DAY_SPREAD)
    return ScheduleItem(
        id = safeId.toString(),
        title = title.orEmpty().trim(),
        location = "",
        startAtMillis = DateTimeUtils.plusHours(day, FIRST_HOUR + safeId % HOUR_SPREAD),
        isDone = completed == true,
    )
}

/** Trải sự kiện ra 5 ngày kể từ hôm nay. */
private const val DAY_SPREAD = 5

/** Giờ sớm nhất một sự kiện có thể bắt đầu. */
private const val FIRST_HOUR = 7

/** Khoảng giờ trong ngày mà sự kiện rơi vào (7h -> 18h). */
private const val HOUR_SPREAD = 12
