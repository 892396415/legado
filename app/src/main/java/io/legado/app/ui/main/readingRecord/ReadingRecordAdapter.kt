package io.legado.app.ui.main.readingRecord

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.ReadingRecord
import io.legado.app.databinding.ItemReadingRecordBinding

/**
 * 阅读历史记录 Adapter - 用于演示 MVVM 的 Demo
 */
class ReadingRecordAdapter(
    private val viewModel: ReadingRecordViewModel,
    private val onItemClick: (ReadingRecord) -> Unit,
    private val onItemLongClick: (ReadingRecord) -> Boolean
) : RecyclerAdapter<ReadingRecord, ItemReadingRecordBinding>(
    viewModel.context
) {

    override fun getViewBinding(parent: ViewGroup): ItemReadingRecordBinding {
        return ItemReadingRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    }

    override fun convert(holder: ItemViewHolder, binding: ItemReadingRecordBinding, item: ReadingRecord, payloads: MutableList<Any>) {
        binding.apply {
            // 设置书籍名称
            tvBookName.text = item.bookName.ifEmpty { "未知书籍" }
            
            // 设置作者
            tvAuthor.text = item.author.ifEmpty { "未知作者" }
            
            // 设置当前阅读章节
            tvChapter.text = item.currentChapterName.ifEmpty { "未开始阅读" }
            
            // 设置阅读进度
            tvProgress.text = "进度: ${item.readProgress}%"
            
            // 设置阅读时长
            tvDuration.text = viewModel.formatDuration(item.readDuration)
            
            // 设置最后阅读时间
            tvLastReadTime.text = formatTime(item.lastReadTime)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemReadingRecordBinding) {
        binding.root.setOnClickListener {
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                getItem(position)?.let { onItemClick(it) }
            }
        }
        
        binding.root.setOnLongClickListener {
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                getItem(position)?.let { onItemLongClick(it) } ?: false
            } else {
                false
            }
        }
    }

    /**
     * 格式化时间
     */
    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60000 -> "刚刚"
            diff < 3600000 -> "${diff / 60000}分钟前"
            diff < 86400000 -> "${diff / 3600000}小时前"
            diff < 604800000 -> "${diff / 86400000}天前"
            else -> "${timestamp / 86400000}天前"
        }
    }
}
