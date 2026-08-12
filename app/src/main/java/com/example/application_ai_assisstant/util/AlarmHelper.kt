package com.example.application_ai_assisstant.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.application_ai_assisstant.receiver.AlarmReceiver

class AlarmHelper(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Từ Android 12 (API 31), báo thức chính xác cần quyền SCHEDULE_EXACT_ALARM mà người dùng phải
     * tự bật trong Cài đặt — khai báo trong manifest là chưa đủ. Nếu chưa có quyền thì hạ cấp
     * xuống báo thức không chính xác thay vì để hệ thống ném SecurityException.
     *
     * @return `true` nếu đặt được báo thức chính xác, `false` nếu đã phải hạ cấp.
     */
    fun setExactAlarm(timeInMillis: Long): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
            true
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
            false
        }
    }

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    /**
     * Mở màn hình hệ thống để người dùng cấp quyền báo thức chính xác. Gọi khi
     * [setExactAlarm] trả về `false` và tính năng thực sự cần độ chính xác.
     */
    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
