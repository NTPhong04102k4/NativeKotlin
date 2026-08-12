package com.example.application_ai_assisstant.ui.discovery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.application_ai_assisstant.R
import com.example.application_ai_assisstant.data.model.DiscoveryItem
import com.example.application_ai_assisstant.databinding.ItemDiscoveryCardBinding
import com.example.application_ai_assisstant.ui.common.toLabelRes

/**
 * Adapter cho danh sách Khám phá.
 *
 * Dùng [ListAdapter] chứ không phải RecyclerView.Adapter thuần: chỉ cần gọi `submitList(...)`
 * với danh sách mới, DiffUtil sẽ tự tính phần thay đổi và chạy animation cho đúng những item
 * đó — thay vì `notifyDataSetChanged()` vẽ lại toàn bộ và làm mất vị trí cuộn.
 *
 * Adapter KHÔNG tự mở màn hình mới: nó gọi [onItemClick] để Activity/ViewModel quyết định.
 */
class DiscoveryAdapter(
    private val onItemClick: (DiscoveryItem) -> Unit,
) : ListAdapter<DiscoveryItem, DiscoveryAdapter.ItemViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemDiscoveryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ItemViewHolder(
        private val binding: ItemDiscoveryCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Gán listener trong init (chạy 1 lần khi tạo holder), KHÔNG gán trong bind()
            // vì bind() chạy lại mỗi lần cuộn -> tạo lambda thừa liên tục.
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: DiscoveryItem) = with(binding) {
            tvTitle.text = item.title
            tvDescription.text = item.description
            tvFooter.text = root.context.getString(
                R.string.discovery_card_footer,
                item.readingMinutes,
                root.context.getString(item.category.toLabelRes()),
            )
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DiscoveryItem>() {
            // Cùng một thực thể hay không -> so sánh id
            override fun areItemsTheSame(oldItem: DiscoveryItem, newItem: DiscoveryItem) =
                oldItem.id == newItem.id

            // Nội dung có đổi không -> data class nên `==` đã so sánh đủ mọi field
            override fun areContentsTheSame(oldItem: DiscoveryItem, newItem: DiscoveryItem) =
                oldItem == newItem
        }
    }
}
