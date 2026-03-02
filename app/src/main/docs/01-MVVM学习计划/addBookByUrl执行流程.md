# addBookByUrl 方法完整执行流程

> 本文档详细分析 BookshelfViewModel.addBookByUrl() 方法的完整执行流程

## 一、整体流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           addBookByUrl 执行流程                               │
└─────────────────────────────────────────────────────────────────────────────┘

    ┌──────────────────┐
    │ 1. 调用 execute  │ ◀── BaseViewModel.execute()
    └────────┬─────────┘
             │
             ▼  IO 线程
    ┌──────────────────────────────────────────────────────────────────────┐
    │                         execute { ... } 块                           │
    │  ┌─────────────────────────────────────────────────────────────────┐  │
    │  │ 2. 加载书源配置 (hasBookUrlPattern) - lazy                      │  │
    │  │ 3. 按换行符分割 URL 字符串                                       │  │
    │  └─────────────────────────────────────────────────────────────────┘  │
    │                              │                                        │
    │                              ▼ 循环处理每个 URL                      │
    │  ┌─────────────────────────────────────────────────────────────────┐  │
    │  │ 4. 去除首尾空格                                                 │  │
    │  │ 5. 空URL跳过                                                    │  │
    │  │ 6. 检查URL是否已存在 ───YES──▶ successCount++, 跳过           │  │
    │  │        │NO                                                     │  │
    │  │        ▼                                                       │  │
    │  │ 7. 提取 BaseUrl                                                │  │
    │  │ 8. 查找适配书源  ──NO──▶ 尝试正则匹配书源                      │  │
    │  │        │YES                                                    │  │
    │  │        ▼                                                       │  │
    │  │ 9. 创建 Book 对象                                              │  │
    │  │10. runCatching 获取书籍信息                                    │  │
    │  │    └─▶ WebBook.getBookInfoAwait()                             │  │
    │  │        ├── 网络请求 (OkHttp)                                   │  │
    │  │        ├── HTML解析 (Jsoup/XPath)                              │  │
    │  │        └── 返回完整 Book 对象                                   │  │
    │  │                                                               │  │
    │  │11. 检查是否已存在同名书籍                                      │  │
    │  │    ├── YES: 迁移更新 + 获取目录                                 │  │
    │  │    │       WebBook.getChapterListAwait()                     │  │
    │  │    │       更新数据库                                           │  │
    │  │    └── NO:  新增书籍到数据库                                    │  │
    │  │                                                               │  │
    │  │12. successCount++                                             │  │
    │  │13. postValue(successCount)  ──▶ 更新 UI 进度                  │  │
    │  └─────────────────────────────────────────────────────────────────┘  │
    └──────────────────────────────────────────────────────────────────────┘
             │
             ▼ 主线程回调
    ┌──────────────────┐
    │ .onSuccess { }  │ ◀── 成功回调
    └────────┬─────────┘
             │
             ▼
    ┌──────────────────────────────────────────────────────────────────────┐
    │ if (successCount > 0)                                               │
    │     toast("添加成功")                                                │
    │ else                                                                  │
    │     toast("添加网址失败")                                             │
    └──────────────────────────────────────────────────────────────────────┘

             │
             ▼ 主线程回调
    ┌──────────────────┐
    │ .onError { }     │ ◀── 异常回调 (可选)
    └────────┬─────────┘
             │
             ▼
    ┌──────────────────────────────────────────────────────────────────────┐
    │ AppLog.put("添加网址出错\n${it.localizedMessage}", it, true)        │
    └──────────────────────────────────────────────────────────────────────┘

             │
             ▼ 主线程回调
    ┌──────────────────┐
    │ .onFinally { }   │ ◀── 最终回调
    └────────┬─────────┘
             │
             ▼
    ┌──────────────────────────────────────────────────────────────────────┐
    │ addBookProgressLiveData.postValue(-1)  ◀── 隐藏进度对话框           │
    └──────────────────────────────────────────────────────────────────────┘
```

---

## 二、详细步骤说明

### 阶段一：启动协程 (第40行)

```kotlin
addBookJob = execute {  // 调用 BaseViewModel.execute()
    // 后台任务...
}.onSuccess { ... }
.onError { ... }
.onFinally { ... }
```

| 属性 | 值 | 说明 |
|------|-----|------|
| `scope` | `viewModelScope` | 随 ViewModel 销毁自动取消 |
| `context` | `Dispatchers.IO` | 后台线程执行 |
| `executeContext` | `Dispatchers.Main` | 回调在主线程 |

---

### 阶段二：核心业务逻辑 (第41-87行)

```
┌─────────────────────────────────────────────────────────────┐
|  for (url in urls) { ... }  // 循环处理每个URL              │
└─────────────────────────────────────────────────────────────┘

步骤1: 预处理
  ├── val bookUrl = url.trim()           // 去除空格
  ├── if (bookUrl.isEmpty()) continue   // 空URL跳过

步骤2: 查重
  ├── appDb.bookDao.getBook(bookUrl)    // 查询数据库
  └── if (exists) { successCount++; continue }  // 已存在则跳过

步骤3: 匹配书源
  ├── NetworkUtils.getBaseUrl(bookUrl)  // 提取基础URL
  ├── appDb.bookSourceDao.getBookSourceAddBook(baseUrl)  // 精确匹配
  └── 失败则用正则匹配: bookUrl.matches(bookUrlPattern)

步骤4: 获取书籍信息
  ├── WebBook.getBookInfoAwait(bookSource, book)
  │     ├── OkHttp 网络请求
  │     ├── Jsoup 解析 HTML
  │     ├── XPath 提取书籍信息(书名/作者/封面等)
  │     └── 返回填充好的 Book 对象
  │
步骤5: 保存书籍
  ├── dbBook = appDb.bookDao.getBook(it.name, it.author)
  │
  ├── if (dbBook != null) {  // 已存在，同步更新
  │     ├── toc = WebBook.getChapterListAwait()  // 获取目录
  │     ├── dbBook.migrateTo(it, toc)            // 迁移数据
  │     ├── appDb.bookDao.insert(it)             // 更新书籍
  │     └── appDb.bookChapterDao.insert(*toc)   // 更新目录
  │     }
  └── else {  // 新书
        ├── it.order = appDb.bookDao.minOrder - 1
        └── it.save()  // 新增书籍
        }

步骤6: 更新进度
  └── addBookProgressLiveData.postValue(successCount)
```

---

### 阶段三：回调处理

| 回调 | 触发条件 | 执行内容 |
|------|----------|----------|
| `.onSuccess` | 协程正常完成 | 显示成功/失败 Toast |
| `.onError` | 发生异常 | 记录错误日志 |
| `.onFinally` | 无论成功/失败 | 隐藏进度对话框 (`-1`) |

---

## 三、关键类和方法调用链

```
BookshelfViewModel.addBookByUrl()
    │
    ├── BaseViewModel.execute()
    │       │
    │       └── Coroutine.async()
    │               │
    │               └── scope.launch() { ... }  // IO线程
    │
    ├── appDb.bookSourceDao.hasBookUrlPattern  // 加载书源配置
    │
    ├── NetworkUtils.getBaseUrl()  // 提取BaseUrl
    │
    ├── appDb.bookSourceDao.getBookSourceAddBook()  // 查找书源
    │
    ├── WebBook.getBookInfoAwait()  // 获取书籍信息
    │       │
    │       ├── AnalyzeUrl.getStrResponseAwait()  // 网络请求
    │       │       └── OkHttp.newCall()
    │       │
    │       └── BookInfo.analyzeBookInfo()  // HTML解析
    │               └── Jsoup.selectXpath()
    │
    ├── appDb.bookDao.getBook()  // 查重
    │
    ├── WebBook.getChapterListAwait()  // 获取目录
    │
    ├── appDb.bookDao.insert()  // 保存书籍
    │
    ├── appDb.bookChapterDao.insert()  // 保存目录
    │
    └── addBookProgressLiveData.postValue()  // 通知UI
```

---

## 四、数据流变化

```
输入: "https://example.com/book/123\nhttps://example.com/book/456"

URL处理:
  URL1 ──▶ 获取书籍信息 ──▶ 保存 ──▶ successCount=1 ──▶ postValue(1)
  URL2 ──▶ 获取书籍信息 ──▶ 保存 ──▶ successCount=2 ──▶ postValue(2)

UI观察 (BaseBookshelfFragment):
  addBookProgressLiveData.observe(this) { count ->
      if (count == -1)  waitDialog.dismiss()  // 隐藏
      else              waitDialog.show()    // 显示
  }

回调执行:
  onSuccess ──▶ toast("添加成功") / "添加网址失败"
  onFinally ──▶ postValue(-1)  // 通知完成
```

---

## 五、异常处理

| 异常类型 | 处理方式 |
|---------|---------|
| URL为空 | `continue` 跳过 |
| 书源未匹配 | `continue` 跳过 |
| 网络请求失败 | `runCatching` 捕获，`continue` |
| 数据库异常 | `runCatching` 捕获，记录日志 |
| 协程被取消 | `onFinally` 仍会执行，隐藏对话框 |

---

## 六、核心源码

### BookshelfViewModel.addBookByUrl()

```kotlin
fun addBookByUrl(bookUrls: String) {
    var successCount = 0
    addBookJob = execute {  // 启动协程，IO线程执行
        // 1. 加载有正则的书源配置
        val hasBookUrlPattern: List<BookSourcePart> by lazy {
            appDb.bookSourceDao.hasBookUrlPattern
        }
        
        // 2. 按换行分割URL
        val urls = bookUrls.split("\n")
        for (url in urls) {
            val bookUrl = url.trim()
            if (bookUrl.isEmpty()) continue
            
            // 3. 检查是否已存在
            if (appDb.bookDao.getBook(bookUrl) != null) {
                successCount++
                continue
            }
            
            // 4. 获取BaseUrl并匹配书源
            val baseUrl = NetworkUtils.getBaseUrl(bookUrl) ?: continue
            var source = appDb.bookSourceDao.getBookSourceAddBook(baseUrl)
            
            // 5. 正则匹配书源
            if (source == null) {
                for (bookSource in hasBookUrlPattern) {
                    try {
                        val bs = bookSource.getBookSource()!!
                        if (bookUrl.matches(bs.bookUrlPattern!!.toRegex())) {
                            source = bs
                            break
                        }
                    } catch (_: Exception) { }
                }
            }
            
            val bookSource = source ?: continue
            
            // 6. 创建Book对象
            val book = Book(
                bookUrl = bookUrl,
                origin = bookSource.bookSourceUrl,
                originName = bookSource.bookSourceName
            )
            
            // 7. 获取书籍信息
            kotlin.runCatching {
                WebBook.getBookInfoAwait(bookSource, book)
            }.onSuccess {
                // 8. 检查是否已存在同名书籍
                val dbBook = appDb.bookDao.getBook(it.name, it.author)
                if (dbBook != null) {
                    // 已存在，同步更新
                    val toc = WebBook.getChapterListAwait(bookSource, it).getOrThrow()
                    dbBook.migrateTo(it, toc)
                    appDb.bookDao.insert(it)
                    appDb.bookChapterDao.insert(*toc.toTypedArray())
                } else {
                    // 新书，直接保存
                    it.order = appDb.bookDao.minOrder - 1
                    it.save()
                }
                successCount++
                addBookProgressLiveData.postValue(successCount)
            }
        }
    }.onSuccess {  // 主线程回调
        if (successCount > 0) {
            context.toastOnUi(R.string.success)
        } else {
            context.toastOnUi("添加网址失败")
        }
    }.onError {  // 异常回调
        AppLog.put("添加网址出错\n${it.localizedMessage}", it, true)
    }.onFinally {  // 最终回调
        addBookProgressLiveData.postValue(-1)
    }
}
```
