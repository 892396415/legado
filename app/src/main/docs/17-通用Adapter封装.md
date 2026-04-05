# 通用 Adapter 封装详解

> 基于 Legado 项目的 RecyclerAdapter 封装，深入解析通用 Adapter 的设计、ViewBinding 集成、DiffUtil 优化、动画系统等高级技术。

## 目录

- [一、基础概述](#一基础概述)
- [二、核心架构设计](#二核心架构设计)
- [三、RecyclerAdapter 详解](#三recycleradapter-详解)
- [四、DiffRecyclerAdapter 详解](#四diffrecycleradapter-详解)
- [五、ViewBinding 集成](#五viewbinding-集成)
- [六、DiffUtil 优化](#六diffutil-优化)
- [七、Item 动画系统](#七item-动画系统)
- [八、完整实战示例](#八完整实战示例)
- [九、最佳实践](#九最佳实践)

---

## 一、基础概述

### 1.1 为什么要封装通用 Adapter

| 传统写法 | 通用 Adapter |
|---------|-------------|
| 每个列表都要新建 Adapter | 一个 Adapter 通用所有列表 |
| 重复编写 ViewHolder | 自动处理 ViewHolder |
| 手动管理点击事件 | 统一封装点击事件 |
| 全量刷新性能差 | DiffUtil 局部刷新 |
| 代码冗余 | 精简代码，专注业务 |

### 1.2 封装目标

1. **简化使用**：只需实现 `convert()` 方法
2. **类型安全**：泛型 + ViewBinding，编译期检查
3. **性能优化**：内置 DiffUtil，自动局部刷新
4. **功能丰富**：支持 Header/Footer、动画、拖拽排序
5. **易于扩展**：通过抽象方法扩展功能

### 1.3 核心组件

```
base/adapter/
├── RecyclerAdapter.kt          # 基础适配器（486行）
├── DiffRecyclerAdapter.kt      # Diff版本适配器（233行）
├── ItemViewHolder.kt           # ViewHolder封装（10行）
├── ItemAnimation.kt            # 动画配置（85行）
└── animations/
    ├── BaseAnimation.kt        # 动画接口
    ├── AlphaInAnimation.kt     # 淡入动画
    ├── ScaleInAnimation.kt     # 缩放动画
    ├── SlideInBottomAnimation.kt  # 底部滑入
    ├── SlideInLeftAnimation.kt    # 左侧滑入
    └── SlideInRightAnimation.kt   # 右侧滑入
```

---

## 二、核心架构设计

### 2.1 类图关系

```
┌─────────────────────────────────────────────────────────────┐
│                     RecyclerAdapter<ITEM, VB>                │
│                    (抽象基类，486行)                          │
├─────────────────────────────────────────────────────────────┤
│  - items: MutableList<ITEM>                                 │
│  - headerItems: SparseArray                                 │
│  - footerItems: SparseArray                                 │
│  - itemAnimation: ItemAnimation                             │
├─────────────────────────────────────────────────────────────┤
│  + abstract convert(holder, binding, item, payloads)        │
│  + abstract getViewBinding(parent): VB                      │
│  + abstract registerListener(holder, binding)               │
│  + setItems(items)                                          │
│  + setItems(items, itemCallback, skipDiff)                  │
│  + addItem(item) / removeItem(position)                     │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
┌──────────────────────────┐    ┌──────────────────────────┐
│   BookSourceAdapter       │    │   DiffRecyclerAdapter    │
│   (业务实现)               │    │   (DiffUtil版本)          │
└──────────────────────────┘    └──────────────────────────┘
```

### 2.2 泛型设计

```kotlin
// ITEM: 数据类型
// VB: ViewBinding 类型
abstract class RecyclerAdapter<ITEM, VB : ViewBinding>(
    protected val context: Context
) : RecyclerView.Adapter<ItemViewHolder>()

// 使用示例
class BookSourceAdapter(
    context: Context, ...
) : RecyclerAdapter<BookSourcePart, ItemBookSourceBinding>(context)
```

**优势**：
- 编译期类型检查
- 自动推断 ViewBinding 类型
- 无需强制类型转换

---

## 三、RecyclerAdapter 详解

### 3.1 完整代码结构（486行）

```kotlin
abstract class RecyclerAdapter<ITEM, VB : ViewBinding>(
    protected val context: Context
) : RecyclerView.Adapter<ItemViewHolder>() {

    val inflater: LayoutInflater = LayoutInflater.from(context)
    
    // 数据存储
    private val items: MutableList<ITEM> = mutableListOf()
    private val headerItems: SparseArray<(parent: ViewGroup) -> ViewBinding> by lazy { SparseArray() }
    private val footerItems: SparseArray<(parent: ViewGroup) -> ViewBinding> by lazy { SparseArray() }
    
    // 事件监听
    private var itemClickListener: ((holder: ItemViewHolder, item: ITEM) -> Unit)? = null
    private var itemLongClickListener: ((holder: ItemViewHolder, item: ITEM) -> Boolean)? = null
    
    // 动画
    var itemAnimation: ItemAnimation? = null
    
    // DiffUtil 任务
    private var diffJob: Coroutine<*>? = null
    
    // 常量
    companion object {
        private const val TYPE_HEADER_VIEW = Int.MIN_VALUE
        const val TYPE_FOOTER_VIEW = Int.MAX_VALUE - 999
    }
}
```

### 3.2 核心方法解析

#### 3.2.1 抽象方法（子类必须实现）

```kotlin
/**
 * 绑定数据到视图
 * 这是最核心的方法，所有子类必须实现
 */
abstract fun convert(
    holder: ItemViewHolder,
    binding: VB,
    item: ITEM,
    payloads: MutableList<Any>
)

/**
 * 创建 ViewBinding
 * 子类返回对应的 ViewBinding 实例
 */
abstract fun getViewBinding(parent: ViewGroup): VB

/**
 * 注册监听器
 * 设置点击事件、长按事件等
 */
abstract fun registerListener(holder: ItemViewHolder, binding: VB)
```

#### 3.2.2 数据操作方法

```kotlin
// 设置数据（全量刷新）
fun setItems(items: List<ITEM>?) {
    this.items.clear()
    items?.let { this.items.addAll(it) }
    notifyDataSetChanged()
}

// 设置数据（使用 DiffUtil）
fun setItems(
    items: List<ITEM>?,
    itemCallback: DiffUtil.ItemCallback<ITEM>,
    skipDiff: Boolean = false
) {
    // 计算差异
    val diffResult = DiffUtil.calculateDiff(callback, itemsSize < 2000)
    // 应用到列表
    diffResult.dispatchUpdatesTo(this)
}

// 添加单条数据
fun addItem(item: ITEM) {
    val oldSize = getActualItemCount()
    if (this.items.add(item)) {
        notifyItemInserted(oldSize + getHeaderCount())
    }
}

// 删除数据
fun removeItem(position: Int) {
    if (this.items.removeAt(position) != null) {
        notifyItemRemoved(position + getHeaderCount())
    }
}

// 交换位置（拖拽排序）
fun swapItem(oldPosition: Int, newPosition: Int) {
    Collections.swap(this.items, srcPosition, targetPosition)
    notifyItemMoved(srcPosition, targetPosition)
}

// 局部更新（使用 payload）
fun updateItem(position: Int, payload: Any) {
    notifyItemChanged(position + getHeaderCount(), payload)
}
```

### 3.3 ViewHolder 创建与绑定

```kotlin
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when {
    // Header
    viewType < TYPE_HEADER_VIEW + getHeaderCount() -> {
        ItemViewHolder(headerItems.get(viewType).invoke(parent))
    }
    // Footer
    viewType >= TYPE_FOOTER_VIEW -> {
        ItemViewHolder(footerItems.get(viewType).invoke(parent))
    }
    // 普通 Item
    else -> {
        ItemViewHolder(getViewBinding(parent))
    }
}

override fun onBindViewHolder(
    holder: ItemViewHolder,
    position: Int,
    payloads: MutableList<Any>
) {
    if (!isHeader(holder.layoutPosition) && !isFooter(holder.layoutPosition)) {
        // 注册监听器
        registerListener(holder, holder.binding as VB)
        registerItemListener(holder)
        
        // 绑定数据
        getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
            convert(holder, holder.binding, item, payloads)
        }
    }
}
```

### 3.4 Header 和 Footer 支持

```kotlin
// 添加 Header
fun addHeaderView(header: ((parent: ViewGroup) -> ViewBinding)) {
    val index = headerItems.size()
    headerItems.put(TYPE_HEADER_VIEW + headerItems.size(), header)
    notifyItemInserted(index)
}

// 添加 Footer
fun addFooterView(footer: ((parent: ViewGroup) -> ViewBinding)) {
    val index = getActualItemCount() + footerItems.size()
    footerItems.put(TYPE_FOOTER_VIEW + footerItems.size(), footer)
    notifyItemInserted(index)
}

// 判断位置
private fun isHeader(position: Int) = position < getHeaderCount()
private fun isFooter(position: Int) = position >= getActualItemCount() + getHeaderCount()
```

### 3.5 点击事件封装

```kotlin
// 设置点击监听
fun setOnItemClickListener(
    listener: (holder: ItemViewHolder, item: ITEM) -> Unit
) {
    itemClickListener = listener
}

// 注册到 ViewHolder
private fun registerItemListener(holder: ItemViewHolder) {
    // 点击事件
    holder.itemView.setOnClickListener {
        getItemByLayoutPosition(holder.layoutPosition)?.let {
            itemClickListener?.invoke(holder, it)
        }
    }
    
    // 长按事件
    holder.itemView.onLongClick {
        getItemByLayoutPosition(holder.layoutPosition)?.let {
            itemLongClickListener?.invoke(holder, it)
        }
    }
}
```

---

## 四、DiffRecyclerAdapter 详解

### 4.1 为什么需要 Diff 版本

`RecyclerAdapter` 虽然支持 DiffUtil，但需要手动传入 callback。`DiffRecyclerAdapter` 更进一步，将 DiffUtil 集成到内部，使用更简洁。

### 4.2 核心实现（233行）

```kotlin
abstract class DiffRecyclerAdapter<ITEM, VB : ViewBinding>(
    protected val context: Context
) : RecyclerView.Adapter<ItemViewHolder>() {

    // 使用 AsyncListDiffer 自动管理差异
    private val asyncListDiffer: AsyncListDiffer<ITEM> by lazy {
        AsyncListDiffer(this, diffItemCallback).apply {
            addListListener { _, _ ->
                onCurrentListChanged()
                if (keepScrollPosition) {
                    // 恢复滚动位置
                    layoutManager?.onRestoreInstanceState(layoutState)
                    layoutState = null
                }
            }
        }
    }
    
    // 子类必须提供 DiffCallback
    abstract val diffItemCallback: DiffUtil.ItemCallback<ITEM>
    
    // 是否保持滚动位置
    open val keepScrollPosition = false
}
```

### 4.3 简化数据操作

```kotlin
// 设置数据（自动使用 DiffUtil）
fun setItems(items: List<ITEM>?) {
    if (keepScrollPosition) {
        layoutState = layoutManager?.onSaveInstanceState()
    }
    asyncListDiffer.submitList(items?.toMutableList())
}

// 获取数据
fun getItem(position: Int): ITEM? = asyncListDiffer.currentList.getOrNull(position)

fun getItems(): List<ITEM> = asyncListDiffer.currentList
```

**对比**：

| 操作 | RecyclerAdapter | DiffRecyclerAdapter |
|------|----------------|---------------------|
| 设置数据 | `setItems(list, callback)` | `setItems(list)` |
| DiffCallback | 每次调用传入 | 抽象属性，初始化一次 |
| 异步计算 | 手动管理协程 | AsyncListDiffer 自动处理 |

---

## 五、ViewBinding 集成

### 5.1 ViewBinding 优势

```kotlin
// 传统方式（需要 findViewById）
class OldViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val textView: TextView = view.findViewById(R.id.text_view)
    val imageView: ImageView = view.findViewById(R.id.image_view)
}

// ViewBinding 方式（类型安全）
class NewViewHolder(val binding: ItemBookSourceBinding) : 
    RecyclerView.ViewHolder(binding.root)
// 直接使用 binding.textView, binding.imageView
```

### 5.2 ItemViewHolder 封装

```kotlin
class ItemViewHolder(val binding: ViewBinding) : 
    RecyclerView.ViewHolder(binding.root)

// 使用
override fun convert(
    holder: ItemViewHolder,
    binding: ItemBookSourceBinding,  // 自动推断类型
    item: BookSourcePart,
    payloads: MutableList<Any>
) {
    binding.textView.text = item.name
    binding.imageView.setImageResource(item.icon)
}
```

### 5.3 子类实现示例

```kotlin
class BookSourceAdapter(context: Context) : 
    RecyclerAdapter<BookSourcePart, ItemBookSourceBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookSourceBinding {
        return ItemBookSourceBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookSourceBinding,
        item: BookSourcePart,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            // 直接访问视图
            textView.text = item.bookSourceName
            checkBox.isChecked = item.enabled
            
            // 局部刷新
            if (payloads.isNotEmpty()) {
                payloads.forEach { payload ->
                    when (payload) {
                        "enabled" -> checkBox.isChecked = item.enabled
                        "name" -> textView.text = item.bookSourceName
                    }
                }
            }
        }
    }
}
```

---

## 六、DiffUtil 优化

### 6.1 什么是 DiffUtil

DiffUtil 是 Android Support Library 提供的工具类，用于计算两个列表的差异，并输出最小更新操作集。

```
旧列表: [A, B, C, D]
新列表: [A, C, B, E]

DiffUtil 计算结果:
- B: 从位置 1 移动到位置 2
- C: 从位置 2 移动到位置 1
- D: 删除
- E: 在位置 3 插入

而不是: notifyDataSetChanged() （全量刷新）
```

### 6.2 ItemCallback 实现

```kotlin
val diffItemCallback = object : DiffUtil.ItemCallback<BookSourcePart>() {
    
    /**
     * 判断是否是同一个 Item（通常用 ID）
     */
    override fun areItemsTheSame(
        oldItem: BookSourcePart, 
        newItem: BookSourcePart
    ): Boolean {
        return oldItem.bookSourceUrl == newItem.bookSourceUrl
    }
    
    /**
     * 判断内容是否相同（用于决定是否需要刷新）
     */
    override fun areContentsTheSame(
        oldItem: BookSourcePart, 
        newItem: BookSourcePart
    ): Boolean {
        return oldItem.bookSourceName == newItem.bookSourceName
                && oldItem.bookSourceGroup == newItem.bookSourceGroup
                && oldItem.enabled == newItem.enabled
    }
    
    /**
     * 局部刷新的 Payload
     * 返回非 null 时，只刷新变化的字段
     */
    override fun getChangePayload(
        oldItem: BookSourcePart, 
        newItem: BookSourcePart
    ): Any? {
        val payload = Bundle()
        if (oldItem.bookSourceName != newItem.bookSourceName) {
            payload.putBoolean("upName", true)
        }
        if (oldItem.enabled != newItem.enabled) {
            payload.putBoolean("enabled", newItem.enabled)
        }
        return if (payload.isEmpty) null else payload
    }
}
```

### 6.3 局部刷新使用

```kotlin
override fun convert(
    holder: ItemViewHolder,
    binding: ItemBookSourceBinding,
    item: BookSourcePart,
    payloads: MutableList<Any>
) {
    if (payloads.isEmpty()) {
        // 全量刷新
        binding.textView.text = item.bookSourceName
        binding.checkBox.isChecked = item.enabled
    } else {
        // 局部刷新
        payloads.forEach { payload ->
            val bundle = payload as Bundle
            bundle.keySet().forEach { key ->
                when (key) {
                    "enabled" -> binding.checkBox.isChecked = bundle.getBoolean(key)
                    "upName" -> binding.textView.text = item.bookSourceName
                }
            }
        }
    }
}
```

### 6.4 性能对比

| 方式 | 刷新范围 | 性能 | 动画 |
|------|---------|------|------|
| notifyDataSetChanged() | 全部 | 差 | 无 |
| notifyItemChanged() | 单条 | 好 | 有 |
| DiffUtil | 最小集 | 最优 | 自动 |

---

## 七、Item 动画系统

### 7.1 动画配置类

```kotlin
class ItemAnimation private constructor() {
    var itemAnimEnabled = false        // 是否启用
    var itemAnimFirstOnly = true       // 只播放第一次
    var itemAnimation: BaseAnimation? = null
    var itemAnimInterpolator: Interpolator = LinearInterpolator()
    var itemAnimDuration: Long = 300L
    var itemAnimStartPosition: Int = -1
    
    // Builder 模式
    fun enabled(enabled: Boolean) = apply { itemAnimEnabled = enabled }
    fun duration(duration: Long) = apply { itemAnimDuration = duration }
    fun animation(animationType: Int) = apply {
        itemAnimation = when (animationType) {
            FADE_IN -> AlphaInAnimation()
            SCALE_IN -> ScaleInAnimation()
            BOTTOM_SLIDE_IN -> SlideInBottomAnimation()
            // ...
        }
    }
    
    companion object {
        const val NONE = 0x00000000
        const val FADE_IN = 0x00000001
        const val SCALE_IN = 0x00000002
        const val BOTTOM_SLIDE_IN = 0x00000003
        
        fun create() = ItemAnimation()
    }
}
```

### 7.2 动画接口

```kotlin
interface BaseAnimation {
    fun getAnimators(view: View): Array<Animator>
}

// 淡入动画
class AlphaInAnimation(private val mFrom: Float = 0f) : BaseAnimation {
    override fun getAnimators(view: View): Array<Animator> =
        arrayOf(ObjectAnimator.ofFloat(view, "alpha", mFrom, 1f))
}

// 缩放动画
class ScaleInAnimation(private val mFrom: Float = 0f) : BaseAnimation {
    override fun getAnimators(view: View): Array<Animator> =
        arrayOf(
            ObjectAnimator.ofFloat(view, "scaleX", mFrom, 1f),
            ObjectAnimator.ofFloat(view, "scaleY", mFrom, 1f)
        )
}
```

### 7.3 使用动画

```kotlin
// 方式 1：代码设置
adapter.itemAnimation = ItemAnimation.create()
    .enabled(true)
    .duration(300)
    .animation(ItemAnimation.SCALE_IN)
    .firstOnly(true)

// 方式 2：自定义动画
adapter.itemAnimation = ItemAnimation.create()
    .enabled(true)
    .animation(animation = CustomAnimation())
```

### 7.4 动画触发

```kotlin
override fun onViewAttachedToWindow(holder: ItemViewHolder) {
    super.onViewAttachedToWindow(holder)
    if (!isHeader(holder.layoutPosition) && !isFooter(holder.layoutPosition)) {
        addAnimation(holder)
    }
}

private fun addAnimation(holder: ItemViewHolder) {
    itemAnimation?.let {
        if (it.itemAnimEnabled) {
            // 只播放第一次，或每次都有动画
            if (!it.itemAnimFirstOnly || 
                holder.layoutPosition > it.itemAnimStartPosition) {
                startAnimation(holder, it)
                it.itemAnimStartPosition = holder.layoutPosition
            }
        }
    }
}
```

---

## 八、完整实战示例

### 8.1 BookSourceAdapter（403行完整代码）

```kotlin
class BookSourceAdapter(
    context: Context,
    private val callBack: CallBack,
    private val recyclerView: RecyclerView
) : RecyclerAdapter<BookSourcePart, ItemBookSourceBinding>(context),
    ItemTouchCallback.Callback {

    private val selected = linkedSetOf<BookSourcePart>()
    
    // DiffCallback 定义
    val diffItemCallback = object : DiffUtil.ItemCallback<BookSourcePart>() {
        override fun areItemsTheSame(
            oldItem: BookSourcePart, 
            newItem: BookSourcePart
        ): Boolean {
            return oldItem.bookSourceUrl == newItem.bookSourceUrl
        }
        
        override fun areContentsTheSame(
            oldItem: BookSourcePart, 
            newItem: BookSourcePart
        ): Boolean {
            return oldItem.bookSourceName == newItem.bookSourceName
                    && oldItem.bookSourceGroup == newItem.bookSourceGroup
                    && oldItem.enabled == newItem.enabled
        }
        
        override fun getChangePayload(
            oldItem: BookSourcePart, 
            newItem: BookSourcePart
        ): Any? {
            val payload = Bundle()
            if (oldItem.bookSourceName != newItem.bookSourceName ||
                oldItem.bookSourceGroup != newItem.bookSourceGroup
            ) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.enabled != newItem.enabled) {
                payload.putBoolean("enabled", newItem.enabled)
            }
            return if (payload.isEmpty) null else payload
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemBookSourceBinding {
        return ItemBookSourceBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookSourceBinding,
        item: BookSourcePart,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isEmpty()) {
                // 全量刷新
                root.setBackgroundColor(ColorUtils.withAlpha(context.backgroundColor, 0.5f))
                cbBookSource.text = item.getDisPlayNameGroup()
                swtEnabled.isChecked = item.enabled
                cbBookSource.isChecked = selected.contains(item)
            } else {
                // 局部刷新
                payloads.forEach { payload ->
                    val bundle = payload as Bundle
                    bundle.keySet().forEach { key ->
                        when (key) {
                            "enabled" -> swtEnabled.isChecked = bundle.getBoolean(key)
                            "upName" -> cbBookSource.text = item.getDisPlayNameGroup()
                            "selected" -> cbBookSource.isChecked = selected.contains(item)
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookSourceBinding) {
        binding.apply {
            // 启用开关
            swtEnabled.setOnCheckedChangeListener { view, checked ->
                getItem(holder.layoutPosition)?.let {
                    if (view.isPressed) {
                        it.enabled = checked
                        callBack.enable(checked, it)
                    }
                }
            }
            
            // 选择框
            cbBookSource.setOnCheckedChangeListener { view, checked ->
                getItem(holder.layoutPosition)?.let {
                    if (view.isPressed) {
                        if (checked) selected.add(it) else selected.remove(it)
                        callBack.upCountView()
                    }
                }
            }
            
            // 编辑按钮
            ivEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.edit(it)
                }
            }
        }
    }
    
    // 全选
    fun selectAll() {
        getItems().forEach { selected.add(it) }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
    }
    
    // 拖拽排序
    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = getItem(srcPosition)
        val targetItem = getItem(targetPosition)
        if (srcItem != null && targetItem != null) {
            val srcOrder = srcItem.customOrder
            srcItem.customOrder = targetItem.customOrder
            targetItem.customOrder = srcOrder
        }
        swapItem(srcPosition, targetPosition)
        return true
    }
    
    interface CallBack {
        fun enable(enable: Boolean, bookSource: BookSourcePart)
        fun edit(bookSource: BookSourcePart)
        fun upCountView()
        // ...
    }
}
```

### 8.2 Activity 中使用

```kotlin
class BookSourceActivity : AppCompatActivity() {
    
    private lateinit var adapter: BookSourceAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_source)
        
        // 初始化 Adapter
        adapter = BookSourceAdapter(this, callBack, recyclerView)
        
        // 设置动画
        adapter.itemAnimation = ItemAnimation.create()
            .enabled(true)
            .duration(300)
            .animation(ItemAnimation.SCALE_IN)
        
        // 设置点击监听
        adapter.setOnItemClickListener { holder, item ->
            // 处理点击
        }
        
        adapter.setOnItemLongClickListener { holder, item ->
            // 处理长按
            true
        }
        
        // 绑定 RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // 加载数据
        loadData()
    }
    
    private fun loadData() {
        lifecycleScope.launch {
            val sources = viewModel.getBookSources()
            // 使用 DiffUtil 更新
            adapter.setItems(sources, adapter.diffItemCallback)
        }
    }
}
```

---

## 九、最佳实践

### 9.1 选择合适的 Adapter

| 场景 | 推荐 Adapter |
|------|-------------|
| 简单列表，无频繁更新 | RecyclerAdapter |
| 频繁更新，需要动画 | DiffRecyclerAdapter |
| 需要 Header/Footer | RecyclerAdapter |
| 数据量大（>1000条） | DiffRecyclerAdapter + AsyncListDiffer |

### 9.2 DiffUtil 最佳实践

```kotlin
// ✅ 正确的 areItemsTheSame
override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
    return oldItem.id == newItem.id  // 使用唯一 ID
}

// ❌ 错误的 areItemsTheSame
override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
    return oldItem == newItem  // 会比较所有字段，失去意义
}

// ✅ 正确的 areContentsTheSame
override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
    return oldItem.name == newItem.name 
            && oldItem.price == newItem.price
            // 只比较会显示的字段
}
```

### 9.3 性能优化

```kotlin
// 1. 使用 Payload 局部刷新
override fun getChangePayload(oldItem: Book, newItem: Book): Any? {
    val payload = Bundle()
    if (oldItem.likeCount != newItem.likeCount) {
        payload.putInt("likeCount", newItem.likeCount)
    }
    return payload
}

// 2. 避免在 convert 中创建对象
override fun convert(...) {
    // ❌ 错误：每次都会创建
    val formatter = SimpleDateFormat("yyyy-MM-dd")
    
    // ✅ 正确：使用成员变量或静态变量
    binding.textView.text = dateFormatter.format(item.date)
}

// 3. 复杂计算移到后台
fun setItems(items: List<ITEM>) {
    lifecycleScope.launch(Dispatchers.Default) {
        val diffResult = DiffUtil.calculateDiff(callback)
        withContext(Dispatchers.Main) {
            diffResult.dispatchUpdatesTo(this@Adapter)
        }
    }
}
```

### 9.4 常见错误

```kotlin
// ❌ 错误 1：直接修改 item 数据
override fun convert(...) {
    item.name = "新名称"  // 不要修改数据！
}

// ✅ 正确
override fun convert(...) {
    binding.textView.text = item.name
}

// ❌ 错误 2：在回调中直接使用 item
adapter.setOnItemClickListener { holder, item ->
    // item 可能已经被复用，数据不对
    viewModel.delete(item)
}

// ✅ 正确
adapter.setOnItemClickListener { holder, item ->
    // 通过 position 获取最新数据
    adapter.getItem(holder.layoutPosition)?.let {
        viewModel.delete(it)
    }
}
```

---

## 参考资源

- **Android 官方文档**: [RecyclerView](https://developer.android.com/guide/topics/ui/layout/recyclerview)
- **DiffUtil 文档**: [DiffUtil](https://developer.android.com/reference/androidx/recyclerview/widget/DiffUtil)
- **ViewBinding 文档**: [ViewBinding](https://developer.android.com/topic/libraries/view-binding)
- **项目文件**:
  - `app/src/main/java/io/legado/app/base/adapter/`

---

> 文档生成时间：2026年3月
> 核心类：RecyclerAdapter(486行)、DiffRecyclerAdapter(233行)、BookSourceAdapter(403行)
