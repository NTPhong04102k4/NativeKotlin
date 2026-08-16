package com.ntp.application_ai_assisstant.ui.schedule

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ntp.application_ai_assisstant.data.model.ScheduleItem
import com.ntp.application_ai_assisstant.databinding.ItemScheduleBinding
import com.ntp.application_ai_assisstant.util.DateTimeUtils

/**
 * Adapter danh sách lịch trình. Cùng khuôn với DiscoveryAdapter: [ListAdapter] + DiffUtil.
 */
class ScheduleAdapter(
    private val onItemClick: (ScheduleItem) -> Unit,
) : ListAdapter<ScheduleItem, ScheduleAdapter.ItemViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ItemViewHolder(
        private val binding: ItemScheduleBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: ScheduleItem) = with(binding) {
            // Format ở đây chứ không ở model: chuỗi phụ thuộc Locale của máy.
            tvTime.text = DateTimeUtils.formatTime(item.startAtMillis)
            tvPeriod.text = DateTimeUtils.formatPeriod(item.startAtMillis)

            tvTaskTitle.text = item.title
            // ViewHolder được tái sử dụng khi cuộn -> phải set CẢ HAI chiều của mọi thuộc tính,
            // nếu chỉ bật gạch ngang mà không tắt thì item chưa xong cũng bị gạch.
            tvTaskTitle.paintFlags = if (item.isDone) {
                tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            tvTaskLocation.text = item.location
            tvTaskLocation.isVisible = item.location.isNotBlank()
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ScheduleItem>() {
            override fun areItemsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem) =
                oldItem == newItem
        }
    }
}
