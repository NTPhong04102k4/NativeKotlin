package com.example.application_ai_assisstant.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.application_ai_assisstant.data.DiscoveryRepository
import com.example.application_ai_assisstant.data.SessionRepository

/**
 * Factory cho các ViewModel của màn Khám phá.
 *
 * Khác với LoginViewModelFactory (tự `new` repository bên trong), factory này NHẬN repository
 * từ ngoài vào — repository phải là singleton lấy từ AppContainer thì cache mới dùng chung được
 * giữa màn danh sách và màn chi tiết.
 */
class DiscoveryViewModelFactory(
    private val repository: DiscoveryRepository,
    private val sessionRepository: SessionRepository,
    private val itemId: String? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(DiscoveryViewModel::class.java) ->
            DiscoveryViewModel(repository, sessionRepository) as T

        modelClass.isAssignableFrom(DiscoveryDetailViewModel::class.java) ->
            DiscoveryDetailViewModel(repository, requireNotNull(itemId)) as T

        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
