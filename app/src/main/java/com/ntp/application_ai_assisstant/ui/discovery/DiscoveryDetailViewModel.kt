package com.ntp.application_ai_assisstant.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntp.application_ai_assisstant.data.AppException
import com.ntp.application_ai_assisstant.data.DiscoveryRepository
import com.ntp.application_ai_assisstant.data.Result
import com.ntp.application_ai_assisstant.data.model.DiscoveryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State của màn chi tiết — dùng SEALED INTERFACE thay vì data class.
 *
 * Đây là kiểu state phù hợp khi màn hình chỉ ở ĐÚNG MỘT trạng thái tại một thời điểm:
 * hoặc đang tải, hoặc có dữ liệu, hoặc lỗi. Trình biên dịch sẽ bắt buộc `when` phải xử lý
 * đủ cả ba nhánh, nên không thể quên trạng thái nào.
 *
 * (So sánh: DiscoveryUiState của màn danh sách dùng data class vì ở đó nhiều thứ chồng lên nhau.)
 */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val item: DiscoveryItem) : DetailUiState
    data class Error(val kind: AppException.Kind) : DetailUiState
}

class DiscoveryDetailViewModel(
    private val repository: DiscoveryRepository,
    private val itemId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            // Màn chi tiết tự lấy dữ liệu theo id thay vì nhận cả object qua Intent.
            // Nhờ vậy dữ liệu luôn khớp với repository, kể cả khi màn trước đã cũ.
            _uiState.value = when (val result = repository.getItem(itemId)) {
                is Result.Success -> DetailUiState.Success(result.data)
                is Result.Error -> DetailUiState.Error(
                    (result.exception as? AppException)?.kind ?: AppException.Kind.UNKNOWN,
                )
            }
        }
    }
}
