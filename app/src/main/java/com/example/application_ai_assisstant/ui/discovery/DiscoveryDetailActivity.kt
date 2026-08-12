package com.example.application_ai_assisstant.ui.discovery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.application_ai_assisstant.R
import com.example.application_ai_assisstant.appContainer
import com.example.application_ai_assisstant.data.model.DiscoveryItem
import com.example.application_ai_assisstant.databinding.ActivityDiscoveryDetailBinding
import com.example.application_ai_assisstant.ui.common.toLabelRes
import com.example.application_ai_assisstant.ui.common.toMessageRes
import kotlinx.coroutines.launch

/**
 * Màn chi tiết — minh hoạ cách chuyển màn khi không có Navigation Component.
 *
 * Mỗi Activity tự công bố cách mở nó qua [newIntent]. Nhờ vậy tên các key extra
 * là `private` và không nơi nào gõ nhầm chuỗi "extra_id"; muốn đổi tham số thì
 * trình biên dịch sẽ chỉ ra hết mọi chỗ gọi.
 *
 * Chỉ truyền ID, KHÔNG truyền cả object: object nhét vào Intent là bản chụp tại thời điểm
 * bấm, sẽ lệch với repository ngay khi dữ liệu được làm mới. Màn này tự hỏi repository.
 */
class DiscoveryDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ITEM_ID = "extra_item_id"

        fun newIntent(context: Context, itemId: String): Intent =
            Intent(context, DiscoveryDetailActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId)
            }
    }

    private lateinit var binding: ActivityDiscoveryDetailBinding

    private val itemId: String by lazy {
        requireNotNull(intent.getStringExtra(EXTRA_ITEM_ID)) { "Thiếu EXTRA_ITEM_ID" }
    }

    private val viewModel: DiscoveryDetailViewModel by viewModels {
        DiscoveryViewModelFactory(
            appContainer.discoveryRepository,
            appContainer.sessionRepository,
            itemId,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiscoveryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.retry() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    /**
     * Với sealed interface, `when` là exhaustive: thêm một trạng thái mới vào DetailUiState
     * sẽ làm hàm này không biên dịch được cho tới khi xử lý nó — không thể quên.
     */
    private fun render(state: DetailUiState) = with(binding) {
        progressBar.isVisible = state is DetailUiState.Loading
        scrollContent.isVisible = state is DetailUiState.Success
        layoutError.isVisible = state is DetailUiState.Error

        when (state) {
            DetailUiState.Loading -> Unit
            is DetailUiState.Success -> bindItem(state.item)
            is DetailUiState.Error -> tvError.setText(state.kind.toMessageRes())
        }
    }

    private fun bindItem(item: DiscoveryItem) = with(binding) {
        tvTitle.text = item.title
        tvDescription.text = item.description
        tvFooter.text = getString(
            R.string.discovery_card_footer,
            item.readingMinutes,
            getString(item.category.toLabelRes()),
        )
    }
}
