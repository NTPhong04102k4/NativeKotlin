package com.ntp.application_ai_assisstant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ntp.application_ai_assisstant.util.NotificationHelper

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationHelper = NotificationHelper(context)
        notificationHelper.showNotification("Báo thức", "Đã đến giờ hẹn!")
    }
}
