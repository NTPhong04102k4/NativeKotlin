package com.example.application_ai_assisstant.di

import com.example.application_ai_assisstant.data.DiscoveryRepository
import com.example.application_ai_assisstant.data.LoginDataSource
import com.example.application_ai_assisstant.data.LoginRepository
import com.example.application_ai_assisstant.data.ScheduleRepository
import com.example.application_ai_assisstant.data.SessionRepository
import com.example.application_ai_assisstant.data.remote.ApiClient
import com.example.application_ai_assisstant.data.remote.ApiService

/**
 * Service Locator — nơi khởi tạo và chia sẻ các dependency sống theo vòng đời Application.
 *
 * Thay cho Hilt/Koin khi dự án còn nhỏ: không annotation processor, không thời gian build thêm.
 * Khi số màn hình vượt ~15 thì cân nhắc chuyển sang Hilt.
 *
 * Tất cả đều `by lazy` nên chỉ được tạo khi thực sự có màn hình cần đến.
 * Repository ở đây là SINGLETON — đó là điều làm cache dùng chung được giữa các màn hình
 * và sống sót khi một Activity bị huỷ.
 */
class AppContainer {

    val apiService: ApiService by lazy { ApiClient.service }

    val discoveryRepository: DiscoveryRepository by lazy { DiscoveryRepository(apiService) }

    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository(apiService) }

    val loginRepository: LoginRepository by lazy { LoginRepository(LoginDataSource()) }

    /**
     * Biết về mọi repository có cache gắn với user — thêm repository mới thì nhớ khai báo ở đây,
     * nếu không dữ liệu của nó sẽ sót lại sau khi đăng xuất.
     */
    val sessionRepository: SessionRepository by lazy {
        SessionRepository(discoveryRepository, scheduleRepository)
    }
}
