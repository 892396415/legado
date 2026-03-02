# execute 方法链式调用的实现原理

## 一、核心原理

`execute` 方法的链式调用是通过 **Coroutine 类** 实现的，它将 Kotlin 协程封装为类似 Promise/A+ 的链式 API。

## 二、源码分析

### 1. execute 方法定义 (BaseViewModel.kt:39-48)

```kotlin
fun <T> execute(
    scope: CoroutineScope = viewModelScope,
    context: CoroutineContext = Dispatchers.IO,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    executeContext: CoroutineContext = Dispatchers.Main,
    semaphore: Semaphore? = null,
    block: suspend CoroutineScope.() -> T
): Coroutine<T> {
    return Coroutine.async(scope, context, start, executeContext, semaphore, block)
}
```

关键点：
- `scope`：协程作用域，默认 `viewModelScope`
- `context`：任务执行线程，默认 `Dispatchers.IO`
- `executeContext`：回调线程，默认 `Dispatchers.Main`

### 2. Coroutine 类的链式实现

```kotlin
class Coroutine<T>(...) {
    
    // 保存回调（此时不执行，返回 this 实现链式）
    fun onSuccess(block: suspend CoroutineScope.(T) -> Unit): Coroutine<T> {
        this.success = Callback(context, block)
        return this@Coroutine
    }
    
    fun onError(block: suspend CoroutineScope.(Throwable) -> Unit): Coroutine<T> {
        this.error = Callback(context, block)
        return this@Coroutine
    }
    
    fun onFinally(block: suspend CoroutineScope.() -> Unit): Coroutine<T> {
        this.finally = VoidCallback(context, block)
        return this@Coroutine
    }
    
    // 在 init 块中启动协程
    init {
        this.job = executeInternal(context, block)
    }
}
```

## 三、链式调用流程图

```
execute { ... }          // 创建 Coroutine，启动协程
    │                       
    ├── .onSuccess { }   // 保存回调，返回 this
    ├── .onError { }     // 保存回调，返回 this  
    └── .onFinally { }   // 保存回调，返回 this

协程执行时:
    IO 线程执行 block
        │
        ├── 成功 ──▶ onSuccess 回调 (主线程)
        ├── 异常 ──▶ onError 回调 (主线程)
        └── 完成 ──▶ onFinally 回调 (主线程)
```

## 四、关键设计点

| 设计点 | 说明 |
|--------|------|
| 回调保存 | 通过属性保存回调，延迟到协程执行时调用 |
| 返回 this | 每个回调方法返回 `Coroutine<T>`，支持链式 |
| 线程切换 | 通过 `withContext(executeContext)` 切换到主线程 |
| 自动取消 | 使用 `viewModelScope`，ViewModel 销毁时自动取消 |
