# 为什么使用 postValue 而不是 value

## 一、两者区别

| 方法 | 线程 | 适用场景 |
|------|------|---------|
| `setValue()` | 主线程 | 只能在主线程调用 |
| `postValue()` | 任意线程 | 后台线程调用，自动切换到主线程 |

## 二、在 BookshelfViewModel.addBookByUrl 中的使用

```kotlin
// 在 IO 线程中执行
kotlin.runCatching {
    WebBook.getBookInfoAwait(bookSource, book)
}.onSuccess {
    successCount++
    addBookProgressLiveData.postValue(successCount)  // ✓ 正确
}
```

## 三、为什么用 postValue？

| 原因 | 说明 |
|------|------|
| 1. 线程安全 | `execute` 的 block 在 `Dispatchers.IO` 线程执行 |
| 2. 自动切换 | `postValue()` 自动将更新调度到主线程 |
| 3. 避免崩溃 | 在非主线程调用 `setValue()` 会抛出异常 |

## 四、源码分析

**setValue** (LiveData.kt):
```kotlin
@MainThread
protected void setValue(T value) {
    mVersion++;
    mData = value;
    dispatchingValue(this);
}
```

**postValue** (LiveData.kt):
```kotlin
protected void postValue(T value) {
    boolean postTask;
    synchronized (mDataLock) {
        postTask = mPendingData == NOT_SET;
        mPendingData = value;
    }
    if (!postTask) return;
    ArchTaskExecutor.getInstance().postToMainThread(mPostValueRunnable);
}
```

## 五、总结

| 场景 | 推荐方法 |
|------|---------|
| ViewModel 中 IO 线程更新 | `postValue()` |
| ViewModel 中主线程更新 | `setValue()` |
| 不确定调用线程 | `postValue()` |
| 高频更新 | `postValue()`（会合并多次更新） |
