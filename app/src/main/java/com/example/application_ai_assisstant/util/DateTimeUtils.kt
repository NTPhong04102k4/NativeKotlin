package com.example.application_ai_assisstant.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tiện ích ngày giờ dùng [Calendar] chứ KHÔNG dùng java.time.
 *
 * minSdk của dự án là 24, trong khi java.time (LocalDate/LocalTime) chỉ có sẵn từ API 26.
 * Muốn dùng java.time thì phải bật core library desugaring trong build.gradle.kts —
 * chừng nào chưa bật thì Calendar là lựa chọn an toàn.
 */
object DateTimeUtils {

    /** Mốc 00:00:00.000 của ngày chứa [millis]. Dùng làm khoá so sánh "cùng ngày". */
    fun startOfDay(millis: Long): Long = calendarOf(millis).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Cộng [days] ngày — dùng Calendar để không sai lệch khi đổi giờ mùa hè. */
    fun plusDays(millis: Long, days: Int): Long = calendarOf(millis).apply {
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    fun plusHours(millis: Long, hours: Int): Long = calendarOf(millis).apply {
        add(Calendar.HOUR_OF_DAY, hours)
    }.timeInMillis

    fun isSameDay(first: Long, second: Long): Boolean = startOfDay(first) == startOfDay(second)

    /** "08:30" */
    fun formatTime(millis: Long): String = TIME_FORMAT.format(Date(millis))

    /** "AM" / "PM" (theo Locale của máy) */
    fun formatPeriod(millis: Long): String = PERIOD_FORMAT.format(Date(millis))

    private fun calendarOf(millis: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = millis }

    private val TIME_FORMAT = SimpleDateFormat("hh:mm", Locale.getDefault())
    private val PERIOD_FORMAT = SimpleDateFormat("a", Locale.getDefault())
}
