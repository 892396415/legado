# Entity 实体类设计详解

> 基于 Legado 项目的 Room Entity 设计实践，深入解析实体类注解、索引设计、关系映射和最佳实践。

## 目录

- [一、Entity 基础概念](#一entity-基础概念)
- [二、核心注解详解](#二核心注解详解)
- [三、主键设计策略](#三主键设计策略)
- [四、索引设计](#四索引设计)
- [五、字段定义](#五字段定义)
- [六、类型转换器](#六类型转换器)
- [七、关系映射](#七关系映射)
- [八、继承与接口](#八继承与接口)
- [九、最佳实践](#九最佳实践)
- [十、项目实战案例](#十项目实战案例)

---

## 一、Entity 基础概念

### 1.1 什么是 Entity

**Entity** 是 Room 中用于定义**数据表结构**的类，每个 Entity 对应数据库中的一张表。

```
Kotlin Entity Class          SQLite Table
       │                         │
       ▼                         ▼
┌──────────────┐           ┌──────────────┐
│ @Entity      │           │ CREATE TABLE │
│ data class   │    →      │ books (      │
│ Book {       │           │   bookUrl    │
│   @PrimaryKey│           │   name       │
│   url: String│           │   author     │
│ }            │           │ );           │
└──────────────┘           └──────────────┘
```

### 1.2 基本结构

```kotlin
@Entity(tableName = "books")  // 定义表名
data class Book(
    @PrimaryKey                 // 主键
    val bookUrl: String = "",
    
    @ColumnInfo(name = "book_name")  // 自定义列名
    val name: String = "",
    
    val author: String = ""     // 默认使用字段名作为列名
)
```

---

## 二、核心注解详解

### 2.1 @Entity - 实体标记

```kotlin
@Entity(
    tableName = "books",                    // 表名（默认使用类名）
    indices = [...],                        // 索引定义
    foreignKeys = [...],                    // 外键约束
    inheritSuperIndices = false             // 是否继承父类索引
)
```

### 2.2 @PrimaryKey - 主键

```kotlin
// 方式 1：单字段主键
@Entity
data class Book(
    @PrimaryKey
    val bookUrl: String = ""
)

// 方式 2：自增主键
@Entity
data class ReplaceRule(
    @PrimaryKey(autoGenerate = true)
    var id: Long = System.currentTimeMillis()
)

// 方式 3：复合主键
@Entity(
    primaryKeys = ["url", "bookUrl"]
)
data class BookChapter(
    var url: String = "",
    var bookUrl: String = ""
)
```

### 2.3 @ColumnInfo - 列信息

```kotlin
@Entity
data class Book(
    // 自定义列名
    @ColumnInfo(name = "book_name")
    val name: String = "",
    
    // 设置默认值
    @ColumnInfo(defaultValue = "0")
    val order: Int = 0,
    
    // 指定类型（很少需要）
    @ColumnInfo(typeAffinity = ColumnInfo.TEXT)
    val data: String = "",
    
    // 不为 null
    @ColumnInfo(name = "is_enabled", defaultValue = "1")
    val isEnabled: Boolean = true
)
```

**常用属性**：

| 属性 | 作用 | 示例 |
|------|------|------|
| `name` | 列名 | `@ColumnInfo(name = "user_name")` |
| `defaultValue` | 默认值 | `@ColumnInfo(defaultValue = "0")` |
| `typeAffinity` | 类型亲和性 | `TEXT`, `INTEGER`, `REAL`, `BLOB` |

### 2.4 @Ignore - 忽略字段

```kotlin
@Entity
data class Book(
    @PrimaryKey
    val bookUrl: String = "",
    val name: String = "",
    
    // 不存入数据库的字段
    @Ignore
    var tempData: String? = null,
    
    @Ignore
    var downloadUrls: List<String>? = null
)
```

### 2.5 注解组合使用

```kotlin
@Parcelize                                    // 支持序列化
@Entity(
    tableName = "books",
    indices = [Index(value = ["name", "author"], unique = true)]
)
@TypeConverters(Book.Converters::class)      // 类型转换器
data class Book(
    @PrimaryKey
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    
    // 组合使用多个注解
    @ColumnInfo(defaultValue = "")
    val name: String = "",
    
    @Ignore
    @IgnoredOnParcel
    var infoHtml: String? = null
) : Parcelable
```

---

## 三、主键设计策略

### 3.1 主键类型对比

| 类型 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **自增 ID** | 简单、连续 | 可能暴露数据量 | 内部关联表 |
| **业务主键** | 有意义、唯一 | 可能变更 | URL、ISBN 等 |
| **复合主键** | 业务唯一性 | 查询复杂 | 多对多关系 |
| **UUID** | 全局唯一 | 占用空间大 | 分布式系统 |

### 3.2 Legado 项目主键设计

```kotlin
// 1. 业务主键 - Book（书籍 URL 作为唯一标识）
@Entity(tableName = "books")
data class Book(
    @PrimaryKey
    val bookUrl: String = ""  // 书籍详情页 URL
)

// 2. 自增主键 - ReplaceRule（ID 无业务含义）
@Entity(tableName = "replace_rules")
data class ReplaceRule(
    @PrimaryKey(autoGenerate = true)
    var id: Long = System.currentTimeMillis()
)

// 3. 时间戳主键 - Bookmark（时间戳保证唯一）
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey
    val time: Long = System.currentTimeMillis()
)

// 4. 复合主键 - BookChapter（章节 URL + 书籍 URL）
@Entity(
    tableName = "chapters",
    primaryKeys = ["url", "bookUrl"]
)
data class BookChapter(
    var url: String = "",      // 章节 URL
    var bookUrl: String = ""   // 所属书籍 URL
)
```

### 3.3 主键设计原则

1. **稳定性**：主键值不应随时间改变
2. **唯一性**：必须全局唯一
3. **简洁性**：尽量简短，提高索引效率
4. **业务无关性**：避免使用业务属性（除非确定不变）

---

## 四、索引设计

### 4.1 索引基础

**什么是索引？**
```
没有索引：
SELECT * FROM books WHERE name = '三体'
→ 全表扫描，逐行比较（慢）

有索引：
→ 直接定位到 name='三体' 的位置（快）
```

### 4.2 @Index 注解

```kotlin
@Entity(
    tableName = "books",
    indices = [
        // 单字段索引
        Index(value = ["name"]),
        
        // 复合索引
        Index(value = ["name", "author"]),
        
        // 唯一索引（防止重复）
        Index(value = ["bookUrl"], unique = true),
        
        // 命名索引
        Index(
            name = "idx_name_author", 
            value = ["name", "author"], 
            unique = true
        )
    ]
)
```

### 4.3 索引类型与应用场景

#### 4.3.1 单字段索引

```kotlin
@Entity(
    tableName = "bookmarks",
    indices = [(Index(value = ["time"], unique = true))]
)
data class Bookmark(
    @PrimaryKey
    val time: Long = System.currentTimeMillis()
    // ...
)
```

**适用场景**：按单个字段查询、排序（如按时间排序）

#### 4.3.2 复合索引

```kotlin
@Entity(
    tableName = "books",
    indices = [Index(value = ["name", "author"], unique = true)]
)
data class Book(
    val name: String = "",
    val author: String = ""
)
```

**适用场景**：多字段联合查询（如根据书名+作者查找）

**最左前缀原则**：
```sql
-- 索引是 (name, author)
WHERE name = '三体'                    -- ✓ 使用索引
WHERE name = '三体' AND author = '刘'   -- ✓ 使用索引
WHERE author = '刘'                    -- ✗ 不使用索引（缺少 name）
```

#### 4.3.3 唯一索引

```kotlin
@Entity(
    tableName = "caches",
    indices = [(Index(value = ["key"], unique = true))]
)
data class Cache(
    @PrimaryKey
    val key: String = "",
    var value: String? = null
)
```

**适用场景**：保证字段唯一性（如缓存 key、用户名）

### 4.4 项目中的索引实战

```kotlin
// 1. Book - 书名+作者唯一索引（防止重复书籍）
@Entity(
    tableName = "books",
    indices = [Index(value = ["name", "author"], unique = true)]
)

// 2. BookChapter - 复合索引（按书籍查询章节）
@Entity(
    tableName = "chapters",
    indices = [
        Index(value = ["bookUrl"]),                    // 查询某书的所有章节
        Index(value = ["bookUrl", "index"], unique = true)  // 章节序号唯一
    ]
)

// 3. SearchBook - 唯一索引 + 普通索引
@Entity(
    tableName = "searchBooks",
    indices = [
        Index(value = ["bookUrl"], unique = true),     // URL 唯一
        Index(value = ["origin"])                       // 按书源查询
    ]
)

// 4. Cookie - URL 唯一索引
@Entity(
    tableName = "cookies",
    indices = [(Index(value = ["url"], unique = true))]
)
```

### 4.5 索引设计原则

| 原则 | 说明 | 示例 |
|------|------|------|
| **查询条件字段** | WHERE、ORDER BY、JOIN 的字段 | `bookUrl`, `name` |
| **外键字段** | 外键列自动有索引 | `bookUrl` in chapters |
| **避免过度索引** | 每个额外索引都影响写入性能 | 不要给所有字段建索引 |
| **区分度高的字段** | 值越分散，索引效果越好 | `bookUrl` > `type` |

---

## 五、字段定义

### 5.1 字段类型映射

| Kotlin 类型 | SQLite 类型 | 说明 |
|-------------|-------------|------|
| `String` | `TEXT` | 文本 |
| `Int` | `INTEGER` | 整数 |
| `Long` | `INTEGER` | 长整数 |
| `Boolean` | `INTEGER` (0/1) | 布尔值 |
| `Float` | `REAL` | 浮点数 |
| `Double` | `REAL` | 双精度 |
| `ByteArray` | `BLOB` | 二进制 |

### 5.2 可空类型处理

```kotlin
@Entity
data class Book(
    @PrimaryKey
    val bookUrl: String = "",
    
    // 非空字段（必须有值）
    val name: String = "",
    
    // 可空字段（数据库中可为 NULL）
    val coverUrl: String? = null,
    val intro: String? = null,
    val kind: String? = null
)
```

**建议**：尽量使用可空类型，避免用空字符串 `""` 表示无值。

### 5.3 默认值设置

```kotlin
@Entity
data class Book(
    @PrimaryKey
    val bookUrl: String = "",
    
    // Kotlin 默认值
    val name: String = "",
    
    // 数据库默认值（用于新增字段时的旧数据）
    @ColumnInfo(defaultValue = "0")
    val order: Int = 0,
    
    @ColumnInfo(defaultValue = "1")
    val canUpdate: Boolean = true,
    
    @ColumnInfo(defaultValue = "0")
    val latestChapterTime: Long = System.currentTimeMillis()
)
```

**Kotlin 默认值 vs @ColumnInfo(defaultValue)**：
- **Kotlin 默认值**：代码层面的默认值
- **@ColumnInfo(defaultValue)**：数据库层面的默认值（数据库升级时旧数据使用）

### 5.4 特殊字段处理

#### 5.4.1 时间戳字段

```kotlin
@Entity
data class SearchBook(
    @PrimaryKey
    val bookUrl: String = "",
    
    // 自动设置当前时间
    var time: Long = System.currentTimeMillis()
)
```

#### 5.4.2 位运算存储多选状态

```kotlin
@Entity
data class Book(
    @PrimaryKey
    val bookUrl: String = "",
    
    // 使用位运算存储多个类型标记
    // type = 5 (二进制 101) 表示：local(4) + text(1)
    @ColumnInfo(defaultValue = "0")
    var type: Int = BookType.text
)

// 类型常量定义
object BookType {
    const val text = 0        // 0000 文本
    const val audio = 1       // 0001 音频
    const val image = 2       // 0010 图片
    const val local = 4       // 0100 本地
    const val webFile = 8     // 1000 文件
}
```

#### 5.4.3 JSON 字符串存储对象

```kotlin
@Entity
@TypeConverters(Book.Converters::class)
data class Book(
    @PrimaryKey
    val bookUrl: String = "",
    
    // 复杂对象转为 JSON 存储
    var readConfig: ReadConfig? = null
) {
    // 类型转换器
    class Converters {
        @TypeConverter
        fun readConfigToString(config: ReadConfig?): String = 
            GSON.toJson(config)

        @TypeConverter
        fun stringToReadConfig(json: String?) = 
            GSON.fromJsonObject<ReadConfig>(json).getOrNull()
    }
}
```

---

## 六、类型转换器

### 6.1 为什么需要 TypeConverter

Room 只支持基本类型，复杂类型需要转换：

```kotlin
// ❌ 不支持直接存储
@Entity
data class Book(
    var readConfig: ReadConfig? = null,  // 自定义对象
    var tags: List<String>? = null,       // 集合
    var createTime: Date? = null          // Date 类型
)

// ✅ 使用 TypeConverter 转换
@Entity
@TypeConverters(Book.Converters::class)
data class Book(
    var readConfig: ReadConfig? = null    // 转为 JSON 字符串存储
)
```

### 6.2 TypeConverter 定义

```kotlin
class Converters {
    
    // ReadConfig ↔ JSON String
    @TypeConverter
    fun readConfigToString(config: ReadConfig?): String = 
        GSON.toJson(config)

    @TypeConverter
    fun stringToReadConfig(json: String?) = 
        GSON.fromJsonObject<ReadConfig>(json).getOrNull()
    
    // 更多转换器...
}
```

### 6.3 应用 TypeConverter

#### 方式 1：单个 Entity

```kotlin
@Entity
@TypeConverters(Book.Converters::class)
data class Book(...)
```

#### 方式 2：全局配置（Database 级别）

```kotlin
@Database(
    entities = [Book::class, ...],
    version = 76
)
@TypeConverters(AppTypeConverters::class)  // 全局转换器
abstract class AppDatabase : RoomDatabase() { ... }
```

### 6.4 项目实战：BookSource 的类型转换

```kotlin
@TypeConverters(BookSource.Converters::class)
@Entity(tableName = "book_sources")
data class BookSource(
    @PrimaryKey
    var bookSourceUrl: String = "",
    
    // 多个复杂规则对象
    var ruleExplore: ExploreRule? = null,
    var ruleSearch: SearchRule? = null,
    var ruleBookInfo: BookInfoRule? = null,
    var ruleToc: TocRule? = null,
    var ruleContent: ContentRule? = null,
    var ruleReview: ReviewRule? = null
) {
    class Converters {
        
        @TypeConverter
        fun exploreRuleToString(exploreRule: ExploreRule?): String =
            GSON.toJson(exploreRule)

        @TypeConverter
        fun stringToExploreRule(json: String?) =
            GSON.fromJsonObject<ExploreRule>(json).getOrNull()

        @TypeConverter
        fun searchRuleToString(searchRule: SearchRule?): String =
            GSON.toJson(searchRule)

        @TypeConverter
        fun stringToSearchRule(json: String?) =
            GSON.fromJsonObject<SearchRule>(json).getOrNull()

        // ... 其他规则对象的转换器
    }
}
```

### 6.5 常见类型转换器

```kotlin
class AppTypeConverters {
    
    // Date ↔ Long
    @TypeConverter
    fun dateToLong(date: Date?): Long? = date?.time

    @TypeConverter
    fun longToDate(timestamp: Long?): Date? = 
        timestamp?.let { Date(it) }
    
    // List<String> ↔ JSON String
    @TypeConverter
    fun listToString(list: List<String>?): String = 
        GSON.toJson(list)

    @TypeConverter
    fun stringToList(json: String?): List<String>? =
        GSON.fromJson(json, object : TypeToken<List<String>>() {}.type)
    
    // Enum ↔ String
    @TypeConverter
    fun statusToString(status: Status?): String? = 
        status?.name

    @TypeConverter
    fun stringToStatus(name: String?): Status? = 
        name?.let { Status.valueOf(it) }
}
```

---

## 七、关系映射

### 7.1 外键约束（ForeignKey）

```kotlin
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,              // 父表
            parentColumns = ["bookUrl"],        // 父表列
            childColumns = ["bookUrl"],         // 子表列
            onDelete = ForeignKey.CASCADE       // 级联删除
        )
    ]
)
data class BookChapter(
    var bookUrl: String = "",  // 外键
    var url: String = ""
)
```

**级联操作**：

| 操作 | 说明 | 场景 |
|------|------|------|
| `CASCADE` | 父记录删除，子记录也删除 | 删除书籍时删除所有章节 |
| `SET_NULL` | 父记录删除，子记录外键设为 NULL | 可选关联 |
| `SET_DEFAULT` | 设置为默认值 | 有默认值时 |
| `RESTRICT` | 阻止删除 | 保护数据完整性 |
| `NO_ACTION` | 不采取任何操作 | 默认行为 |

### 7.2 项目中的外键应用

```kotlin
// BookChapter - 章节关联到书籍
@Entity(
    tableName = "chapters",
    foreignKeys = [(ForeignKey(
        entity = Book::class,
        parentColumns = ["bookUrl"],
        childColumns = ["bookUrl"],
        onDelete = ForeignKey.CASCADE  // 删除书籍时自动删除章节
    ))]
)
data class BookChapter(...)

// SearchBook - 搜索结果关联到书源
@Entity(
    tableName = "searchBooks",
    foreignKeys = [(ForeignKey(
        entity = BookSource::class,
        parentColumns = ["bookSourceUrl"],
        childColumns = ["origin"],
        onDelete = ForeignKey.CASCADE
    ))]
)
data class SearchBook(...)
```

### 7.3 索引与外键

**重要**：外键字段必须创建索引，否则会报错。

```kotlin
@Entity(
    tableName = "chapters",
    indices = [
        Index(value = ["bookUrl"])  // 外键必须有索引
    ],
    foreignKeys = [...]
)
```

---

## 八、继承与接口

### 8.1 接口定义公共属性

```kotlin
// 基础接口
interface BaseBook : RuleDataInterface {
    var name: String
    var author: String
    var bookUrl: String
    var kind: String?
    var wordCount: String?
    var variable: String?
    
    // 公共方法
    fun putCustomVariable(value: String?)
    fun getCustomVariable(): String
}

// 实现接口
@Entity(tableName = "books")
data class Book(
    @PrimaryKey
    override var bookUrl: String = "",
    override var name: String = "",
    override var author: String = "",
    override var kind: String? = null,
    override var wordCount: String? = null,
    override var variable: String? = null
) : BaseBook
```

### 8.2 抽象类复用代码

```kotlin
// 抽象基类（Room 2.0+ 支持）
abstract class BaseEntity {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
    
    var createTime: Long = System.currentTimeMillis()
    var updateTime: Long = System.currentTimeMillis()
}

@Entity
data class Book(
    val name: String = ""
) : BaseEntity()
```

### 8.3 项目实战：BaseBook 接口

```kotlin
// BaseBook.kt - 定义书籍的公共属性和方法
interface BaseBook : RuleDataInterface {
    var name: String
    var author: String
    var bookUrl: String
    var kind: String?
    var wordCount: String?
    var variable: String?
    
    var infoHtml: String?
    var tocHtml: String?
    
    // 变量管理
    override fun putVariable(key: String, value: String?): Boolean
    
    // 大数据变量（存储到单独表）
    override fun putBigVariable(key: String, value: String?)
    override fun getBigVariable(key: String): String?
    
    // 工具方法
    fun getKindList(): List<String>
}

// Book.kt - 完整书籍信息
@Entity(tableName = "books")
data class Book(
    @PrimaryKey
    override var bookUrl: String = "",
    override var name: String = "",
    override var author: String = "",
    // ... 实现所有接口属性
) : Parcelable, BaseBook { ... }

// SearchBook.kt - 搜索结果（简化版书籍）
@Entity(tableName = "searchBooks")
data class SearchBook(
    @PrimaryKey
    override var bookUrl: String = "",
    override var name: String = "",
    override var author: String = "",
    // ... 实现接口属性
) : Parcelable, BaseBook, Comparable<SearchBook> { ... }
```

**优势**：
- 统一的数据结构定义
- 共享业务逻辑
- 类型安全
- 便于扩展

---

## 九、最佳实践

### 9.1 命名规范

| 项目 | 规范 | 示例 |
|------|------|------|
| **类名** | PascalCase | `Book`, `BookChapter` |
| **表名** | snake_case | `books`, `book_chapters` |
| **字段名** | camelCase | `bookUrl`, `chapterName` |
| **索引名** | idx_前缀 | `idx_name_author` |
| **外键名** | fk_前缀 | `fk_book_chapter` |

### 9.2 字段设计原则

1. **使用 data class**
   ```kotlin
   // ✅ 推荐
   @Entity
data class Book(...)
   
   // ❌ 不推荐
   @Entity
   class Book { ... }
   ```

2. **合理使用可空类型**
   ```kotlin
   // ✅ 明确语义
   val coverUrl: String? = null  // 可能没有封面
   
   // ❌ 不明确
   val coverUrl: String = ""     // 空字符串也是有值
   ```

3. **设置合适的默认值**
   ```kotlin
   @ColumnInfo(defaultValue = "1")
   var isEnabled: Boolean = true
   
   @ColumnInfo(defaultValue = "0")
   var order: Int = 0
   ```

4. **使用 @Ignore 排除临时字段**
   ```kotlin
   @Ignore
   var downloadProgress: Int = 0  // 临时状态，不持久化
   ```

### 9.3 性能优化

1. **索引优化**
   - 给查询频繁的字段加索引
   - 外键自动创建索引
   - 避免过多索引影响写入性能

2. **字段类型优化**
   ```kotlin
   // ✅ 使用合适的类型
   @ColumnInfo(defaultValue = "0")
   var type: Int = 0  // 用 Int 存储枚举
   
   // ❌ 避免大对象
   // var largeData: ByteArray? = null  // 考虑存文件
   ```

3. **延迟加载**
   ```kotlin
   @delegate:Ignore
   @IgnoredOnParcel
   val variableMap: HashMap<String, String> by lazy {
       // 延迟解析，避免每次创建对象都解析
       GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
   }
   ```

### 9.4 版本演进

新增字段时如何处理：

```kotlin
// 版本 1
@Entity
data class Book(
    @PrimaryKey
    val bookUrl: String = "",
    val name: String = ""
)

// 版本 2 - 新增字段
@Entity
data class Book(
    @PrimaryKey
    val bookUrl: String = "",
    val name: String = "",
    
    // 新增字段，必须设置默认值
    @ColumnInfo(defaultValue = "0")
    val order: Int = 0
)
```

---

## 十、项目实战案例

### 10.1 Book - 完整书籍实体

```kotlin
@Parcelize
@TypeConverters(Book.Converters::class)
@Entity(
    tableName = "books",
    indices = [Index(value = ["name", "author"], unique = true)]
)
data class Book(
    // 主键：书籍 URL
    @PrimaryKey
    @ColumnInfo(defaultValue = "")
    override var bookUrl: String = "",
    
    // 基本信息
    @ColumnInfo(defaultValue = "")
    var tocUrl: String = "",
    @ColumnInfo(defaultValue = "")
    var origin: String = "",
    @ColumnInfo(defaultValue = "")
    var originName: String = "",
    @ColumnInfo(defaultValue = "")
    override var name: String = "",
    @ColumnInfo(defaultValue = "")
    override var author: String = "",
    override var kind: String? = null,
    var customTag: String? = null,
    
    // 媒体信息
    var coverUrl: String? = null,
    var customCoverUrl: String? = null,
    var intro: String? = null,
    var customIntro: String? = null,
    var charset: String? = null,
    
    // 类型和分组（位运算存储）
    @ColumnInfo(defaultValue = "0")
    var type: Int = BookType.text,
    @ColumnInfo(defaultValue = "0")
    var group: Long = 0,
    
    // 章节信息
    var latestChapterTitle: String? = null,
    @ColumnInfo(defaultValue = "0")
    var latestChapterTime: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    var lastCheckTime: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    var lastCheckCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var totalChapterNum: Int = 0,
    
    // 阅读进度
    var durChapterTitle: String? = null,
    @ColumnInfo(defaultValue = "0")
    var durChapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var durChapterPos: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var durChapterTime: Long = System.currentTimeMillis(),
    
    // 其他
    override var wordCount: String? = null,
    @ColumnInfo(defaultValue = "1")
    var canUpdate: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    @ColumnInfo(defaultValue = "0")
    var originOrder: Int = 0,
    override var variable: String? = null,
    var readConfig: ReadConfig? = null,  // 需要 TypeConverter
    @ColumnInfo(defaultValue = "0")
    var syncTime: Long = 0L
) : Parcelable, BaseBook {
    
    // 类型转换器
    class Converters {
        @TypeConverter
        fun readConfigToString(config: ReadConfig?): String = GSON.toJson(config)

        @TypeConverter
        fun stringToReadConfig(json: String?) = GSON.fromJsonObject<ReadConfig>(json).getOrNull()
    }
    
    // 业务方法...
}
```

### 10.2 ReplaceRule - 替换规则实体

```kotlin
@Parcelize
@Entity(
    tableName = "replace_rules",
    indices = [(Index(value = ["id"]))]
)
data class ReplaceRule(
    // 自增主键
    @PrimaryKey(autoGenerate = true)
    var id: Long = System.currentTimeMillis(),
    
    @ColumnInfo(defaultValue = "")
    var name: String = "",
    var group: String? = null,
    
    // 替换规则
    @ColumnInfo(defaultValue = "")
    var pattern: String = "",
    @ColumnInfo(defaultValue = "")
    var replacement: String = "",
    
    // 作用范围
    var scope: String? = null,
    @ColumnInfo(defaultValue = "0")
    var scopeTitle: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    var scopeContent: Boolean = true,
    var excludeScope: String? = null,
    
    // 状态
    @ColumnInfo(defaultValue = "1")
    var isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    var isRegex: Boolean = true,
    
    // 性能
    @ColumnInfo(defaultValue = "3000")
    var timeoutMillisecond: Long = 3000L,
    
    // 排序
    @ColumnInfo(name = "sortOrder", defaultValue = "0")
    var order: Int = Int.MIN_VALUE
) : Parcelable
```

### 10.3 Cache - 简单键值对实体

```kotlin
@Entity(
    tableName = "caches",
    indices = [(Index(value = ["key"], unique = true))]
)
data class Cache(
    @PrimaryKey
    val key: String = "",
    var value: String? = null,
    var deadline: Long = 0L  // 过期时间
)
```

---

## 参考资源

- **Room 官方文档**: [Entities](https://developer.android.com/training/data-storage/room/defining-data)
- **SQLite 数据类型**: [Datatypes In SQLite](https://www.sqlite.org/datatype3.html)
- **项目文件**:
  - `app/src/main/java/io/legado/app/data/entities/Book.kt`
  - `app/src/main/java/io/legado/app/data/entities/BookChapter.kt`
  - `app/src/main/java/io/legado/app/data/entities/BookSource.kt`
  - `app/src/main/java/io/legado/app/data/entities/BaseBook.kt`

---

> 文档生成时间：2026年3月
> 覆盖实体类：Book、BookChapter、BookSource、BookGroup、ReplaceRule、Cache 等
