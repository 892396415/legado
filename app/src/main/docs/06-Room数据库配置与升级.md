# Room 数据库配置与升级详解

> 基于 Legado 项目的 Room 数据库实践，深入解析数据库配置、迁移策略和最佳实践。

## 目录

- [一、Room 基础架构](#一room-基础架构)
- [二、数据库配置详解](#二数据库配置详解)
- [三、Entity 实体类设计](#三entity-实体类设计)
- [四、DAO 数据访问层](#四dao-数据访问层)
- [五、数据库迁移策略](#五数据库迁移策略)
- [六、复杂查询与事务](#六复杂查询与事务)
- [七、最佳实践](#七最佳实践)
- [八、常见问题排查](#八常见问题排查)

---

## 一、Room 基础架构

### 1.1 Room 组件关系图

```
┌─────────────────┐
│   UI Layer      │  ← Activity/Fragment/ViewModel
│   (Observer)    │
└────────┬────────┘
         │ Flow/LiveData
         ▼
┌─────────────────┐
│   ViewModel     │  ← 业务逻辑层
│                 │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Repository    │  ← 数据仓库层
│                 │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│      DAO        │  ← 数据访问对象（接口）
│   (Interface)   │
└────────┬────────┘
         │ SQL/SQLite
         ▼
┌─────────────────┐
│   Room Database │  ← 数据库抽象层
│                 │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  SQLiteDatabase │  ← 底层 SQLite
│                 │
└─────────────────┘
```

### 1.2 Room 三大核心组件

| 组件 | 作用 | 注解 |
|------|------|------|
| **Database** | 数据库持有者，连接 Entity 和 DAO | `@Database` |
| **Entity** | 数据表结构定义 | `@Entity` |
| **DAO** | 数据访问接口 | `@Dao` |

---

## 二、数据库配置详解

### 2.1 数据库单例模式

**文件**: `app/src/main/java/io/legado/app/data/AppDatabase.kt`

```kotlin
// 使用 lazy 实现线程安全的单例
val appDb by lazy {
    Room.databaseBuilder(appCtx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .fallbackToDestructiveMigrationFrom(false, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        .addMigrations(*DatabaseMigrations.migrations)
        .allowMainThreadQueries()
        .addCallback(AppDatabase.dbCallback)
        .build()
}
```

**配置解析**:

| 配置项 | 作用 | 说明 |
|--------|------|------|
| `databaseBuilder()` | 创建数据库实例 | 需要 Context、数据库类、数据库名 |
| `fallbackToDestructiveMigrationFrom()` | 指定版本销毁重建 | 从旧版本升级时删除所有数据 |
| `addMigrations()` | 添加迁移策略 | 传入 Migration 数组 |
| `allowMainThreadQueries()` | 允许主线程查询 | ⚠️ 仅调试使用，生产环境应避免 |
| `addCallback()` | 数据库回调 | 在创建/打开时执行操作 |

### 2.2 数据库类定义

```kotlin
@Database(
    version = 76,                          // 当前数据库版本
    exportSchema = true,                    // 导出 schema 到 JSON（用于版本控制）
    entities = [                            // 实体类列表
        Book::class, 
        BookGroup::class, 
        BookSource::class,
        // ... 其他实体
    ],
    views = [BookSourcePart::class],        // 数据库视图
    autoMigrations = [                      // 自动迁移配置
        AutoMigration(from = 43, to = 44),
        AutoMigration(from = 54, to = 55, spec = DatabaseMigrations.Migration_54_55::class),
        // ... 其他自动迁移
    ]
)
abstract class AppDatabase : RoomDatabase() {
    
    // 抽象 DAO 属性，Room 会自动实现
    abstract val bookDao: BookDao
    abstract val bookGroupDao: BookGroupDao
    abstract val bookSourceDao: BookSourceDao
    // ... 其他 DAO

    companion object {
        const val DATABASE_NAME = "legado.db"
        
        // 数据库回调
        val dbCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // 首次创建数据库时执行
                db.setLocale(Locale.CHINESE)
            }
            
            override fun onOpen(db: SupportSQLiteDatabase) {
                // 每次打开数据库时执行
                // 可以在这里初始化默认数据
            }
        }
    }
}
```

### 2.3 关键设计要点

#### 2.3.1 版本号管理

- **version 递增规则**: 每次数据库结构变更（添加表、添加字段、修改字段类型）都必须递增
- **当前版本**: 76（项目持续演进中）
- **历史版本**: 1-9 版本使用破坏性迁移，10+ 版本使用手动迁移

#### 2.3.2 exportSchema 配置

```groovy
// build.gradle (app)
android {
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += ["room.schemaLocation": "$projectDir/schemas".toString()]
            }
        }
    }
}
```

导出 schema 的好处：
- 版本控制：可追踪数据库结构变化历史
- 测试验证：可对比不同版本的 schema
- 文档生成：自动生成数据库文档

---

## 三、Entity 实体类设计

### 3.1 基础实体结构

**文件**: `app/src/main/java/io/legado/app/data/entities/Book.kt`

```kotlin
@Parcelize                                    // 支持 Parcelable 序列化
@TypeConverters(Book.Converters::class)      // 指定类型转换器
@Entity(
    tableName = "books",                     // 自定义表名（默认使用类名）
    indices = [                              // 索引定义
        Index(value = ["name", "author"], unique = true)  // 唯一索引
    ]
)
data class Book(
    // 主键定义
    @PrimaryKey
    @ColumnInfo(defaultValue = "")           // 字段默认值
    override var bookUrl: String = "",
    
    // 普通字段
    @ColumnInfo(defaultValue = "")
    var tocUrl: String = "",
    
    // 可为 null 的字段
    var kind: String? = null,
    
    // 忽略字段（不存入数据库）
    @Ignore
    @IgnoredOnParcel
    override var infoHtml: String? = null,
    
    // 复杂类型字段（需要 TypeConverter）
    var readConfig: ReadConfig? = null,
    
    // 其他字段...
) : Parcelable, BaseBook
```

### 3.2 常用注解详解

| 注解 | 作用 | 示例 |
|------|------|------|
| `@Entity` | 标记为实体类 | `@Entity(tableName = "books")` |
| `@PrimaryKey` | 主键标记 | `@PrimaryKey val id: Long` |
| `@ColumnInfo` | 字段配置 | `@ColumnInfo(name = "user_name")` |
| `@Ignore` | 忽略字段 | `@Ignore val tempData: String` |
| `@Index` | 索引定义 | `@Index(value = ["name"], unique = true)` |
| `@Embedded` | 嵌入对象 | `@Embedded val address: Address` |
| `@Relation` | 定义关系 | 配合 `@Embedded` 使用 |

### 3.3 索引设计最佳实践

```kotlin
@Entity(
    tableName = "books",
    indices = [
        // 单字段索引 - 提高查询速度
        Index(value = ["name"]),
        
        // 复合索引 - 多字段联合查询优化
        Index(value = ["name", "author"], unique = true),
        
        // 唯一索引 - 防止重复数据
        Index(value = ["bookUrl"], unique = true)
    ]
)
```

**索引设计原则**：
1. **查询频繁的字段**添加索引
2. **WHERE、ORDER BY、JOIN** 条件中的字段优先
3. **唯一约束**的字段必须使用唯一索引
4. 索引过多会影响写入性能，需要权衡

### 3.4 类型转换器（TypeConverter）

用于存储 Room 不支持的类型（如自定义对象、List、Date 等）：

```kotlin
class Converters {
    @TypeConverter
    fun readConfigToString(config: ReadConfig?): String = 
        GSON.toJson(config)

    @TypeConverter
    fun stringToReadConfig(json: String?) = 
        GSON.fromJsonObject<ReadConfig>(json).getOrNull()
}
```

**使用场景**：
- 存储 JSON 对象
- 存储日期时间
- 存储枚举类型
- 存储复杂数据结构

---

## 四、DAO 数据访问层

### 4.1 DAO 接口定义

**文件**: `app/src/main/java/io/legado/app/data/dao/BookDao.kt`

```kotlin
@Dao
interface BookDao {
    
    // ================== Flow 响应式查询 ==================
    
    @Query("SELECT * FROM books order by durChapterTime desc")
    fun flowAll(): Flow<List<Book>>
    
    @Query("SELECT * FROM books WHERE type & ${BookType.audio} > 0")
    fun flowAudio(): Flow<List<Book>>
    
    // 带参数查询
    @Query("SELECT * FROM books WHERE (`group` & :group) > 0")
    fun flowByUserGroup(group: Long): Flow<List<Book>>
    
    // 模糊查询
    @Query("SELECT * FROM books WHERE name like '%'||:key||'%' or author like '%'||:key||'%'")
    fun flowSearch(key: String): Flow<List<Book>>
    
    // ================== 同步查询 ==================
    
    @Query("SELECT * FROM books WHERE bookUrl = :bookUrl")
    fun getBook(bookUrl: String): Book?
    
    @get:Query("SELECT * FROM books")
    val all: List<Book>
    
    @get:Query("SELECT COUNT(*) FROM books")
    val allBookCount: Int
    
    // ================== 插入操作 ==================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg book: Book)
    
    // ================== 更新操作 ==================
    
    @Update
    fun update(vararg book: Book)
    
    // 直接 SQL 更新
    @Query("update books set durChapterPos = :pos where bookUrl = :bookUrl")
    fun upProgress(bookUrl: String, pos: Int)
    
    // ================== 删除操作 ==================
    
    @Delete
    fun delete(vararg book: Book)
    
    @Query("delete from books where type & ${BookType.notShelf} > 0")
    fun deleteNotShelfBook()
    
    // ================== 判断操作 ==================
    
    @Query("select exists(select 1 from books where bookUrl = :bookUrl)")
    fun has(bookUrl: String): Boolean
}
```

### 4.2 注解详解

| 注解 | 作用 | 返回值 | 是否挂起 |
|------|------|--------|----------|
| `@Query` | 自定义 SQL 查询 | 任意类型 | 可选 `suspend` |
| `@Insert` | 插入数据 | `void`/`long`/`long[]` | 可选 `suspend` |
| `@Update` | 更新数据 | `int`（影响行数） | 可选 `suspend` |
| `@Delete` | 删除数据 | `int`（影响行数） | 可选 `suspend` |

### 4.3 返回值类型

```kotlin
@Dao
interface ExampleDao {
    
    // 单个对象
    @Query("SELECT * FROM books WHERE bookUrl = :url")
    fun getBook(url: String): Book?
    
    // 列表
    @Query("SELECT * FROM books")
    fun getAllBooks(): List<Book>
    
    // Flow 响应式（自动观察数据变化）
    @Query("SELECT * FROM books")
    fun observeBooks(): Flow<List<Book>>
    
    // 单个字段
    @Query("SELECT COUNT(*) FROM books")
    fun getCount(): Int
    
    // 插入返回行 ID
    @Insert
    fun insert(book: Book): Long
    
    // 批量插入返回 ID 列表
    @Insert
    fun insertAll(books: List<Book>): List<Long>
}
```

### 4.4 复杂查询示例

#### 4.4.1 多表联合查询

```kotlin
@Query("""
    select distinct bs.* from books, book_sources bs 
    where origin == bookSourceUrl 
    and origin not like '${BookType.localTag}%' 
    and origin not like '${BookType.webDavTag}%'
""")
fun getAllUseBookSource(): List<BookSource>
```

#### 4.4.2 条件聚合查询

```kotlin
@Query("""
    select * from books where type & ${BookType.text} > 0
    and type & ${BookType.local} = 0
    and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
    and (select show from book_groups where groupId = ${BookGroup.IdNetNone}) != 1
""")
fun flowRoot(): Flow<List<Book>>
```

#### 4.4.3 分组过滤查询

```kotlin
fun flowByGroup(groupId: Long): Flow<List<Book>> {
    return when (groupId) {
        BookGroup.IdRoot -> flowRoot()
        BookGroup.IdAll -> flowAll()
        BookGroup.IdLocal -> flowLocal()
        BookGroup.IdAudio -> flowAudio()
        else -> flowByUserGroup(groupId)
    }.map { list ->
        list.filterNot { it.isNotShelf }
    }
}
```

---

## 五、数据库迁移策略

### 5.1 迁移方式对比

| 迁移方式 | 适用场景 | 优点 | 缺点 |
|----------|----------|------|------|
| **自动迁移** | 简单结构变更（添加字段、添加表） | 无需编写代码 | 复杂变更不支持 |
| **手动迁移** | 复杂变更（删除字段、重命名、数据转换） | 完全可控 | 需要编写 SQL |
| **破坏性迁移** | 开发阶段、数据不重要 | 最简单 | 丢失所有数据 |

### 5.2 自动迁移（AutoMigration）

**文件**: `app/src/main/java/io/legado/app/data/AppDatabase.kt`

```kotlin
@Database(
    version = 76,
    autoMigrations = [
        // 简单自动迁移：版本 43 → 44
        AutoMigration(from = 43, to = 44),
        
        // 带规范的自动迁移：版本 54 → 55
        AutoMigration(
            from = 54, 
            to = 55, 
            spec = DatabaseMigrations.Migration_54_55::class
        ),
        
        // 删除列的自动迁移
        AutoMigration(
            from = 64, 
            to = 65, 
            spec = DatabaseMigrations.Migration_64_65::class
        ),
    ]
)
```

**自动迁移规范（AutoMigrationSpec）**:

```kotlin
// 在 Migration_54_55 中执行数据转换
@Suppress("ClassName")
class Migration_54_55 : AutoMigrationSpec {
    
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        // 迁移完成后执行的 SQL
        db.execSQL("""
            update books set type = ${BookType.audio}
            where type = ${BookSourceType.audio}
        """.trimIndent())
        // ... 其他转换
    }
}

// 删除列的规范
@Suppress("ClassName")
@DeleteColumn(
    tableName = "book_sources",
    columnName = "enabledReview"
)
class Migration_64_65 : AutoMigrationSpec
```

**支持的操作**：
- ✅ 添加新表
- ✅ 添加新列
- ✅ 删除列（需 `@DeleteColumn`）
- ✅ 重命名列（需 `@RenameColumn`）
- ❌ 重命名表
- ❌ 复杂的字段类型变更

### 5.3 手动迁移（Manual Migration）

**文件**: `app/src/main/java/io/legado/app/data/DatabaseMigrations.kt`

```kotlin
object DatabaseMigrations {
    
    // 集中管理所有手动迁移
    val migrations: Array<Migration> by lazy {
        arrayOf(
            migration_10_11, migration_11_12, migration_12_13,
            // ... 所有迁移
            migration_42_43,
        )
    }
    
    // 版本 10 → 11：删除旧表并创建新表
    private val migration_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE txtTocRules")
            database.execSQL("""
                CREATE TABLE txtTocRules(
                    id INTEGER NOT NULL, 
                    name TEXT NOT NULL, 
                    rule TEXT NOT NULL, 
                    serialNumber INTEGER NOT NULL, 
                    enable INTEGER NOT NULL, 
                    PRIMARY KEY (id)
                )
            """)
        }
    }
    
    // 版本 13 → 14：表结构重构（创建新表→迁移数据→删除旧表）
    private val migration_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 创建新表
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `books_new` (
                    `bookUrl` TEXT NOT NULL, 
                    `tocUrl` TEXT NOT NULL,
                    ... -- 新表结构
                    PRIMARY KEY(`bookUrl`)
                )
            """)
            
            // 2. 迁移数据
            database.execSQL("INSERT INTO books_new select * from books")
            
            // 3. 删除旧表
            database.execSQL("DROP TABLE books")
            
            // 4. 重命名新表
            database.execSQL("ALTER TABLE books_new RENAME TO books")
            
            // 5. 重建索引
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_books_name_author` 
                ON `books` (`name`, `author`)
            """)
        }
    }
    
    // 版本 21 → 22：新增字段
    private val migration_21_22 = object : Migration(21, 22) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `books_new` (...)
            """)
            database.execSQL("""
                INSERT INTO books_new select 
                    `bookUrl`, `tocUrl`, ..., `variable`, null  -- 新字段填充 null
                from books
            """)
            database.execSQL("DROP TABLE books")
            database.execSQL("ALTER TABLE books_new RENAME TO books")
        }
    }
    
    // 版本 32 → 33：关联查询迁移
    private val migration_32_33 = object : Migration(32, 33) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE bookmarks RENAME TO bookmarks_old")
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `bookmarks` (...)
            """)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_bookmarks_bookName_bookAuthor` 
                ON `bookmarks` (`bookName`, `bookAuthor`)
            """)
            // 使用 LEFT JOIN 从关联表获取数据
            database.execSQL("""
                insert into bookmarks (...)
                select time, ifNull(b.name, bookName) bookName, 
                       ifNull(b.author, bookAuthor) bookAuthor, ...
                from bookmarks_old o
                left join books b on o.bookUrl = b.bookUrl
            """)
        }
    }
}
```

### 5.4 迁移策略选择决策树

```
是否需要升级数据库？
    │
    ├── 版本号增加？
    │       ├── 是 → 继续
    │       └── 否 → 无需操作
    │
    └── 结构变更类型？
            │
            ├── 添加新表？
            │       └── 使用 AutoMigration
            │
            ├── 添加新列？
            │       └── 使用 AutoMigration
            │
            ├── 删除列？
            │       └── AutoMigration + @DeleteColumn
            │
            ├── 重命名列？
            │       └── AutoMigration + @RenameColumn
            │
            ├── 数据转换？
            │       └── AutoMigration + AutoMigrationSpec
            │
            ├── 表结构重构？
            │       └── Manual Migration
            │
            └── 复杂变更？
                    └── Manual Migration
```

### 5.5 迁移注意事项

1. **测试迁移**: 每次迁移都必须测试从旧版本升级
2. **数据备份**: 重要数据在迁移前建议备份
3. **事务保证**: Room 迁移自动在事务中执行
4. **版本连续性**: 迁移路径必须连续（不能跳版本）
5. **向下兼容**: Room 不支持降级，只能升级

---

## 六、复杂查询与事务

### 6.1 事务处理

```kotlin
@Dao
interface BookChapterDao {
    
    // 方式 1：使用 @Transaction 注解
    @Transaction
    suspend fun updateBookChapters(bookUrl: String, chapters: List<BookChapter>) {
        // 删除旧章节
        deleteChaptersByBookUrl(bookUrl)
        // 插入新章节
        insertAll(chapters)
        // 两个操作在同一个事务中，要么都成功，要么都失败
    }
    
    // 方式 2：显式事务（Repository 层）
    suspend fun batchUpdate(books: List<Book>) {
        appDb.runInTransaction {
            books.forEach { book ->
                bookDao.update(book)
            }
        }
    }
}
```

### 6.2 复杂查询技巧

#### 6.2.1 使用位运算存储多选状态

```kotlin
// BookType 定义
typealias BookType = Int

object BookType {
    const val text = 0               // 0000 0000 0000
    const val audio = 1              // 0000 0000 0001
    const val image = 2              // 0000 0000 0010
    const val local = 4              // 0000 0000 0100
    const val webFile = 8            // 0000 0000 1000
    const val updateError = 16       // 0000 0001 0000
    const val notShelf = 32          // 0000 0010 0000
}

// 查询所有音频书
@Query("SELECT * FROM books WHERE type & ${BookType.audio} > 0")
fun flowAudio(): Flow<List<Book>>

// 查询本地文本书（同时满足 local 和 text）
@Query("SELECT * FROM books WHERE type & ${BookType.local} > 0 AND type & ${BookType.text} > 0")
fun getLocalTextBooks(): List<Book>
```

#### 6.2.2 子查询与关联

```kotlin
// 查询未分组的书籍
@Query("""
    select * from books 
    where type & ${BookType.audio} = 0 
    and type & ${BookType.local} = 0
    and ((SELECT sum(groupId) FROM book_groups where groupId > 0) & `group`) = 0
""")
fun flowNetNoGroup(): Flow<List<Book>>
```

### 6.3 数据库视图（View）

```kotlin
// 定义视图实体
@Entity(
    tableName = "book_sources_part",  // 实际是一个视图
    // ...
)
data class BookSourcePart(
    @PrimaryKey
    val bookSourceUrl: String,
    val bookSourceName: String,
    // ...
)

// 在数据库中声明视图
@Database(
    views = [BookSourcePart::class],
    // ...
)
```

---

## 七、最佳实践

### 7.1 项目结构建议

```
data/
├── AppDatabase.kt              # 数据库配置
├── DatabaseMigrations.kt       # 迁移管理
├── entities/                   # 实体类
│   ├── Book.kt
│   ├── BookChapter.kt
│   └── ...
└── dao/                        # DAO 接口
    ├── BookDao.kt
    ├── BookChapterDao.kt
    └── ...
```

### 7.2 命名规范

| 项目 | 命名规则 | 示例 |
|------|----------|------|
| 数据库 | PascalCase | `AppDatabase` |
| 实体类 | PascalCase | `Book`, `BookChapter` |
| 表名 | snake_case | `books`, `book_chapters` |
| DAO 接口 | PascalCase + Dao | `BookDao`, `BookChapterDao` |
| 字段名 | camelCase | `bookUrl`, `chapterName` |
| 迁移 | migration_startVersion_endVersion | `migration_10_11` |

### 7.3 性能优化建议

1. **使用 Flow 替代频繁查询**
   ```kotlin
   // 不推荐：频繁手动查询
   fun refresh() {
       val books = bookDao.getAllBooks()
       // 更新 UI
   }
   
   // 推荐：使用 Flow 自动观察
   bookDao.flowAll().collect { books ->
       // 数据变化自动触发
   }
   ```

2. **批量操作代替单条**
   ```kotlin
   // 不推荐：循环插入
   books.forEach { bookDao.insert(it) }
   
   // 推荐：批量插入
   bookDao.insert(*books.toTypedArray())
   ```

3. **合理添加索引**
   - WHERE、ORDER BY、JOIN 条件字段
   - 避免在频繁更新的字段上建索引

4. **使用 @Transaction 保证数据一致性**

### 7.4 协程与 Room

```kotlin
// 在 ViewModel 中使用
class BookViewModel : ViewModel() {
    
    // 查询自动在 IO 线程执行
    fun getBooks(): Flow<List<Book>> = 
        appDb.bookDao.flowAll()
            .flowOn(Dispatchers.IO)
    
    // 插入使用 suspend 函数
    fun addBook(book: Book) {
        viewModelScope.launch {
            appDb.bookDao.insert(book)
        }
    }
}
```

---

## 八、常见问题排查

### 8.1 迁移失败

**问题**: `IllegalStateException: Migration didn't properly handle...`

**解决方案**:
1. 检查所有迁移版本是否连续
2. 确认 schema 导出文件与代码一致
3. 验证 SQL 语句语法正确
4. 测试从每个旧版本升级

### 8.2 主线程查询异常

**问题**: `Cannot access database on the main thread`

**解决方案**:
```kotlin
// 方式 1：使用 suspend 函数
@Query("SELECT * FROM books")
suspend fun getAllBooks(): List<Book>

// 方式 2：使用 Flow
@Query("SELECT * FROM books")
fun observeBooks(): Flow<List<Book>>

// 方式 3：允许主线程（仅调试）
.allowMainThreadQueries()
```

### 8.3 数据类型不匹配

**问题**: 实体类字段与数据库表结构不匹配

**解决方案**:
1. 检查 `@ColumnInfo` 的 `name` 属性
2. 确认 TypeConverter 已注册
3. 检查默认值设置
4. 清理并重建项目

### 8.4 版本号管理

**问题**: 忘记递增版本号导致异常

**解决方案**:
```kotlin
// 开发时可以先使用破坏性迁移
.fallbackToDestructiveMigration()

// 生产环境必须使用迁移
.addMigrations(*DatabaseMigrations.migrations)
```

---

## 参考资源

- **官方文档**: [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- **项目文件**:
  - `app/src/main/java/io/legado/app/data/AppDatabase.kt`
  - `app/src/main/java/io/legado/app/data/DatabaseMigrations.kt`
  - `app/src/main/java/io/legado/app/data/entities/Book.kt`
  - `app/src/main/java/io/legado/app/data/dao/BookDao.kt`

---

> 文档生成时间：2026年3月
> 数据库版本：76
