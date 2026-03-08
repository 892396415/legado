package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.ReadingRecord
import kotlinx.coroutines.flow.Flow

/**
 * 阅读历史记录 DAO - 用于演示 MVVM 的 Demo
 */
@Dao
interface ReadingRecordDao {

    /**
     * 观察所有阅读记录（按最后阅读时间倒序）
     */
    @Query("SELECT * FROM reading_records ORDER BY lastReadTime DESC")
    fun flowAll(): Flow<List<ReadingRecord>>

    /**
     * 根据书籍URL获取阅读记录
     */
    @Query("SELECT * FROM reading_records WHERE bookUrl = :bookUrl")
    suspend fun getByBookUrl(bookUrl: String): ReadingRecord?

    /**
     * 获取最近的阅读记录
     */
    @Query("SELECT * FROM reading_records ORDER BY lastReadTime DESC LIMIT :limit")
    fun flowRecent(limit: Int): Flow<List<ReadingRecord>>

    /**
     * 获取总阅读时长
     */
    @Query("SELECT SUM(readDuration) FROM reading_records")
    fun flowTotalDuration(): Flow<Long?>

    /**
     * 插入或更新阅读记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ReadingRecord)

    /**
     * 更新阅读记录
     */
    @Update
    suspend fun update(record: ReadingRecord)

    /**
     * 删除阅读记录
     */
    @Delete
    suspend fun delete(record: ReadingRecord)

    /**
     * 根据书籍URL删除
     */
    @Query("DELETE FROM reading_records WHERE bookUrl = :bookUrl")
    suspend fun deleteByBookUrl(bookUrl: String)

    /**
     * 清空所有阅读记录
     */
    @Query("DELETE FROM reading_records")
    suspend fun deleteAll()
}
