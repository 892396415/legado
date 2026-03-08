package io.legado.app.ui.main.readingRecord

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.ReadingRecord
import io.legado.app.databinding.FragmentReadingRecordBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch


/**
 * 阅读历史记录 Fragment - 用于演示 MVVM 的 Demo
 */
class ReadingRecordFragment : VMBaseFragment<ReadingRecordViewModel>(R.layout.fragment_reading_record) {

    override val viewModel by viewModels<ReadingRecordViewModel>()

    private lateinit var adapter: ReadingRecordAdapter

    private val binding by viewBinding(FragmentReadingRecordBinding::bind)

    /**
     * 页面初始化
     */
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        // 初始化 RecyclerView
        adapter = ReadingRecordAdapter(
            viewModel = viewModel,
            onItemClick = { record ->
                // 点击进入阅读
                onRecordClick(record)
            },
            onItemLongClick = { record ->
                // 长按显示菜单
                showRecordMenu(record)
                true
            }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ReadingRecordFragment.adapter
        }

        // 观察阅读记录数据
        viewModel.recordsLiveData.observe(viewLifecycleOwner) { records ->
            adapter.setItems(records)
            binding.tvEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }

        // 观察加载状态
        viewModel.loadStateLiveData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ReadingRecordViewModel.LoadState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ReadingRecordViewModel.LoadState.Success -> {
                    binding.progressBar.visibility = View.GONE
                }
                is ReadingRecordViewModel.LoadState.Error -> {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        // 观察事件总线（刷新阅读记录）
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            viewModel.loadRecords()
        }

        // 加载数据
        viewModel.loadRecords()
    }

    /**
     * 创建菜单
     */
    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.menu_reading_record, menu)
    }

    /**
     * 菜单点击事件
     */
    override fun onCompatOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.action_add_demo -> {
                // 添加示例数据
                addDemoData()
                return
            }
            R.id.action_clear -> {
                // 清空记录
                showClearConfirmDialog()
                return
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 点击阅读记录
     */
    private fun onRecordClick(record: ReadingRecord) {
        // 实际项目中可以跳转到阅读界面
        // startActivity<ReadBookActivity>(...)
        android.widget.Toast.makeText(
            requireContext(),
            "点击了: ${record.bookName}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * 显示记录菜单
     */
    private fun showRecordMenu(record: ReadingRecord) {
        alert {
            setTitle(record.bookName)
            items(listOf("继续阅读", "删除记录")) { _, which ->
                when (which) {
                    0 -> onRecordClick(record)
                    1 -> viewModel.deleteRecord(record)
                }
            }
        }
    }

    /**
     * 添加示例数据（用于演示）
     */
    private fun addDemoData() {
        val demoRecords = listOf(
            ReadingRecord(
                bookUrl = "book://demo1",
                bookName = "星辰变",
                author = "我吃西红柿",
                currentChapterName = "第100章 星辰变异",
                currentChapterIndex = 99,
                readProgress = 50,
                readDuration = 7200000,
                lastReadTime = System.currentTimeMillis() - 3600000
            ),
            ReadingRecord(
                bookUrl = "book://demo2",
                bookName = "完美世界",
                author = "辰东",
                currentChapterName = "第200章 石村",
                currentChapterIndex = 199,
                readProgress = 75,
                readDuration = 14400000,
                lastReadTime = System.currentTimeMillis() - 7200000
            ),
            ReadingRecord(
                bookUrl = "book://demo3",
                bookName = "凡人修仙传",
                author = "忘语",
                currentChapterName = "第50章 练剑",
                currentChapterIndex = 49,
                readProgress = 20,
                readDuration = 3600000,
                lastReadTime = System.currentTimeMillis() - 86400000
            )
        )
        
        lifecycleScope.launch {
            demoRecords.forEach { record ->
                io.legado.app.data.appDb.readingRecordDao.insert(record)
            }
        }.invokeOnCompletion {
            viewModel.loadRecords()
        }
    }

    /**
     * 显示清空确认对话框
     */
    private fun showClearConfirmDialog() {
        alert("确定清空所有阅读记录吗？") {
            positiveButton("确定") {
                viewModel.clearAllRecords()
            }
            negativeButton("取消")
        }
    }

    companion object {
        /**
         * 创建 Fragment 实例
         */
        fun newInstance(): ReadingRecordFragment {
            return ReadingRecordFragment()
        }
    }
}
