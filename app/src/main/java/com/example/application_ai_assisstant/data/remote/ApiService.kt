package com.example.application_ai_assisstant.data.remote

import com.example.application_ai_assisstant.data.remote.dto.DiscoveryItemDto
import com.example.application_ai_assisstant.data.remote.dto.ScheduleItemDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Khai báo toàn bộ endpoint của app.
 *
 * Mọi hàm đều là `suspend` — Retrofit tự chạy request off main thread và trả kết quả về,
 * không cần Call/enqueue/callback.
 */
interface ApiService {

    @GET("posts")
    suspend fun getDiscoveryItems(): List<DiscoveryItemDto>

    @GET("posts/{id}")
    suspend fun getDiscoveryItem(@Path("id") id: String): DiscoveryItemDto

    @GET("todos")
    suspend fun getScheduleItems(): List<ScheduleItemDto>
}
