# LiveData 变换详解

> LiveData 变换（Transformations）用于在 UI 层消费数据之前，对 LiveData 中的数据进行转换和操作。

---

## 一、map() - 数据类型转换

### 1.1 作用

将 LiveData 的数据转换为另一种类型，类似 RxJS 的 map 操作符。

### 1.2 源码签名

```kotlin
fun <X, Y> LiveData<X>.map(transform: (X) -> Y): LiveData<Y>
```

### 1.3 使用示例

```kotlin
// 原始数据：User 对象
data class User(val name: String, val age: Int)

// ViewModel
class UserViewModel : ViewModel() {
    
    private val _user = MutableLiveData<User>()
    
    // 转换后：只暴露用户名
    val userName: LiveData<String> = _user.map { it.name }
    
    // 转换后：成年状态
    val isAdult: LiveData<Boolean> = _user.map { it.age >= 18 }
}

// 使用
viewModel.userName.observe(this) { name ->
    textView.text = name  // 直接使用 String，无需解包
}
```

### 1.4 实际场景示例

```kotlin
class BookshelfViewModel : BaseViewModel(application) {
    
    // 原始数据：Book 列表
    private val _books = MutableLiveData<List<Book>>()
    
    // 转换1：只获取书籍数量
    val bookCount: LiveData<Int> = _books.map { it.size }
    
    // 转换2：按名称排序
    val sortedBooks: LiveData<List<Book>> = _books.map { 
        it.sortedBy { book -> book.name }
    }
    
    // 转换3：计算总章节数
    val totalChapters: LiveData<Int> = _books.map { books ->
        books.sumOf { it.totalChapterNum }
    }
}
```

---

## 二、switchMap() - 数据源切换

### 2.1 作用

当需要根据一个 LiveData 的值来切换到另一个数据源时使用，类似 RxJS 的 switchMap。

### 2.2 源码签名

```kotlin
fun <X, Y> LiveData<X>.switchMap(transform: (X) -> LiveData<Y>): LiveData<Y>
```

### 2.3 使用示例

```kotlin
// 场景：用户点击不同书籍，切换书籍详情

class BookDetailViewModel : ViewModel() {
    
    // 当前选中的书籍 ID
    private val _selectedBookId = MutableLiveData<String>()
    
    // 根据 bookId 切换数据源
    val bookDetail: LiveData<BookDetail> = _selectedBookId.switchMap { bookId ->
        // 每次 bookId 变化时，返回新的 LiveData
        repository.getBookDetail(bookId)
    }
    
    fun selectBook(bookId: String) {
        _selectedBookId.value = bookId  // 自动触发数据切换
    }
}

// Activity/Fragment 中使用
viewModel.bookDetail.observe(this) { detail ->
    // bookId 变化时，这里会自动收到新数据
    updateUI(detail)
}
```

### 2.4 实际场景示例

```kotlin
class SearchViewModel : ViewModel() {
    
    // 搜索关键词
    private val _searchKey = MutableLiveData<String>()
    
    // 根据关键词切换搜索结果数据源
    val searchResult: LiveData<List<Book>> = _searchKey.switchMap { key ->
        if (key.isBlank()) {
            MutableLiveData(emptyList())
        } else {
            repository.searchBooks(key)  // 返回 LiveData
        }
    }
    
    fun search(key: String) {
        _searchKey.value = key
    }
}
```

---

## 三、MediatorLiveData - 多数据源合并

### 3.1 作用

合并多个 LiveData 数据源，当任一数据源变化时都会通知观察者。

### 3.2 源码签名

```kotlin
class MediatorLiveData<T> : MutableLiveData<T>()
```

### 3.3 使用示例

```kotlin
class ProfileViewModel : ViewModel() {
    
    // 数据源1：本地用户信息
    private val localUser = MutableLiveData<User>()
    
    // 数据源2：远程用户信息
    private val remoteUser = MutableLiveData<User>()
    
    // 合并后的数据源
    val userData = MediatorLiveData<User>()
    
    init {
        // 添加本地数据源
        userData.addSource(localUser) { user ->
            userData.value = user
        }
        
        // 添加远程数据源（优先级更高）
        userData.addSource(remoteUser) { user ->
            userData.value = user  // 远程数据覆盖本地
        }
    }
    
    fun loadLocalUser(user: User) {
        localUser.value = user
    }
    
    fun loadRemoteUser(user: User) {
        remoteUser.value = user
    }
}
```

### 3.4 实际场景示例

```kotlin
class BookshelfViewModel : BaseViewModel(application) {
    
    // 多个书籍来源
    private val localBooks = MutableLiveData<List<Book>>()
    private val remoteBooks = MutableLiveData<List<Book>>()
    private val cachedBooks = MutableLiveData<List<Book>>()
    
    // 合并数据：优先级 缓存 > 本地 > 远程
    val allBooks = MediatorLiveData<List<Book>>().apply {
        addSource(cachedBooks) { books ->
            if (books != null) value = books
        }
        addSource(localBooks) { books ->
            if (books != null && cachedBooks.value == null) value = books
        }
        addSource(remoteBooks) { books ->
            if (books != null && cachedBooks.value == null && localBooks.value == null) {
                value = books
            }
        }
    }
}
```

---

## 四、综合使用示例

### 4.1 搜索 + 过滤 + 排序

```kotlin
class BookshelfViewModel : BaseViewModel(application) {
    
    // 原始数据
    private val _allBooks = MutableLiveData<List<Book>>()
    
    // 过滤条件
    private val _filterKey = MutableLiveData<String>()
    
    // 排序方式
    private val _sortOrder = MutableLiveData<SortOrder>()
    
    // 变换1：过滤
    val filteredBooks: LiveData<List<Book>> = _filterKey.switchMap { key ->
        if (key.isBlank()) {
            _allBooks
        } else {
            _allBooks.map { books ->
                books.filter { it.name.contains(key, ignoreCase = true) }
            }
        }
    }
    
    // 变换2：排序
    val sortedBooks: LiveData<List<Book>> = MediatorLiveData<List<Book>>().apply {
        addSource(filteredBooks) { books ->
            value = sortBooks(books, _sortOrder.value ?: SortOrder.NAME)
        }
        addSource(_sortOrder) { order ->
            filteredBooks.value?.let { books ->
                value = sortBooks(books, order)
            }
        }
    }
    
    private fun sortBooks(books: List<Book>, order: SortOrder): List<Book> {
        return when (order) {
            SortOrder.NAME -> books.sortedBy { it.name }
            SortOrder.DATE -> books.sortedByDescending { it.latestUploadTime }
            SortOrder.AUTHOR -> books.sortedBy { it.author }
        }
    }
}
```

---

## 五、总结对比

| 变换 | 作用 | 返回类型 | 使用场景 |
|------|------|---------|---------|
| `map()` | 数据转换 | `LiveData<Y>` | 数据类型转换、提取字段 |
| `switchMap()` | 数据源切换 | `LiveData<Y>` | 根据条件切换数据源 |
| `MediatorLiveData` | 多源合并 | `MediatorLiveData<T>` | 合并多个数据源、优先级处理 |

---

## 六、注意事项

1. **Transformations 必须在主线程调用**
2. **map 和 switchMap 返回的是 LiveData**，不是直接值
3. **MediatorLiveData 可以添加多个数据源**，但要注意避免循环更新
4. **使用 switchMap 时**，确保返回的 LiveData 会被正确清理
