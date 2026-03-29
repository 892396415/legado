# DAO 数据访问层详解

> 基于 Legado 项目的 Room DAO 设计实践，深入解析数据访问接口、查询方法、响应式编程和最佳实践。

## 目录

- [一、DAO 基础概念](#一dao-基础概念)
- [二、核心注解详解](#二核心注解详解)
- [三、查询操作 @Query](#三查询操作-query)
- [四、插入操作 @Insert](#四插入操作-insert)
- [五、更新操作 @Update](#五更新操作-update)
- [六、删除操作 @Delete](#六删除操作-delete)
- [七、响应式查询](#七响应式查询)
- [八、复杂 SQL 查询](#八复杂-sql-查询)
- [九、事务处理](#九事务处理)
- [十、DAO 方法设计模式](#十dao-方法设计模式)
- [十一、最佳实践](#十一最佳实践)

---

## 一、DAO 基础概念

### 1.1 什么是 DAO

**DAO (Data Access Object)** 是 Room 中用于定义**数据库操作**的接口，封装了对数据库的增删改查操作。

```
┌─────────────────────────────────────────────────────────────┐
│                          DAO Layer                           │
├─────────────────────────────────────────────────────────────┤
│  @Dao                                                        │
│  interface BookDao {                                         │
│      @Query("SELECT * FROM books")                           │
│      fun getAll(): List<Book>        ← 查询                  │
│                                                              │
│      @Insert                                                 │
│      fun insert(book: Book)          ← 插入                  │
│                                                              │
│      @Update                                                 │
│      fun update(book: Book)          ← 更新                  │
│                                                              │
│      @Delete                                                 │
│      fun delete(book: Book)          ← 删除                  │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Room Implementation                       │
│              (Room 自动生成的实现类)                          │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   SQLite Database                            │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 DAO 接口定义

```kotlin
@Dao
interface BookDao {
    // 查询方法
    @Query("SELECT * FROM books")
    fun getAll(): List<Book>
    
    // 插入方法
    @Insert
    fun insert(book: Book)
    
    // 其他方法...
}
```

### 1.3 DAO 特点

- **接口定义**：只需定义接口，Room 自动生成实现
- **编译时检查**：SQL 语句在编译时验证
- **类型安全**：返回类型与 Entity 匹配
- **支持协程**：可标记为 suspend 函数
- **响应式支持**：支持 Flow 和 LiveData

---

## 二、核心注解详解

### 2.1 @Dao - DAO 标记

```kotlin
@Dao
interface BookDao {
    // 所有数据库操作方法
}
```

### 2.2 @Query - 自定义查询

```kotlin
@Dao
interface BookDao {
    // 查询所有
    @Query("SELECT * FROM books")
    fun getAll(): List<Book>
    
    // 条件查询
    @Query("SELECT * FROM books WHERE bookUrl = :url")
    fun getByUrl(url: String): Book?
    
    // 模糊查询
    @Query("SELECT * FROM books WHERE name LIKE '%' || :key || '%'")
    fun search(key: String): List<Book>
}
```

### 2.3 @Insert - 插入数据

```kotlin
@Dao
interface BookDao {
    // 插入单条
    @Insert
    fun insert(book: Book)
    
    // 插入多条
    @Insert
    fun insertAll(books: List<Book>)
    
    // 冲突策略
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(book: Book)
}
```

**冲突策略**：

| 策略 | 作用 |
|------|------|
| `ABORT` | 默认，回滚事务 |
| `REPLACE` | 替换旧数据 |
| `IGNORE` | 忽略冲突，保持旧数据 |
| `FAIL` | 立即失败 |
| `ROLLBACK` | 回滚事务 |

### 2.4 @Update - 更新数据

```kotlin
@Dao
interface BookDao {
    // 更新单条
    @Update
    fun update(book: Book)
    
    // 更新多条
    @Update
    fun updateAll(books: List<Book>)
}
```

### 2.5 @Delete - 删除数据

```kotlin
@Dao
interface BookDao {
    // 删除单条
    @Delete
    fun delete(book: Book)
    
    // 删除多条
    @Delete
    fun deleteAll(books: List<Book>)
}
```

### 2.6 @Transaction - 事务

```kotlin
@Dao
interface BookDao {
    @Transaction
    fun updateBookAndChapters(book: Book, chapters: List<BookChapter>) {
        update(book)
        insertChapters(chapters)
    }
}
```

---

## 三、查询操作 @Query

### 3.1 基础查询

```kotlin
@Dao
interface BookDao {
    
    // 查询所有
    @Query("SELECT * FROM books")
    fun getAll(): List<Book>
    
    // 查询单条
    @Query("SELECT * FROM books WHERE bookUrl = :url")
    fun getByUrl(url: String): Book?
    
    // 查询数量
    @get:Query("SELECT COUNT(*) FROM books")
    val count: Int
    
    // 查询是否存在
    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE bookUrl = :url)")
    fun exists(url: String): Boolean
}
```

### 3.2 参数绑定

```kotlin
@Dao
interface BookChapterDao {
    
    // 单参数
    @Query("SELECT * FROM chapters WHERE bookUrl = :bookUrl")
    fun getByBook(bookUrl: String): List<BookChapter>
    
    // 多参数
    @Query("SELECT * FROM chapters WHERE bookUrl = :bookUrl AND `index` = :index")
    fun getChapter(bookUrl: String, index: Int): BookChapter?
    
    // 范围查询
    @Query("SELECT * FROM chapters WHERE bookUrl = :bookUrl AND `index` BETWEEN :start AND :end")
    fun getRange(bookUrl: String, start: Int, end: Int): List<BookChapter>
}
```

### 3.3 模糊查询

```kotlin
@Dao
interface BookSourceDao {
    
    // LIKE 模糊查询
    @Query("SELECT * FROM book_sources WHERE bookSourceName LIKE '%' || :key || '%'")
    fun searchByName(key: String): List<BookSource>
    
    // 多字段模糊查询
    @Query("""
        SELECT * FROM book_sources 
        WHERE bookSourceName LIKE '%' || :key || '%'
        OR bookSourceGroup LIKE '%' || :key || '%'
        OR bookSourceUrl LIKE '%' || :key || '%'
    """)
    fun search(key: String): List<BookSource>
}
```

### 3.4 排序与分页

```kotlin
@Dao
interface BookDao {
    
    // 排序查询
    @Query("SELECT * FROM books ORDER BY durChapterTime DESC")
    fun getByReadTime(): List<Book>
    
    // 多字段排序
    @Query("SELECT * FROM books ORDER BY `group` ASC, `order` DESC")
    fun getOrdered(): List<Book>
    
    // 限制数量
    @Query("SELECT * FROM books ORDER BY durChapterTime DESC LIMIT 10")
    fun getTop10(): List<Book>
    
    // 分页查询
    @Query("SELECT * FROM books ORDER BY durChapterTime DESC LIMIT :limit OFFSET :offset")
    fun getPage(limit: Int, offset: Int): List<Book>
}
```

### 3.5 聚合查询

```kotlin
@Dao
interface BookDao {
    
    // 计数
    @get:Query("SELECT COUNT(*) FROM books")
    val count: Int
    
    // 条件计数
    @Query("SELECT COUNT(*) FROM books WHERE type & :type > 0")
    fun countByType(type: Int): Int
    
    // 最大值
    @get:Query("SELECT MAX(`order`) FROM books")
    val maxOrder: Int
    
    // 最小值
    @get:Query("SELECT MIN(`order`) FROM books")
    val minOrder: Int
    
    // 求和
    @get:Query("SELECT SUM(groupId) FROM book_groups")
    val sumGroupId: Long
    
    // 分组统计
    @Query("SELECT type, COUNT(*) FROM books GROUP BY type")
    fun countByGroup(): Map<Int, Int>
}
```

---

## 四、插入操作 @Insert

### 4.1 插入单条数据

```kotlin
@Dao
interface BookDao {
    
    // 简单插入
    @Insert
    fun insert(book: Book)
    
    // 返回行 ID
    @Insert
    fun insert(book: Book): Long
    
    // 冲突替换
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(book: Book)
}
```

### 4.2 插入多条数据

```kotlin
@Dao
interface BookDao {
    
    // 插入数组
    @Insert
    fun insert(vararg books: Book)
    
    // 插入列表
    @Insert
    fun insertAll(books: List<Book>)
    
    // 返回 ID 列表
    @Insert
    fun insertAll(books: List<Book>): List<Long>
}
```

### 4.3 实战：CacheDao

```kotlin
@Dao
interface CacheDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg cache: Cache)
    
    // 使用示例：
    // cacheDao.insert(Cache(key = "user", value = "data"))
    // cacheDao.insert(cache1, cache2, cache3)
}
```

---

## 五、更新操作 @Update

### 5.1 基础更新

```kotlin
@Dao
interface BookDao {
    
    // 更新单条
    @Update
    fun update(book: Book)
    
    // 更新多条
    @Update
    fun update(vararg books: Book)
    
    // 更新列表
    @Update
    fun updateAll(books: List<Book>)
}
```

### 5.2 条件更新（SQL）

```kotlin
@Dao
interface BookSourceDao {
    
    // 更新单个字段
    @Query("UPDATE book_sources SET enabled = :enable WHERE bookSourceUrl = :url")
    fun setEnabled(url: String, enable: Boolean)
    
    // 更新多个字段
    @Query("UPDATE books SET durChapterPos = :pos WHERE bookUrl = :url")
    fun updateProgress(url: String, pos: Int)
    
    // 批量更新
    @Query("UPDATE book_groups SET show = 1 WHERE groupId = :groupId")
    fun enableGroup(groupId: Long)
}
```

### 5.3 实战：批量启用/禁用

```kotlin
@Dao
interface BookSourceDao {
    
    // 单个启用/禁用
    @Query("UPDATE book_sources SET enabled = :enable WHERE bookSourceUrl = :url")
    fun enable(url: String, enable: Boolean)
    
    // 批量启用/禁用
    @Transaction
    fun enable(enable: Boolean, sources: List<BookSourcePart>) {
        for (source in sources) {
            enable(source.bookSourceUrl, enable)
        }
    }
}
```

---

## 六、删除操作 @Delete

### 6.1 基础删除

```kotlin
@Dao
interface BookDao {
    
    // 删除单条（根据主键）
    @Delete
    fun delete(book: Book)
    
    // 删除多条
    @Delete
    fun delete(vararg books: Book)
    
    // 删除列表
    @Delete
    fun deleteAll(books: List<Book>)
}
```

### 6.2 条件删除（SQL）

```kotlin
@Dao
interface BookDao {
    
    // 根据主键删除
    @Query("DELETE FROM books WHERE bookUrl = :url")
    fun deleteByUrl(url: String)
    
    // 批量删除
    @Query("DELETE FROM books WHERE type & :type > 0")
    fun deleteByType(type: Int)
    
    // 删除所有
    @Query("DELETE FROM search_keywords")
    fun deleteAll()
    
    // 条件删除（缓存过期）
    @Query("DELETE FROM caches WHERE deadline > 0 AND deadline < :now")
    fun clearExpired(now: Long)
}
```

### 6.3 级联删除

外键配置为 `CASCADE` 时，删除父记录会自动删除子记录：

```kotlin
// Entity 定义
@Entity(
    tableName = "chapters",
    foreignKeys = [(ForeignKey(
        entity = Book::class,
        parentColumns = ["bookUrl"],
        childColumns = ["bookUrl"],
        onDelete = ForeignKey.CASCADE  // 级联删除
    ))]
)

// DAO
@Dao
interface BookDao {
    @Delete
    fun delete(book: Book)  // 会自动删除关联的 chapters
}
```

---

## 七、响应式查询

### 7.1 Flow 响应式查询

```kotlin
@Dao
interface BookDao {
    
    // Flow 自动观察数据变化
    @Query("SELECT * FROM books ORDER BY durChapterTime DESC")
    fun flowAll(): Flow<List<Book>>
    
    // 带条件的 Flow
    @Query("SELECT * FROM books WHERE `group` & :group > 0")
    fun flowByGroup(group: Long): Flow<List<Book>>
}
```

**使用示例**：

```kotlin
class BookViewModel : ViewModel() {
    
    // 在 ViewModel 中使用
    val books: Flow<List<Book>> = bookDao.flowAll()
    
    // UI 层收集
    viewModelScope.launch {
        bookDao.flowAll().collect { books ->
            // 数据变化时自动回调
            updateUI(books)
        }
    }
}
```

### 7.2 Flow 与协程

```kotlin
@Dao
interface BookSourceDao {
    
    // 切换到 IO 线程
    fun flowEnabledGroups(): Flow<List<String>> {
        return flowEnabledGroupsUnProcessed()
            .map { list -> dealGroups(list) }
            .flowOn(Dispatchers.IO)
    }
}
```

### 7.3 LiveData 响应式查询

```kotlin
@Dao
interface BookGroupDao {
    
    @get:Query("SELECT * FROM book_groups WHERE show > 0 ORDER BY `order`")
    val show: LiveData<List<BookGroup>>
}
```

**使用示例**：

```kotlin
class BookGroupViewModel : ViewModel() {
    
    val groups: LiveData<List<BookGroup>> = bookGroupDao.show
    
    init {
        // 在 Activity/Fragment 中观察
        groups.observe(this) { groups ->
            updateUI(groups)
        }
    }
}
```

### 7.4 Flow vs LiveData

| 特性 | Flow | LiveData |
|------|------|----------|
| 生命周期感知 | 需配合 `repeatOnLifecycle` | 自动感知 |
| 线程切换 | `flowOn()` | 自动主线程 |
| 操作符 | 丰富 | 有限 |
| 冷/热流 | 冷流 | 热流 |
| 适用场景 | 复杂数据流 | 简单 UI 绑定 |

---

## 八、复杂 SQL 查询

### 8.1 多表联合查询

```kotlin
@Dao
interface BookDao {
    
    // 关联查询书源
    @Query("""
        SELECT DISTINCT bs.* FROM books, book_sources bs 
        WHERE origin == bookSourceUrl 
        AND origin NOT LIKE 'local_%'
    """)
    fun getAllUseBookSource(): List<BookSource>
}
```

### 8.2 子查询

```kotlin
@Dao
interface BookGroupDao {
    
    @get:Query("""
        WITH const AS (
            SELECT SUM(groupId) sumGroupId 
            FROM book_groups 
            WHERE groupId > 0
        )
        SELECT book_groups.* 
        FROM book_groups 
        JOIN const 
        WHERE show > 0 
        AND (
            (groupId >= 0 AND EXISTS (
                SELECT 1 FROM books WHERE `group` & book_groups.groupId > 0
            ))
            OR groupId = -1
        )
        ORDER BY `order`
    """)
    val show: LiveData<List<BookGroup>>
}
```

### 8.3 位运算查询

```kotlin
@Dao
interface BookDao {
    
    // 按类型位运算查询
    @Query("SELECT * FROM books WHERE type & ${BookType.audio} > 0")
    fun flowAudio(): Flow<List<Book>>
    
    @Query("SELECT * FROM books WHERE type & ${BookType.local} > 0")
    fun flowLocal(): Flow<List<Book>>
    
    // 组合条件
    @Query("""
        SELECT * FROM books 
        WHERE type & ${BookType.audio} = 0 
        AND type & ${BookType.local} = 0
    """)
    fun flowNetBooks(): Flow<List<Book>>
}
```

### 8.4 分组匹配查询

```kotlin
@Dao
interface BookSourceDao {
    
    // 分组匹配（支持多分组存储）
    @Query("""
        SELECT * FROM book_sources 
        WHERE bookSourceGroup = :group
        OR bookSourceGroup LIKE :group || ',%' 
        OR bookSourceGroup LIKE '%,' || :group
        OR bookSourceGroup LIKE '%,' || :group || ',%'
    """)
    fun groupSearch(group: String): List<BookSource>
}
```

### 8.5 使用 View 简化查询

```kotlin
// 定义视图 Entity
@Entity(tableName = "book_sources_part")
data class BookSourcePart(
    @PrimaryKey
    val bookSourceUrl: String,
    val bookSourceName: String,
    // 部分字段...
)

// DAO 查询视图
@Dao
interface BookSourceDao {
    
    @Query("SELECT * FROM book_sources_part WHERE enabled = 1")
    fun flowEnabled(): Flow<List<BookSourcePart>>
    
    // 联合查询视图和完整表
    @Query("""
        SELECT bp.* 
        FROM book_sources b 
        JOIN book_sources_part bp ON b.bookSourceUrl = bp.bookSourceUrl 
        WHERE b.enabled = 1
    """)
    fun flowEnabledPart(): Flow<List<BookSourcePart>>
}
```

---

## 九、事务处理

### 9.1 @Transaction 注解

```kotlin
@Dao
interface BookSourceDao {
    
    // 批量删除（事务中执行）
    @Transaction
    fun delete(sources: List<BookSourcePart>) {
        for (source in sources) {
            delete(source.bookSourceUrl)
        }
    }
    
    // 批量更新排序
    @Transaction
    fun upOrder(sources: List<BookSourcePart>) {
        for (source in sources) {
            upOrder(source.bookSourceUrl, source.customOrder)
        }
    }
}
```

### 9.2 事务的作用

```kotlin
@Dao
interface BookDao {
    
    @Transaction
    fun updateBookAndChapters(book: Book, chapters: List<BookChapter>) {
        // 1. 更新书籍
        update(book)
        
        // 2. 删除旧章节
        deleteChapters(book.bookUrl)
        
        // 3. 插入新章节
        insertChapters(chapters)
        
        // 三个操作要么都成功，要么都失败（原子性）
    }
}
```

### 9.3 何时使用事务

| 场景 | 是否使用事务 |
|------|-------------|
| 单条操作 | ❌ 不需要 |
| 多条相关操作 | ✅ 需要 |
| 批量操作 | ✅ 需要 |
| 先删后插 | ✅ 必须 |
| 读取操作 | ❌ 不需要 |

---

## 十、DAO 方法设计模式

### 10.1 单条 vs 批量方法

```kotlin
@Dao
interface BookDao {
    
    // 单条方法（基本操作）
    @Query("SELECT * FROM books WHERE bookUrl = :url")
    fun get(url: String): Book?
    
    @Insert
    fun insert(book: Book)
    
    @Update
    fun update(book: Book)
    
    @Delete
    fun delete(book: Book)
    
    // 批量方法（业务扩展）
    @Insert
    fun insert(vararg books: Book)
    
    @Update
    fun update(vararg books: Book)
    
    @Delete
    fun delete(vararg books: Book)
    
    // 事务批量方法
    @Transaction
    fun insertAll(books: List<Book>) {
        books.forEach { insert(it) }
    }
}
```

### 10.2 查询方法命名规范

| 前缀 | 含义 | 示例 |
|------|------|------|
| `get` | 获取单条 | `getByUrl(url: String)` |
| `getAll` | 获取全部 | `getAll(): List<Book>` |
| `find` | 查找（可能为空） | `findById(id: Long)` |
| `search` | 搜索 | `search(key: String)` |
| `flow` | Flow 响应式 | `flowAll(): Flow<List<Book>>` |
| `count` | 计数 | `count(): Int` |
| `has` | 是否存在 | `has(url: String): Boolean` |
| `exists` | 是否存在 | `exists(url: String): Boolean` |

### 10.3 实战：BookSourceDao 设计

```kotlin
@Dao
interface BookSourceDao {
    
    // ========== Flow 查询 ==========
    @Query("SELECT * FROM book_sources_part ORDER BY customOrder")
    fun flowAll(): Flow<List<BookSourcePart>>
    
    @Query("SELECT * FROM book_sources_part WHERE enabled = 1")
    fun flowEnabled(): Flow<List<BookSourcePart>>
    
    @Query("SELECT * FROM book_sources_part WHERE enabled = 0")
    fun flowDisabled(): Flow<List<BookSourcePart>>
    
    fun flowSearch(key: String): Flow<List<BookSourcePart>>
    
    // ========== 同步查询 ==========
    @get:Query("SELECT * FROM book_sources")
    val all: List<BookSource>
    
    @get:Query("SELECT * FROM book_sources WHERE enabled = 1")
    val allEnabled: List<BookSource>
    
    @Query("SELECT * FROM book_sources WHERE bookSourceUrl = :key")
    fun get(key: String): BookSource?
    
    // ========== 聚合查询 ==========
    @get:Query("SELECT COUNT(*) FROM book_sources")
    fun count(): Int
    
    @get:Query("SELECT MIN(customOrder) FROM book_sources")
    val minOrder: Int
    
    @get:Query("SELECT MAX(customOrder) FROM book_sources")
    val maxOrder: Int
    
    @Query("SELECT EXISTS(SELECT 1 FROM book_sources WHERE bookSourceUrl = :key)")
    fun has(key: String): Boolean
    
    // ========== 插入 ==========
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg source: BookSource)
    
    // ========== 更新 ==========
    @Update
    fun update(vararg source: BookSource)
    
    @Query("UPDATE book_sources SET enabled = :enable WHERE bookSourceUrl = :url")
    fun enable(url: String, enable: Boolean)
    
    @Transaction
    fun enable(enable: Boolean, sources: List<BookSourcePart>)
    
    // ========== 删除 ==========
    @Delete
    fun delete(vararg source: BookSource)
    
    @Query("DELETE FROM book_sources WHERE bookSourceUrl = :key")
    fun delete(key: String)
}
```

---

## 十一、最佳实践

### 11.1 使用 vararg 支持灵活调用

```kotlin
@Dao
interface BookDao {
    
    // ✅ 使用 vararg 支持多种调用方式
    @Insert
    fun insert(vararg book: Book)
    
    // 可以调用：
    // insert(book1)
    // insert(book1, book2)
    // insert(*books.toTypedArray())
}
```

### 11.2 返回 ID 便于后续操作

```kotlin
@Dao
interface ReplaceRuleDao {
    
    // ✅ 返回插入的 ID
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg rule: ReplaceRule): List<Long>
    
    // 使用：
    // val ids = insert(rule1, rule2)
    // val newId = ids[0]
}
```

### 11.3 使用 @Transaction 保证一致性

```kotlin
@Dao
interface BookDao {
    
    // ✅ 批量操作使用事务
    @Transaction
    fun updateBatch(books: List<Book>) {
        books.forEach { update(it) }
    }
    
    // ❌ 不使用事务可能导致数据不一致
    fun updateBatchNoTransaction(books: List<Book>) {
        books.forEach { update(it) }  // 中间失败会导致部分更新
    }
}
```

### 11.4 使用 SQL 直接更新更高效

```kotlin
@Dao
interface BookDao {
    
    // ❌ 低效：先查询再更新
    @Transaction
    fun updateProgressLow(url: String, pos: Int) {
        val book = getByUrl(url) ?: return
        book.durChapterPos = pos
        update(book)
    }
    
    // ✅ 高效：直接 SQL 更新
    @Query("UPDATE books SET durChapterPos = :pos WHERE bookUrl = :url")
    fun updateProgress(url: String, pos: Int)
}
```

### 11.5 使用 Flow 替代轮询

```kotlin
@Dao
interface BookDao {
    
    // ❌ 不推荐：手动刷新
    fun refreshData(): List<Book> {
        return getAll()  // 需要手动调用
    }
    
    // ✅ 推荐：自动观察
    @Query("SELECT * FROM books")
    fun flowAll(): Flow<List<Book>>  // 数据变化自动通知
}
```

### 11.6 参数校验与默认值

```kotlin
@Dao
interface CacheDao {
    
    // ✅ 检查过期时间
    @Query("SELECT value FROM caches WHERE `key` = :key AND (deadline = 0 OR deadline > :now)")
    fun get(key: String, now: Long): String?
    
    // 使用：
    // val value = cacheDao.get("user", System.currentTimeMillis())
}
```

### 11.7 项目结构建议

```
data/
├── dao/
│   ├── BookDao.kt              # 书籍 DAO
│   ├── BookChapterDao.kt       # 章节 DAO
│   ├── BookSourceDao.kt        # 书源 DAO
│   ├── BookGroupDao.kt         # 分组 DAO
│   ├── ReplaceRuleDao.kt       # 替换规则 DAO
│   ├── CacheDao.kt             # 缓存 DAO
│   └── ...                     # 其他 DAO
├── entities/                   # 实体类
└── AppDatabase.kt              # 数据库配置
```

---

## 参考资源

- **Room 官方文档**: [DAO](https://developer.android.com/training/data-storage/room/accessing-data)
- **SQLite 语法**: [SQLite Query Language](https://www.sqlite.org/lang.html)
- **项目文件**:
  - `app/src/main/java/io/legado/app/data/dao/BookDao.kt`
  - `app/src/main/java/io/legado/app/data/dao/BookSourceDao.kt`
  - `app/src/main/java/io/legado/app/data/dao/BookChapterDao.kt`
  - `app/src/main/java/io/legado/app/data/dao/ReplaceRuleDao.kt`

---

> 文档生成时间：2026年3月
> 覆盖 DAO：BookDao、BookSourceDao、BookChapterDao、ReplaceRuleDao、CacheDao 等
