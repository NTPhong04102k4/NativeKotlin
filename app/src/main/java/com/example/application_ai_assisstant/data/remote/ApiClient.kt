package com.example.application_ai_assisstant.data.remote

import com.example.application_ai_assisstant.BuildConfig
import com.example.application_ai_assisstant.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Điểm cấu hình mạng duy nhất của app.
 *
 * ĐỔI [BASE_URL] sang backend thật của bạn. Hiện đang trỏ tới jsonplaceholder
 * để slice Khám phá chạy được ngay mà không cần server.
 */
object ApiClient {

    private const val BASE_URL = "https://jsonplaceholder.typicode.com/"
    private const val TIMEOUT_SECONDS = 20L

    /**
     * Đính token vào mọi request tại một chỗ duy nhất — không Repository nào phải tự truyền header.
     * Đọc trực tiếp từ [SessionManager] nên luôn lấy được token mới nhất sau khi refresh.
     */
    private val authInterceptor = Interceptor { chain ->
        val token = SessionManager.token
        val request = chain.request().newBuilder()
            .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
