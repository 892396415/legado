# LiveEventBus 详解

> LiveEventBus 是基于 LiveData 的事件总线，用于组件间的通信。

---

## 一、概述

项目使用 `com.jeremyliao:liveeventbus` 库，具备以下特点：
- 基于 LiveData，生命周期感知
- 支持粘性事件
- 支持跨组件通信

---

## 二、核心 API

### 1. 发送事件

```kotlin
// 发送普通事件
postEvent<String>(tag, event)

// 延迟发送
postEventDelay<String>(tag, event, delayMillis)

// 有序发送（按接收顺序）
postEventOrderly<String>(tag, event)
```

### 2. 接收事件

```kotlin
// 普通观察（生命周期感知）
observeEvent<String>(tag) { event ->
    // 处理事件
}

// 粘性观察（可接收之前发送的事件）
observeEventSticky<String>(tag) { event ->
    // 处理事件
}
```

---

## 三、项目中的使用

### 1. 事件定义 (EventBus.kt)

```kotlin
object EventBus {
    const val UP_BOOKSHELF = "upBookToc"
    const val BOOKSHELF_REFRESH = "bookshelfRefresh"
    const val UP_CONFIG = "upConfig"
    const val SOURCE_CHANGED = "sourceChanged"
    const val SEARCH_RESULT = "searchResult"
    // ... 更多事件
}
```

### 2. 在 Fragment 中接收事件

```kotlin
// BookshelfFragment2.kt
override fun observeLiveBus() {
    super.observeLiveBus()
    
    // 观察书架更新事件
    observeEvent<String>(EventBus.UP_BOOKSHELF) { bookUrl ->
        booksAdapter.notification(bookUrl)
    }
    
    // 观察书架刷新事件
    observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
        booksAdapter.notifyDataSetChanged()
    }
}
```

### 3. 在任意位置发送事件

```kotlin
// 在 Service 或其他组件中
postEvent<String>(EventBus.UP_BOOKSHELF, bookUrl)
postEvent(EventBus.BOOKSHELF_REFRESH, "")
```

---

## 四、普通事件 vs 粘性事件

| 类型 | 说明 | 使用场景 |
|------|------|---------|
| 普通事件 | 观察时才接收，之前的会被丢弃 | 实时状态更新 |
| 粘性事件 | 可以接收之前发送的最新事件 | 初始化同步 |

### 粘性事件示例

```kotlin
// 发送粘性事件
LiveEventBus.get<String>(tag).postSticky(event)

// 接收粘性事件
observeEventSticky<String>(tag) { event ->
    // 即使之前已经发送过，也能收到最新值
}
```

---

## 五、与 LiveData 的对比

| 特性 | LiveData | LiveEventBus |
|------|----------|--------------|
| 通信方向 | 单向（VM→UI） | 任意组件间 |
| 事件类型 | 单值 | 多事件 |
| 粘性支持 | 不支持 | 支持 |
| 跨组件 | 困难 | 简单 |

---

## 六、LiveEventBus vs EventBus (GreenRobot)

| 特性 | LiveEventBus | EventBus |
|------|--------------|----------|
| 线程模型 | 基于 LiveData | 基于反射 |
| 内存泄漏 | 自动感知生命周期 | 需手动注销 |
| 性能 | 较好 | 较好 |

---

## 七、最佳实践

### 1. 集中管理事件

```kotlin
// 定义事件常量
object EventBus {
    const val REFRESH_BOOK = "refresh_book"
    const val UPDATE_CONFIG = "update_config"
}
```

### 2. 在 Base 类中统一处理

```kotlin
// BaseActivity/BaseFragment 中
open fun observeLiveBus() {
    // 子类重写实现
}
```

### 3. 避免内存泄漏

```kotlin
// LiveEventBus 会自动随生命周期销毁
// 但建议在不需要时及时停止观察
observeEvent<String>(tag) { }
```

---

## 八、项目完整示例

### 1. 定义事件

```kotlin
// constant/EventBus.kt
object EventBus {
    const val UP_BOOKSHELF = "upBookToc"
    const val BOOKSHELF_REFRESH = "bookshelfRefresh"
}
```

### 2. 发送事件（Service）

```kotlin
// service/CacheBookService.kt
import io.legado.app.utils.postEvent
import io.legado.app.constant.EventBus

class CacheBookService : BaseService() {
    fun onBookCached(bookUrl: String) {
        postEvent(EventBus.UP_BOOKSHELF, bookUrl)
    }
}
```

### 3. 接收事件（Fragment）

```kotlin
// ui/main/bookshelf/BookshelfFragment2.kt
import io.legado.app.utils.observeEvent
import io.legado.app.constant.EventBus

class BookshelfFragment2 : BaseBookshelfFragment(...) {
    
    override fun observeLiveBus() {
        super.observeLiveBus()
        
        // 观察事件
        observeEvent<String>(EventBus.UP_BOOKSHELF) { bookUrl ->
            // 刷新对应书籍
            upAdapterBook(bookUrl)
        }
        
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            // 刷新整个书架
            refreshBookshelf()
        }
    }
}
```

---

## 九、注意事项

1. **事件类型要一致**：发送和接收的类型必须匹配
2. **避免内存泄漏**：LiveEventBus 会自动处理，但要注意观察者的生命周期
3. **粘性事件慎用**：粘性事件会保留最后一次值，可能导致意外行为
4. **合理使用标签**：使用有意义的字符串作为事件标签
