# MVVM 架构模式 - 详细学习计划

> 本学习计划针对 1-3 年经验的 Android 开发工程师，通过学习 Legado 项目中 MVVM 架构的实际应用，掌握这一核心架构模式。

## 学习目标

1. **理解 MVVM 架构的核心思想** - 数据驱动、职责分离
2. **掌握 ViewModel 的使用** - 生命周期感知、状态管理
3. **掌握 LiveData 的使用** - 观察者模式、生命周期感知
4. **掌握 Lifecycle 的使用** - 生命周期感知组件
5. **能够独立实现 MVVM 架构** - 从 0 到 1 的完整实践

---

## 知识图谱

```
┌─────────────────────────────────────────────────────────────────┐
│                         MVVM 架构                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│    ┌──────────┐     ┌──────────────┐     ┌──────────────┐      │
│    │   View    │────▶│  ViewModel    │◀────│    Model     │      │
│    │  (UI层)   │◀────│  (业务逻辑)   │────▶│   (数据层)    │      │
│    └──────────┘     └──────────────┘     └──────────────┘      │
│         │                   │                    │               │
│         ▼                   ▼                    ▼               │
│   Activity/Fragment   LiveData/State    Repository/DAO          │
│   ViewBinding         Coroutines        Room Database            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 第一阶段：基础概念理解（Day 1-2）

### 1.1 什么是 MVVM？

**核心概念**：
- **M**odel：数据模型，负责数据处理和业务逻辑
- **V**iew：视图层，负责 UI 展示和用户交互
- **V**iewModel：视图模型，负责连接 View 和 Model，管理 UI 相关数据

**MVVM 相比 MVC 的优势**：
1. 降低耦合：View 和 Model 通过 ViewModel 解耦
2. 复用性：ViewModel 可被多个 View 复用
3. 独立开发：View 和 ViewModel 可以并行开发
4. 更好的测试性：ViewModel 可单独测试

**学习资料**：
- Android 官方文档：https://developer.android.com/topic/libraries/architecture

---

### 1.2 项目中的 MVVM 结构

**关键文件位置**：

| 文件 | 路径 | 作用 |
|------|------|------|
| BaseViewModel | `app/src/main/java/io/legado/app/base/BaseViewModel.kt` | ViewModel 基类 |
| BaseActivity | `app/src/main/java/io/legado/app/base/BaseActivity.kt` | Activity 基类 |
| BaseFragment | `app/src/main/java/io/legado/app/base/BaseFragment.kt` | Fragment 基类 |
| BookshelfViewModel | `app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfViewModel.kt` | 业务 ViewModel 示例 |
| BaseBookshelfFragment | `app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt` | 业务 Fragment 示例 |

---

## 第二阶段：ViewModel 深入学习（Day 3-4）

### 2.1 ViewModel 核心概念

**ViewModel 的特点**：
1. 生命周期感知：在配置变更（如屏幕旋转）时不会销毁
2. 数据存储：存储 UI 相关数据，不会因配置变更丢失
3. 与 Repository 配合：ViewModel 从 Repository 获取数据

### 2.2 项目中 BaseViewModel 分析

**文件**：`app/src/main/java/io/legado/app/base/BaseViewModel.kt`

**核心代码解析**：

```kotlin
/**
 * BaseViewModel 的核心价值：
 * 1. 提供安全的应用上下文访问（避免内存泄漏）
 * 2. 封装协程任务调度方法，简化后台任务的启动和生命周期管理
 * 3. 结合 viewModelScope 确保协程随 ViewModel 销毁而取消
 */
open class BaseViewModel(application: Application) : AndroidViewModel(application) {

    // 1. 安全的上下文访问 - 使用 lazy 避免内存泄漏
    val context: Context by lazy { this.getApplication<App>() }

    // 2. execute 方法 - 封装协程任务调度
    fun <T> execute(
        scope: CoroutineScope = viewModelScope,          // 使用 viewModelScope 自动管理生命周期
        context: CoroutineContext = Dispatchers.IO,     // 后台线程执行
        start: CoroutineStart = CoroutineStart.DEFAULT, // 立即执行
        executeContext: CoroutineContext = Dispatchers.Main, // 主线程回调
        semaphore: Semaphore? = null,                   // 并发控制
        block: suspend CoroutineScope.() -> T
    ): Coroutine<T> {
        return Coroutine.async(scope, context, start, executeContext, semaphore, block)
    }

    // 3. executeLazy 方法 - 延迟执行
    fun <T> executeLazy(...): Coroutine<T> {
        return Coroutine.async(scope, context, CoroutineStart.LAZY, ...)
    }

    // 4. submit 方法 - 提交任务并等待结果
    fun <R> submit(...): Coroutine<R> {
        return Coroutine.async(scope, context) { block().await() }
    }
}
```

**学习要点**：
问题 1：ViewModel 中 viewModelScope 的作用是什么？
答案：
viewModelScope 是绑定到 ViewModel 生命周期的协程作用域，核心价值是让开发者在 ViewModel 中安全地启动协程，无需手动管理协程生命周期。当 ViewModel 销毁时，该作用域下的所有协程会被自动取消，能彻底避免协程泄漏、空指针或操作已销毁 UI 的问题。
问题 2：协程的 Dispatchers.IO 和 Dispatchers.Main 有什么区别？
答案：
Dispatchers.Main：绑定主线程，仅用于执行 UI 相关操作，绝对不能用来执行耗时的 IO 操作（会导致主线程阻塞、界面卡顿）；
Dispatchers.IO：运行在后台线程池，专门用于处理耗时的 IO 操作（如网络请求、文件读写、数据库操作等），不能直接在该调度器下更新 UI。
问题 3：CoroutineStart.LAZY（懒加载模式）的作用是什么？
答案：
CoroutineStart.LAZY 是协程的启动模式之一，其核心作用是：创建协程后不会让协程立即执行，只有当主动调用协程的 start() 方法时，该协程才会开始执行。

### 2.3 实战：阅读 BookshelfViewModel

**文件**：`app/src/main/java/io/legado/app/ui/main/bookshelf/BookshelfViewModel.kt`

**代码结构分析**：

```kotlin
class BookshelfViewModel(application: Application) : BaseViewModel(application) {

    // 1. LiveData - 用于观察 UI 状态
    val addBookProgressLiveData = MutableLiveData(-1)

    // 2. Job 引用 - 用于取消任务
    var addBookJob: Coroutine<*>? = null

    // 3. 业务方法 - 添加书籍
    fun addBookByUrl(bookUrls: String) {
        addBookJob = execute {  // 使用基类方法启动协程
            // 后台任务：网络请求、数据库操作
            // ... 业务逻辑
        }.onSuccess { ... }    // 成功回调（主线程）
         .onError { ... }      // 错误回调
         .onFinally { ... }    // 最终回调
    }

    // 4. 导出书架
    fun exportBookshelf(books: List<Book>?, success: (file: File) -> Unit) {
        execute { ... }
    }

    // 5. 导入书架
    fun importBookshelf(str: String, groupId: Long) {
        execute { ... }
    }
}
```

**练习任务**：
问题：MutableLiveData 的作用是什么？
答案：
MutableLiveData 是 Android Jetpack 中 LiveData 的可变子类，是一种具备生命周期感知能力的可观察数据持有者，核心作用是在 Android 组件（如 Activity/Fragment/ViewModel）之间安全、高效地管理和传递数据，具体体现在以下三点：
1. 核心特性与作用
   生命周期感知：它能感知 UI 组件（Activity/Fragment）的生命周期状态，仅向处于「活跃状态」（如前台可见、未销毁）的组件发送数据更新，避免向已销毁的组件推送数据，彻底杜绝空指针、内存泄漏或操作已销毁 UI 的问题。
   支持数据修改：作为「可变」的 LiveData，它提供了 setValue()（主线程调用）和 postValue()（子线程调用）方法来主动更新数据；而父类 LiveData 仅能读取数据（只有 getValue()），无法直接修改。
   解耦组件通信：最常用在 ViewModel 与 UI 组件之间的通信 ——ViewModel 持有 MutableLiveData 存储数据，UI 组件（Activity/Fragment）观察该数据，当数据变化时 UI 自动收到通知并更新，无需手动监听生命周期或传递数据。
1. 分析 `addBookByUrl` 方法的完整流程
答案见文档：`app/src/main/docs/01-MVVM学习计划/addBookByUrl执行流程.md`
---
2. 理解 `execute` 方法链式调用的实现原理
答案见文档：`app/src/main/docs/01-MVVM学习计划/02-execute方法链式调用原理.md`
---
3. 思考为什么使用 `postValue` 而不是 `value`
答案见文档：`app/src/main/docs/01-MVVM学习计划/03-postValue与setValue的区别.md`
---

## 第三阶段：LiveData 深入学习（Day 5-6）

### 3.1 LiveData 核心概念

**LiveData 的特点**：
1. 生命周期感知：仅在活跃状态下通知观察者
2. 数据更新：支持 `setValue()--只能在主线程调用` 和 `postValue()--线程安全`
3. 观察者模式：支持 `observe()` 和 `observeForever()`

### 3.2 项目中 LiveData 使用分析

**观察者模式示例**（在 Activity 中）：

**文件**：`app/src/main/java/io/legado/app/ui/main/MainActivity.kt` 第 352 行

```kotlin
viewModel.onUpBooksLiveData.observe(this) { books ->
    // UI 更新逻辑
}
```

**文件**：`app/src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt` 第 137 行

```kotlin
viewModel.addBookProgressLiveData.observe(this) { count ->
    if (count == -1) {
        // 隐藏加载对话框
        waitDialog.dismiss()
    } else {
        // 显示加载进度
        waitDialog.show()
    }
}
```

### 3.3 LiveData 的最佳实践

| 场景 | 推荐写法 | 说明 |
|------|---------|------|
| 主线程更新 | `liveData.value = data` | 直接设置值 |
| 后台线程更新 | `liveData.postValue(data)` | 线程安全 |
| 只观察一次 | `liveData.observeOnce()` | 只回调一次 |
| 无生命周期 | `liveData.observeForever()` | 需要手动移除 |

### 3.4 LiveData 变换（Transformations）

**常用变换**：
- `map()`：转换数据类型
- `switchMap()`：切换数据源
- `MediatorLiveData`：合并多个数据源

---

## 第四阶段：Lifecycle 组件（Day 7）

### 4.1 Lifecycle 核心概念

**Lifecycle 的作用**：
- 感知 Activity/Fragment 生命周期
- 在合适时机执行操作
- 避免内存泄漏

### 4.2 项目中 Lifecycle 使用

**文件**：`app/src/main/java/io/legado/app/base/BaseActivity.kt`

```kotlin
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    // 1. 在 onCreate 中初始化
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        onActivityCreated(savedInstanceState)
    }

    // 2. 多窗口模式处理
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        // 处理多窗口变化
    }

    // 3. 事件总线观察
    open fun observeLiveBus() {
        // 子类重写，注册 LiveEventBus 观察
    }
}
```

### 4.3 项目中 LiveEventBus 使用

**文件**：`app/src/main/java/io/legado/app/help/livebus/LiveEventBus.kt`

**观察者注册**：

```kotlin
// 在 Fragment 中
override fun observeLiveBus() {
    observeEvent<String>(EventBus.REFRESH_BOOK) { bookUrl ->
        // 刷新书籍
    }
}
```

---

## 第五阶段：ViewBinding（Day 8）

### 5.1 ViewBinding 简介

**ViewBinding vs findViewById**：
- 类型安全：编译时检查
- 空安全：不再返回 null
- 可读性：直接访问 View

### 5.2 项目中 ViewBinding 使用

**BaseActivity 中的使用**：

```kotlin
abstract class BaseActivity<VB : ViewBinding>(...) : AppCompatActivity() {

    // 子类必须实现 binding 属性
    protected abstract val binding: VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)  // 直接使用 binding 根节点
    }
}
```

**具体使用示例**：

```kotlin
class MainActivity : BaseActivity<ActivityMainBinding>() {

    override val binding: ActivityMainBinding
        get() = ActivityMainBinding.inflate(layoutInflater)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // 直接使用 binding 访问 Views
        binding.tvTitle.text = "标题"
        binding.btnSubmit.setOnClickListener { ... }
    }
}
```

---

## 第六阶段：综合实战（Day 9-10）

### 6.1 实现一个完整的 MVVM 页面

**任务**：仿照 Bookshelf 模块，实现一个"任务列表"页面

**步骤**：

1. **创建数据模型**
```kotlin
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey
    val id: Long,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

2. **创建 DAO**
```kotlin
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Delete
    suspend fun delete(task: Task)
}
```

3. **创建 ViewModel**
```kotlin
class TaskViewModel(application: Application) : BaseViewModel(application) {

    val tasksLiveData = MutableLiveData<List<Task>>()

    fun loadTasks() {
        execute {
            appDb.taskDao.observeAll().collect { tasks ->
                tasksLiveData.postValue(tasks)
            }
        }
    }

    fun addTask(title: String) {
        execute {
            val task = Task(
                id = System.currentTimeMillis(),
                title = title
            )
            appDb.taskDao.insert(task)
        }
    }
}
```

4. **创建 Fragment**
```kotlin
class TaskFragment : VMBaseFragment<TaskViewModel>(R.layout.fragment_task) {

    override val viewModel by viewModels<TaskViewModel>()

    private val adapter = TaskAdapter()

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.adapter = adapter

        // 观察 LiveData
        viewModel.tasksLiveData.observe(viewLifecycleOwner) { tasks ->
            adapter.submitList(tasks)
        }

        // 加载数据
        viewModel.loadTasks()
    }
}
```

---

## 学习检查清单

### 基础概念（Day 1-2）
- [ ] 理解 MVVM 架构的核心思想
- [ ] 理解 Model、View、ViewModel 的职责
- [ ] 了解 MVVM 相比 MVC 的优势

### ViewModel（Day 3-4）
- [ ] 理解 ViewModel 的生命周期
- [ ] 掌握 viewModelScope 的使用
- [ ] 能够使用 BaseViewModel 的 execute 方法
- [ ] 理解协程的线程切换

### LiveData（Day 5-6）
- [ ] 理解 LiveData 的观察者模式
- [ ] 掌握 setValue 和 postValue 的区别
- [ ] 能够正确观察 LiveData 数据变化
- [ ] 理解 LiveData 的生命周期感知

### Lifecycle（Day 7）
- [ ] 理解 Lifecycle 的生命周期状态
- [ ] 能够使用 lifecycleScope
- [ ] 理解 LiveEventBus 的使用场景

### ViewBinding（Day 8）
- [ ] 理解 ViewBinding 的优势
- [ ] 能够在 Activity 中使用 ViewBinding
- [ ] 能够在 Fragment 中使用 ViewBinding

### 综合实战（Day 9-10）
- [ ] 能够从 0 实现一个 MVVM 页面
- [ ] 理解数据层到 UI 层的完整流程
- [ ] 能够处理异常情况和边界条件

---

## 扩展学习资源

### 官方文档
1. **Android Architecture Components**
   - https://developer.android.com/topic/libraries/architecture

2. **Guide to App Architecture**
   - https://developer.android.com/topic/libraries/architecture/guide

3. **ViewModel Documentation**
   - https://developer.android.com/topic/libraries/architecture/viewmodel

4. **LiveData Documentation**
   - https://developer.android.com/topic/libraries/architecture/livedata

### 项目内部资源
| 资源 | 路径 | 说明 |
|------|------|------|
| BaseViewModel | `base/BaseViewModel.kt` | ViewModel 基类 |
| Coroutine 封装 | `help/coroutine/Coroutine.kt` | 协程封装 |
| LiveEventBus | `help/livebus/LiveEventBus.kt` | 事件总线 |
| 示例 ViewModel | `ui/main/bookshelf/BookshelfViewModel.kt` | 业务示例 |

---

## 常见问题与解答

### Q1: ViewModel 和 Activity 的生命周期有什么区别？

**答**：ViewModel 的生命周期比 Activity/Fragment 更长。ViewModel 会在配置变更（如屏幕旋转）时保留，而 Activity 会销毁重建。

### Q2: 什么时候使用 MutableLiveData，什么时候使用 LiveData？

**答**：
- `MutableLiveData`：在 ViewModel 中使用，允许修改值
- `LiveData`：在观察者中使用，只读

### Q3: execute 方法中的 onSuccess、onError 在哪个线程？

**答**：
- `execute` 的 `block` 在 `context` 指定的线程执行（默认 IO 线程）
- `onSuccess`、`onError`、`onFinally` 在 `executeContext` 指定的线程执行（默认主线程）

### Q4: 如何避免 LiveData 内存泄漏？

**答**：
- 使用 `observe()` 方法而非 `observeForever()`
- ViewModel 自动处理生命周期，不需要手动移除

---

## 总结

通过本学习计划，你应该能够：

1. **理解 MVVM 架构**：掌握数据驱动、职责分离的核心思想
2. **熟练使用 ViewModel**：能够创建和管理 ViewModel，理解生命周期
3. **熟练使用 LiveData**：掌握观察者模式，正确处理数据变化
4. **熟练使用 Lifecycle**：理解生命周期感知组件
5. **熟练使用 ViewBinding**：类型安全地访问 Views
6. **综合实践**：能够独立实现 MVVM 架构的页面

**建议**：在学习过程中，多阅读项目中的实际代码，尝试修改和扩展功能加深理解。

---

> 学习计划版本：1.0
> 创建日期：2025年
> 参考项目：https://github.com/gedoor/legado
