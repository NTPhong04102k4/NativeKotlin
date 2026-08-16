package com.ntp.application_ai_assisstant

import android.app.Application
import android.content.Context
import com.ntp.application_ai_assisstant.data.local.SessionManager
import com.ntp.application_ai_assisstant.di.AppContainer

/**
 * Điểm khởi tạo sớm nhất của app — chạy trước mọi Activity.
 *
 * Phải khai báo `android:name=".AiAssistantApp"` trong thẻ <application> của AndroidManifest.xml,
 * nếu không class này sẽ không bao giờ được gọi.
 */
class AiAssistantApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Nạp phiên đăng nhập từ đĩa TRƯỚC khi bất kỳ màn hình nào đọc SessionManager
        SessionManager.init(this)
        container = AppContainer()
    }
}

/**
 * Cách truy cập AppContainer từ Activity/ViewModel:
 *
 *     val repo = appContainer.discoveryRepository
 */
val Context.appContainer: AppContainer
    get() = (applicationContext as AiAssistantApp).container
