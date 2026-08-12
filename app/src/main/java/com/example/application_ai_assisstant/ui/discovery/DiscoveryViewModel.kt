package com.example.application_ai_assisstant.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.application_ai_assisstant.data.AppException
import com.example.application_ai_assisstant.data.DiscoveryRepository
import com.example.application_ai_assisstant.data.Result
import com.example.application_ai_assisstant.data.SessionRepository
import com.example.application_ai_assisstant.data.model.DiscoveryCategory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel của màn Khám phá.
 *
 * Quy tắc: KHÔNG import android.view.*, android.widget.*, Context hay R.layout ở đây.
 * ViewModel chỉ nhận ý định của người dùng (các hàm public bên dưới) và phát ra state.
 */
class DiscoveryViewModel(
    private val repository: DiscoveryRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    // BUFFERED để send() không bao giờ bị treo khi Activity đang ở background (không collect).
    private val _events = Channel<DiscoveryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Hiển thị cache trước để tránh màn hình trắng khi ViewModel được tạo lại,
        // rồi mới tải lại ngầm phía sau.
        _uiState.update { it.copy(allItems = repository.items.value) }
        load(isUserTriggered = false)
    }

    /** Người dùng kéo xuống để làm mới. */
    fun refresh() = load(isUserTriggered = true)

    /** Người dùng bấm "Thử lại" ở màn báo lỗi. */
    fun retry() = load(isUserTriggered = false)

    /** Người dùng chọn chip lọc. null = "Tất cả". */
    fun onCategorySelected(category: DiscoveryCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /** Người dùng gõ vào ô tìm kiếm. Lọc cục bộ trên cache nên không cần debounce. */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Người dùng bấm vào một thẻ. ViewModel không tự startActivity — nó phát event cho View. */
    fun onItemClicked(itemId: String) {
        viewModelScope.launch { _events.send(DiscoveryEvent.OpenDetail(itemId)) }
    }

    private fun load(isUserTriggered: Boolean) {
        // viewModelScope tự huỷ khi ViewModel bị clear -> không rò rỉ, không crash
        // vì cập nhật UI của một Activity đã chết.
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    // Chỉ che cả màn khi CHƯA có gì để hiển thị.
                    isLoading = !isUserTriggered && !repository.hasCache,
                    isRefreshing = isUserTriggered,
                    errorKind = null,
                )
            }

            when (val result = repository.refresh()) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        allItems = repository.items.value,
                    )
                }

                is Result.Error -> handleError(result.exception)
            }
        }
    }

    private suspend fun handleError(exception: Exception) {
        val kind = (exception as? AppException)?.kind ?: AppException.Kind.UNKNOWN

        if (kind == AppException.Kind.UNAUTHORIZED) {
            // Token hết hạn: dọn phiên + cache của MỌI repository rồi mới báo View chuyển màn.
            sessionRepository.logout()
            _events.send(DiscoveryEvent.SessionExpired)
            return
        }

        val hasData = repository.hasCache
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                // Còn dữ liệu cũ thì đừng thay cả màn hình bằng trang lỗi — chỉ báo bằng toast.
                errorKind = if (hasData) null else kind,
            )
        }
        if (hasData) {
            _events.send(DiscoveryEvent.ShowError(kind))
        }
    }
}
