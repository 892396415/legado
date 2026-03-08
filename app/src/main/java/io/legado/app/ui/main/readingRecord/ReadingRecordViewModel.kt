package io.legado.app.ui.main.readingRecord

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReadingRecord
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.catch

/**
 * 阅读历史记录 ViewModel - 用于演示 MVVM 的 Demo
 */
class ReadingRecordViewModel(application: Application) : BaseViewModel(application) {

    /**
     * 阅读记录列表 LiveData
     */
    val recordsLiveData = MutableLiveData<List<ReadingRecord>>()

    /**
     * 总阅读时长 LiveData
     */
    val totalDurationLiveData = MutableLiveData<Long>()

    /**
     * 加载状态
     */
    val loadStateLiveData = MutableLiveData<LoadState>()

    /**
     * 当前操作的任务
     */
    var currentJob: io.legado.app.help.coroutine.Coroutine<*>? = null

    /**
     * 加载所有阅读记录
     */
    fun loadRecords() {
        loadStateLiveData.value = LoadState.Loading
        currentJob = execute {
            appDb.readingRecordDao.flowAll()
                .catch { e ->
                    loadStateLiveData.postValue(LoadState.Error(e.message ?: "加载失败"))
                }
                .collect { records ->
                    recordsLiveData.postValue(records)
                    loadStateLiveData.postValue(LoadState.Success)
                }
        }
    }

    /**
     * 加载总阅读时长
     */
    fun loadTotalDuration() {
        execute {
            appDb.readingRecordDao.flowTotalDuration()
                .catch { }
                .collect { duration ->
                    totalDurationLiveData.postValue(duration ?: 0L)
                }
        }
    }

    /**
     * 添加阅读记录
     */
    fun addRecord(
        bookUrl: String,
        bookName: String,
        author: String,
        chapterName: String,
        chapterIndex: Int,
        progress: Long
    ) {
        execute {
            val existingRecord = appDb.readingRecordDao.getByBookUrl(bookUrl)
            val record = if (existingRecord != null) {
                // 更新已有记录
                existingRecord.copy(
                    currentChapterName = chapterName,
                    currentChapterIndex = chapterIndex,
                    readProgress = progress,
                    readDuration = existingRecord.readDuration + 60000, // 假设本次阅读1分钟
                    lastReadTime = System.currentTimeMillis()
                )
            } else {
                // 新建记录
                ReadingRecord(
                    bookUrl = bookUrl,
                    bookName = bookName,
                    author = author,
                    currentChapterName = chapterName,
                    currentChapterIndex = chapterIndex,
                    readProgress = progress,
                    readDuration = 60000
                )
            }
            appDb.readingRecordDao.insert(record)
        }.onSuccess {
            context.toastOnUi(R.string.success)
        }.onError {
            context.toastOnUi(it.message ?: "添加失败")
        }
    }

    /**
     * 删除单条记录
     */
    fun deleteRecord(record: ReadingRecord) {
        execute {
            appDb.readingRecordDao.delete(record)
        }.onSuccess {
            context.toastOnUi(R.string.success)
        }.onError {
            context.toastOnUi(it.message ?: "删除失败")
        }
    }

    /**
     * 清空所有记录
     */
    fun clearAllRecords() {
        execute {
            appDb.readingRecordDao.deleteAll()
        }.onSuccess {
            context.toastOnUi("已清空所有记录")
        }.onError {
            context.toastOnUi(it.message ?: "清空失败")
        }
    }

    /**
     * 格式化阅读时长
     */
    fun formatDuration(durationMs: Long): String {
        val hours = durationMs / 3600000
        val minutes = (durationMs % 3600000) / 60000
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分钟"
            else -> "0分钟"
        }
    }

    /**
     * 加载状态密封类
     */
    sealed class LoadState {
        data object Loading : LoadState()
        data object Success : LoadState()
        data class Error(val message: String) : LoadState()
    }
}
