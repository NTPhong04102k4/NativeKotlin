package com.ntp.application_ai_assisstant.ui.discovery

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ntp.application_ai_assisstant.R
import com.ntp.application_ai_assisstant.appContainer
import com.ntp.application_ai_assisstant.data.model.DiscoveryCategory
import com.ntp.application_ai_assisstant.databinding.ActivityDiscoveryBinding
import com.ntp.application_ai_assisstant.ui.common.toMessageRes
import com.ntp.application_ai_assisstant.util.AppRouter
import com.ntp.application_ai_assisstant.util.setupBottomNavigation
import kotlinx.coroutines.launch

/**
 * Màn Khám phá — mẫu tham chiếu cho toàn bộ các màn còn lại.
 *
 * Activity ở đây chỉ làm ĐÚNG BA việc:
 *  1. Dựng view và chuyển thao tác của người dùng thành lời gọi vào ViewModel.
 *  2. [render] — nhận DiscoveryUiState và vẽ ra màn hình.
 *  3. [handleEvent] — xử lý các sự kiện một lần (toast, điều hướng).
 *
 * Không có `if` nào phụ thuộc vào "lần trước đã hiện gì": mọi thứ suy ra từ state hiện tại.
 */
class DiscoveryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiscoveryBinding

    private val viewModel: DiscoveryViewModel by viewModels {
        DiscoveryViewModelFactory(appContainer.discoveryRepository, appContainer.sessionRepository)
    }

    private val adapter = DiscoveryAdapter { item -> viewModel.onItemClicked(item.id) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation(binding.bottomNavigation, R.id.nav_discovery)
        setupViews()
        observeViewModel()
    }

    private fun setupViews() = with(binding) {
        rvDiscovery.layoutManager = LinearLayoutManager(this@DiscoveryActivity)
        rvDiscovery.adapter = adapter

        swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        btnRetry.setOnClickListener { viewModel.retry() }

        etSearch.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        // ChipGroup ở chế độ singleSelection nên chỉ có một id được chọn tại một thời điểm.
        // (Material 1.5.0 — bản mới hơn dùng setOnCheckedStateChangeListener nhận danh sách id.)
        chipGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            val category = when (checkedId) {
                R.id.chip_technology -> DiscoveryCategory.TECHNOLOGY
                R.id.chip_health -> DiscoveryCategory.HEALTH
                R.id.chip_life -> DiscoveryCategory.LIFE
                else -> null // chip "Tất cả"
            }
            viewModel.onCategorySelected(category)
        }
    }

    private fun observeViewModel() {
        // repeatOnLifecycle: dừng collect khi Activity vào onStop và chạy lại khi onStart.
        // Nếu chỉ dùng lifecycleScope.launch trần, coroutine vẫn chạy lúc app ở background —
        // tốn tài nguyên và có thể cập nhật UI không nhìn thấy.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    /** Hàm thuần: state vào -> giao diện ra. Không đọc trạng thái hiện tại của view. */
    private fun render(state: DiscoveryUiState) = with(binding) {
        progressBar.isVisible = state.isLoading
        swipeRefresh.isRefreshing = state.isRefreshing
        layoutError.isVisible = state.errorKind != null
        tvEmpty.isVisible = state.isEmpty
        swipeRefresh.isVisible = !state.isLoading && state.errorKind == null

        state.errorKind?.let { tvError.setText(it.toMessageRes()) }
        adapter.submitList(state.visibleItems)
    }

    private fun handleEvent(event: DiscoveryEvent) {
        when (event) {
            is DiscoveryEvent.ShowError ->
                Toast.makeText(this, event.kind.toMessageRes(), Toast.LENGTH_SHORT).show()

            is DiscoveryEvent.OpenDetail ->
                startActivity(DiscoveryDetailActivity.newIntent(this, event.itemId))

            DiscoveryEvent.SessionExpired -> {
                Toast.makeText(this, R.string.error_unauthorized, Toast.LENGTH_LONG).show()
                AppRouter.logout(this)
            }
        }
    }
}
