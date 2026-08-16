package com.ntp.application_ai_assisstant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import android.provider.Telephony

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Theo dõi cuộc gọi
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d("SystemEventReceiver", "Trạng thái cuộc gọi: $state")
            }
            // Theo dõi tin nhắn đến
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                Log.d("SystemEventReceiver", "Đã nhận tin nhắn mới!")
            }
        }
    }
}
