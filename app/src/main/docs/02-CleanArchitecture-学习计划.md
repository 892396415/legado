# Clean Architecture 分层设计 - 详细学习计划

> 基于 Legado 项目源码学习 Clean Architecture 分层设计思想

---

## 一、概述

### 1.1 什么是 Clean Architecture

Clean Architecture（清晰架构）是一种软件架构设计原则，旨在将代码分离为不同的层次，使得每一层都有明确的职责，降低耦合度，提高可维护性和可测试性。

### 1.2 Legado 项目的分层结构

Legado 项目采用了清晰的四层架构：

```
┌─────────────────────────────────────────┐
│              UI Layer                   │  ← Activities, Fragments, Custom Views
├─────────────────────────────────────────┤
│             Model Layer                 │  ← 业务逻辑、规则解析
├─────────────────────────────────────────┤
│              Data Layer                 │  ← 数据库操作、数据源
├─────────────────────────────────────────┤
│            Service Layer                │  ← 后台服务
└─────────────────────────────────────────┘
```

### 1.3 层的依赖方向

**核心原则：外层依赖内层，内层不知道外层**

```
UI → Model → Data → (Service)
```

- UI 层可以调用 Model 层
- Model 层可以调用 Data 层
- Service 层相对独立，可被各层调用

---

## 二、各层职责详解

### 2.1 UI Layer（表现层）

**职责**：
- 展示用户界面
- 处理用户交互
- 接收用户输入
- 显示数据结果

**包含组件**：
- Activities
- Fragments
- Custom Views
- ViewModels（部分）

**关键目录**：
```
app/src/main/java/io/legado/app/ui/
├── main/              # 主界面
├── book/              # 书籍相关界面
├── config/            # 设置界面
├── widget/            # 自定义控件
│   ├── image/         # 图片控件
│   ├── text/          # 文本控件
│   └── recycler/      # RecyclerView相关
├── rss/               # RSS阅读
├── browser/           # 浏览器
└── about/             # 关于
```

**学习要点**：
- Activity/Fragment 的生命周期管理
- ViewBinding 的使用
- 用户交互事件处理
- 数据的展示与刷新

---

### 2.2 Model Layer（业务模型层）

**职责**：
- 业务逻辑实现
- 规则解析
- 数据转换
- 业务规则封装

**包含组件**：
- 业务处理类
- 规则解析器
- 数据模型
- 脚本执行

**关键目录**：
```
app/src/main/java/io/legado/app/model/
├── ReadBook.kt           # 阅读核心逻辑
├── ReadAloud.kt         # 朗读核心逻辑
├── Download.kt          # 下载逻辑
├── webBook/              # 网页书籍
│   ├── SearchModel.kt   # 搜索模型
│   ├── BookInfo.kt      # 书籍信息
│   └── BookChapterList.kt
├── localBook/            # 本地书籍
│   ├── TxtBook.kt       # TXT解析
│   ├── EpubBook.kt      # EPUB解析
│   └── LocalBook.kt
├── analyzeRule/          # 规则解析
│   ├── AnalyzeRule.kt
│   ├── AnalyzeByXPath.kt
│   ├── AnalyzeByJSoup.kt
│   └── AnalyzeByJSonPath.kt
└── rss/                  # RSS处理
```

**学习要点**：
- 业务逻辑的抽取
- 规则的定义与执行
- 数据转换与格式化
- 业务规则的复用

---

### 2.3 Data Layer（数据层）

**职责**：
- 数据持久化
- 数据库操作
- 数据缓存
- 数据源管理

**包含组件**：
- Room 数据库
- DAO 接口
- Entity 实体
- 缓存管理

**关键目录**：
```
app/src/main/java/io/legado/app/data/
├── AppDatabase.kt        # 数据库配置
├── dao/                  # 数据访问对象
│   ├── BookDao.kt
│   ├── BookChapterDao.kt
│   ├── BookSourceDao.kt
│   └── ...
├── entities/             # 实体类
│   ├── Book.kt
│   ├── BookChapter.kt
│   ├── BookSource.kt
│   └── rule/             # 规则实体
└── DatabaseMigrations.kt # 数据库迁移
```

**学习要点**：
- Room 数据库配置
- Entity 实体设计
- DAO 增删改查
- 数据库迁移策略

---

### 2.4 Service Layer（服务层）

**职责**：
- 后台任务执行
- 长时间运行任务
- 系统服务集成
- 定时任务

**关键目录**：
```
app/src/main/java/io/legado/app/service/
├── CacheBookService.kt      # 书籍缓存服务
├── DownloadService.kt       # 下载服务
├── CheckSourceService.kt    # 书源检查服务
├── AudioPlayService.kt      # 音频播放服务
├── TTSReadAloudService.kt   # TTS朗读服务
└── BaseReadAloudService.kt  # 朗读服务基类
```

**学习要点**：
- Service 的创建与配置
- 前台服务与后台服务
- 服务的生命周期
- 与其他层的数据交互

---

## 三、学习路径

### 阶段一：理解架构思想（1-2天）

#### 目标
理解 Clean Architecture 的核心概念和 Legado 项目的分层结构

#### 任务

1. **阅读文档**
   - 复习本指南的"概述"部分
   - 理解各层的职责边界

2. **浏览目录结构**
   ```bash
   # 查看项目包结构
   app/src/main/java/io/legado/app/
   ```

3. **理解依赖方向**
问题:
绘制各层之间的调用关系图
答案:
   项目四层架构（UI → Model → Data → Service）的调用方向。根据文档前文描述，正确的调用关系应符合 "外层依赖内层，内层不知道外层" 的原则：
   UI 层（表现层）可调用 Model 层（业务模型层）
   Model 层可调用 Data 层（数据层）
   Service 层（服务层）相对独立，可被其他各层调用
   绘制此图的目的是帮助理解各层的交互边界，明确谁能调用谁，避免出现反向依赖（如 Data 层调用 UI 层）。
问题:
理解为什么需要单向依赖
答案:
   单向依赖是 Clean Architecture 的核心设计原则，主要原因包括：
   降低耦合度：内层（如 Data 层、Model 层）专注于核心业务逻辑和数据处理，不依赖外层（如 UI 层）的具体实现，避免因外层变化（如 UI 框架更换）影响内层稳定性。
   提高可维护性：各层职责单一，修改某一层时只需关注其内部逻辑，无需担心对其他层的连锁影响。
   增强可测试性：内层可独立于外层进行单元测试（如无需启动 UI 即可测试 Model 层的业务逻辑）。
   保障核心逻辑稳定：内层（尤其是 Model 层和 Data 层）包含项目的核心业务规则和数据模型，单向依赖确保这些核心逻辑不被外层的易变因素（如界面交互方式）干扰。
   简单来说，单向依赖确保了架构的"稳定性"——越靠近内层的代码（核心逻辑）越稳定，越靠近外层的代码（如 UI、服务）越灵活，可根据需求灵活调整而不破坏整体架构。

#### 验证
能用自己的话解释：
- 什么是 Clean Architecture
- Legado 项目分为哪几层
- 各层的职责是什么

---

### 阶段二：深入 UI Layer（3-4天）

#### 目标
掌握 UI 层的实现方式，理解 Activity/Fragment 与 ViewModel 的配合

#### 关键文件

| 文件 | 说明 |
|------|------|
| `ui/main/MainActivity.kt` | 主界面 Activity |
| `ui/book/toc/TocActivity.kt` | 目录界面 |
| `base/BaseActivity.kt` | Activity 基类 |
| `base/BaseFragment.kt` | Fragment 基类 |

#### 学习任务

1. **学习 BaseActivity**
   ```kotlin
   // 位置: app/src/main/java/io/legado/app/base/BaseActivity.kt
   abstract class BaseActivity<VB : ViewBinding>(
       val fullScreen: Boolean = true,
       private val theme: Theme = Theme.Auto
   ) : AppCompatActivity() {
       protected abstract val binding: VB
       // 封装了通用逻辑：主题、权限、Toast 等
   }
   ```

2. **理解 ViewBinding 集成**
   - 如何在 BaseActivity 中统一初始化
   - ViewBinding 的优势

3. **分析典型页面**
   - 阅读 MainActivity 的代码流程
   - 理解数据如何展示到 UI

#### 实践任务
1. 找到 `ui/book/toc/TocActivity.kt`，分析其职责
2. 绘制该 Activity 的数据流图

---

### 阶段三：深入 Model Layer（4-5天）

#### 目标
掌握业务逻辑的实现方式，理解规则解析的原理

#### 关键文件

| 文件 | 说明 |
|------|------|
| `model/ReadBook.kt` | 阅读核心逻辑 |
| `model/analyzeRule/AnalyzeRule.kt` | 规则解析 |
| `model/webBook/SearchModel.kt` | 搜索模型 |

#### 学习任务

1. **学习阅读核心逻辑**
   ```kotlin
   // 位置: app/src/main/java/io/legado/app/model/ReadBook.kt
   // 核心职责：
   // - 章节内容加载
   // - 翻页逻辑
   // - 阅读进度保存
   ```

2. **学习规则解析**
   ```kotlin
   // 位置: app/src/main/java/io/legado/app/model/analyzeRule/
   // 支持的解析方式：
   // - XPath 解析 HTML
   // - JSONPath 解析 JSON
   // - Regex 正则匹配
   // - JavaScript 脚本
   ```

3. **理解业务逻辑抽取**
   - 哪些逻辑放在 Model 层
   - Model 层如何被 UI 层调用

#### 实践任务
1. 阅读 `model/analyzeRule/AnalyzeByXPath.kt`，理解 XPath 解析原理
2. 分析 `model/webBook/SearchModel.kt`，理解搜索流程

---

### 阶段四：深入 Data Layer（3-4天）

#### 目标
掌握 Room 数据库的使用，理解数据持久化

#### 关键文件

| 文件 | 说明 |
|------|------|
| `data/AppDatabase.kt` | 数据库配置 |
| `data/dao/BookDao.kt` | 书籍 DAO |
| `data/entities/Book.kt` | 书籍实体 |

#### 学习任务

1. **学习数据库配置**
   ```kotlin
   // 位置: app/src/main/java/io/legado/app/data/AppDatabase.kt
   @Database(
       entities = [Book::class, BookChapter::class, ...],
       version = 75,
       exportSchema = true
   )
   abstract class AppDatabase : RoomDatabase() {
       abstract fun bookDao(): BookDao
   }
   ```

2. **学习 DAO 操作**
   ```kotlin
   // 位置: app/src/main/java/io/legado/app/data/dao/BookDao.kt
   @Dao
   interface BookDao {
       @Query("SELECT * FROM books ORDER BY latestUploadTime DESC")
       fun observeAll(): Flow<List<Book>>
       
       @Insert(onConflict = OnConflictStrategy.REPLACE)
       suspend fun insert(book: Book)
       
       @Query("DELETE FROM books WHERE bookUrl = :bookUrl")
       suspend fun deleteByUrl(bookUrl: String)
   }
   ```

3. **理解 Flow 响应式数据**
   - 如何实现数据变化自动更新 UI
   - 协程与 Flow 的配合

#### 实践任务
1. 阅读 `data/entities/Book.kt`，理解 Entity 设计
2. 分析 `BookDao.kt` 中的查询方法

---

### 阶段五：深入 Service Layer（2-3天）

#### 目标
掌握后台服务的实现方式

#### 关键文件

| 文件 | 说明 |
|------|------|
| `service/CacheBookService.kt` | 书籍缓存服务 |
| `service/DownloadService.kt` | 下载服务 |

#### 学习任务

1. **学习服务创建**
   - Service 的生命周期
   - 前台服务与后台服务区别

2. **理解服务与数据层交互**
   - Service 如何调用 DAO
   - Service 如何通知 UI 更新

#### 实践任务
阅读 `CacheBookService.kt`，分析其工作流程

---

### 阶段六：综合实践（3-4天）

#### 目标
综合运用各层知识，实现一个简单功能

#### 实践任务

**任务：添加一个新的书籍分组功能**

1. **Data Layer**
   - 在 `entities` 中添加 `BookGroup` 实体（已存在，可参考）
   - 在 `dao` 中添加 `BookGroupDao`
   - 在 `AppDatabase` 中注册

2. **Model Layer**
   - 在 `model` 中添加业务处理逻辑

3. **UI Layer**
   - 创建 Fragment 展示分组列表
   - 实现添加/删除分组功能

#### 验证标准
- 能说出新增功能的数据流
- 各层代码符合分层原则

---

## 四、关键概念总结

### 4.1 为什么要分层

| 优势 | 说明 |
|------|------|
| 职责清晰 | 每层只关注自己的职责 |
| 易于测试 | 可以单独测试每一层 |
| 易于维护 | 修改某一层不影响其他层 |
| 复用性强 | 业务逻辑可以在多处复用 |

### 4.2 分层原则

1. **单向依赖**：外层依赖内层，内层不知道外层
2. **职责边界**：每层有明确的职责，不混淆
3. **数据传递**：通过数据类或接口传递，不直接操作

### 4.3 Legado 项目的特点

1. **未使用 Hilt/Dagger**：采用手动依赖注入
2. **Base 类封装**：减少重复代码
3. **大量使用协程**：异步操作统一管理
4. **Flow 响应式**：数据变化自动通知 UI

---

## 五、参考资源

### 5.1 关键文件索引

| 层级 | 目录 | 核心文件 |
|------|------|----------|
| UI | `ui/` | BaseActivity, BaseFragment, MainActivity |
| Model | `model/` | ReadBook, AnalyzeRule, SearchModel |
| Data | `data/` | AppDatabase, BookDao, Book |
| Service | `service/` | CacheBookService, DownloadService |

### 5.2 相关知识点

学习完本章节后，建议继续学习：

1. **Base 类封装思想** - 知识点 3
2. **MVVM 架构模式** - 知识点 1
3. **Room 数据库** - 知识点 6-10
4. **Kotlin 协程** - 知识点 26

---

## 六、自测题目

### 题目 1
Legado 项目分为哪几层？每层的职责是什么？

### 题目 2
层的依赖方向是什么？为什么这样设计？

### 题目 3
在 `model/analyzeRule/` 目录下有哪些规则解析方式？

### 题目 4
Data 层的 Entity、DAO、Database 之间的关系是什么？

### 题目 5
Service 层通常处理什么类型的任务？

---

> 文档生成时间：2026年
> 基于 Legado 项目源码
