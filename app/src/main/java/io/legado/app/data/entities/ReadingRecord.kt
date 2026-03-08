package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 阅读历史记录 - 用于演示 MVVM 的 Demo 实体类
 */
@Entity(
    tableName = "reading_records",
    indices = [Index(value = ["bookUrl"], unique = true)]
)
data class ReadingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    
    @ColumnInfo(defaultValue = "")
    val bookName: String = "",
    
    @ColumnInfo(defaultValue = "")
    val author: String = "",
    
    @ColumnInfo(defaultValue = "NULL")
    val coverUrl: String? = null,
    
    @ColumnInfo(defaultValue = "")
    val currentChapterName: String = "",
    
    @ColumnInfo(defaultValue = "0")
    val currentChapterIndex: Int = 0,
    
    @ColumnInfo(defaultValue = "0")
    val readProgress: Long = 0,
    
    @ColumnInfo(defaultValue = "0")
    val readDuration: Long = 0,

    @ColumnInfo(defaultValue = "0")
    val lastReadTime: Long = System.currentTimeMillis()
)
