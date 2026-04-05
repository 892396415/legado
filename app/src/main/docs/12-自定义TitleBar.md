# 自定义 TitleBar 详细学习文档

> 本文档基于 Legado 开源阅读项目，深入讲解 Android 自定义 TitleBar 的实现原理、设计思路和最佳实践。

---

## 目录

- [一、TitleBar 概述](#一titlebar-概述)
- [二、Android 标题栏演进史](#二android-标题栏演进史)
- [三、TitleBar 的设计目标](#三titlebar-的设计目标)
- [四、TitleBar 源码分析](#四titlebar-源码分析)
- [五、自定义属性详解](#五自定义属性详解)
- [六、布局文件分析](#六布局文件分析)
- [七、使用示例](#七使用示例)
- [八、主题与样式集成](#八主题与样式集成)
- [九、沉浸式状态栏适配](#九沉浸式状态栏适配)
- [十、扩展与定制](#十扩展与定制)
- [十一、常见问题与解决方案](#十一常见问题与解决方案)

---

## 一、TitleBar 概述

### 1.1 什么是 TitleBar

TitleBar（标题栏）是 Android 应用界面顶部的导航区域，通常包含：
- 返回按钮（Navigation Icon）
- 标题文字（Title）
- 副标题文字（Subtitle）
- 菜单按钮（Menu/Overflow）
- 自定义内容区域

### 1.2 Legado 项目中的 TitleBar

Legado 项目中的 `TitleBar` 是一个自定义 View，继承自 `AppBarLayout`，内部封装了 `Toolbar`，提供了：
- 统一的外观样式
- 主题切换支持（亮色/暗色）
- 沉浸式状态栏适配
- 丰富的自定义属性
- 与 Activity 的无缝集成

### 1.3 类继承关系

```
android.view.View
    └── android.view.ViewGroup
            └── android.widget.LinearLayout
                    └── com.google.android.material.appbar.AppBarLayout
                            └── io.legado.app.ui.widget.TitleBar
```

---

## 二、Android 标题栏演进史

### 2.1 ActionBar（Android 3.0+）

```kotlin
// 传统 ActionBar 使用
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 获取 ActionBar
        val actionBar = actionBar
        actionBar?.title = "标题"
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }
}
```

**缺点：**
- 样式定制受限
- 高度耦合在 Activity 中
- 不同系统版本表现不一致

### 2.2 Toolbar（Android 5.0+）

```kotlin
// Toolbar 使用
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "标题"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}
```

**优点：**
- 作为普通 View 使用，更加灵活
- 支持 Material Design
- 易于自定义样式

### 2.3 自定义 TitleBar（Legado 方案）

Legado 在 Toolbar 基础上进一步封装，提供更简洁的 API 和更多的定制选项。

---

## 三、TitleBar 的设计目标

### 3.1 设计原则

| 原则 | 说明 |
|------|------|
| **统一性** | 整个应用的标题栏外观保持一致 |
| **可配置性** | 支持通过 XML 属性灵活配置 |
| **主题适配** | 支持亮色/暗色主题切换 |
| **沉浸式支持** | 适配沉浸式状态栏 |
| **易用性** | 简化 Toolbar 的使用方式 |

### 3.2 功能特性

1. **自动关联 Activity**：自动调用 `setSupportActionBar()`
2. **主题模式切换**：支持亮色/暗色两种主题
3. **状态栏适配**：自动处理状态栏高度
4. **导航栏适配**：支持底部导航栏高度适配
5. **E-Ink 模式**：支持墨水屏模式（无阴影）
6. **多窗口适配**：适配分屏/多窗口模式

---

## 四、TitleBar 源码分析

### 4.1 完整源码

```kotlin
package io.legado.app.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Menu
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.alpha
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import com.google.android.material.appbar.AppBarLayout
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.activity
import splitties.views.bottomPadding
import splitties.views.topPadding

@Suppress("unused", "MemberVisibilityCanBePrivate")
class TitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppBarLayout(context, attrs) {

    // ========== 核心属性 ==========
    
    val toolbar: Toolbar
    val menu: Menu
        get() = toolbar.menu

    // 标题属性（使用委托模式）
    var title: CharSequence?
        get() = toolbar.title
        set(title) {
            if (toolbar.title != title) {
                toolbar.title = title
            }
        }

    // 副标题属性
    var subtitle: CharSequence?
        get() = toolbar.subtitle
        set(subtitle) {
            if (toolbar.subtitle != subtitle) {
                toolbar.subtitle = subtitle
            }
        }

    // 自定义属性
    private val displayHomeAsUp: Boolean
    private val navigationIconTint: ColorStateList?
    private val navigationIconTintMode: Int
    private val fitStatusBar: Boolean
    private val fitNavigationBar: Boolean
    private val attachToActivity: Boolean

    // ========== 初始化 ==========
    
    init {
        // 1. 读取自定义属性
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.TitleBar,
            R.attr.titleBarStyle, 0
        )
        
        navigationIconTint = a.getColorStateList(R.styleable.TitleBar_navigationIconTint)
        navigationIconTintMode = a.getInt(R.styleable.TitleBar_navigationIconTintMode, 9)
        attachToActivity = a.getBoolean(R.styleable.TitleBar_attachToActivity, true)
        displayHomeAsUp = a.getBoolean(R.styleable.TitleBar_displayHomeAsUp, true)
        fitStatusBar = a.getBoolean(R.styleable.TitleBar_fitStatusBar, true)
        fitNavigationBar = a.getBoolean(R.styleable.TitleBar_fitNavigationBar, false)

        val navigationIcon = a.getDrawable(R.styleable.TitleBar_navigationIcon)
        val navigationContentDescription =
            a.getText(R.styleable.TitleBar_navigationContentDescription)
        val titleText = a.getString(R.styleable.TitleBar_title)
        val subtitleText = a.getString(R.styleable.TitleBar_subtitle)

        // 2. 根据主题模式加载不同布局
        when (a.getInt(R.styleable.TitleBar_themeMode, 0)) {
            1 -> inflate(context, R.layout.view_title_bar_dark, this)
            else -> inflate(context, R.layout.view_title_bar, this)
        }
        
        toolbar = findViewById(R.id.toolbar)

        // 3. 配置 Toolbar
        toolbar.apply {
            // 设置导航图标
            navigationIcon?.let {
                this.navigationIcon = it
                this.navigationContentDescription = navigationContentDescription
            }

            // 设置标题样式
            if (a.hasValue(R.styleable.TitleBar_titleTextAppearance)) {
                this.setTitleTextAppearance(
                    context,
                    a.getResourceId(R.styleable.TitleBar_titleTextAppearance, 0)
                )
            }

            if (a.hasValue(R.styleable.TitleBar_titleTextColor)) {
                this.setTitleTextColor(a.getColor(R.styleable.TitleBar_titleTextColor, -0x1))
            }

            // 设置副标题样式
            if (a.hasValue(R.styleable.TitleBar_subtitleTextAppearance)) {
                this.setSubtitleTextAppearance(
                    context,
                    a.getResourceId(R.styleable.TitleBar_subtitleTextAppearance, 0)
                )
            }

            if (a.hasValue(R.styleable.TitleBar_subtitleTextColor)) {
                this.setSubtitleTextColor(a.getColor(R.styleable.TitleBar_subtitleTextColor, -0x1))
            }

            // 设置内容边距
            if (a.hasValue(R.styleable.TitleBar_contentInsetLeft)
                || a.hasValue(R.styleable.TitleBar_contentInsetRight)
            ) {
                this.setContentInsetsAbsolute(
                    a.getDimensionPixelSize(R.styleable.TitleBar_contentInsetLeft, 0),
                    a.getDimensionPixelSize(R.styleable.TitleBar_contentInsetRight, 0)
                )
            }

            // 设置标题和副标题文本
            if (!titleText.isNullOrBlank()) {
                this.title = titleText
            }

            if (!subtitleText.isNullOrBlank()) {
                this.subtitle = subtitleText
            }

            // 加载自定义内容布局
            if (a.hasValue(R.styleable.TitleBar_contentLayout)) {
                inflate(context, a.getResourceId(R.styleable.TitleBar_contentLayout, 0), this)
            }
        }

        // 4. 配置状态栏和导航栏适配
        if (!isInEditMode) {
            if (fitStatusBar || fitNavigationBar) {
                ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    if (fitStatusBar) {
                        topPadding = insets.top
                    }
                    if (fitNavigationBar) {
                        bottomPadding = insets.bottom
                    }
                    windowInsets
                }
            }

            // 5. 设置背景和阴影
            if (AppConfig.isEInkMode) {
                setBackgroundResource(R.drawable.bg_eink_border_bottom)
            } else {
                setBackgroundColor(context.primaryColor)
            }

            stateListAnimator = null
            elevation = context.elevation
        }
        a.recycle()
    }

    // ========== 生命周期回调 ==========
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachToActivity()
    }

    // ========== 公共方法 ==========
    
    fun setNavigationOnClickListener(clickListener: ((View) -> Unit)) {
        toolbar.setNavigationOnClickListener(clickListener)
    }

    fun setTitle(titleId: Int) {
        toolbar.setTitle(titleId)
    }

    fun setSubTitle(subtitleId: Int) {
        toolbar.setSubtitle(subtitleId)
    }

    fun setTitleTextColor(@ColorInt color: Int) {
        toolbar.setTitleTextColor(color)
    }

    fun setTitleTextAppearance(@StyleRes resId: Int) {
        toolbar.setTitleTextAppearance(context, resId)
    }

    fun setSubTitleTextColor(@ColorInt color: Int) {
        toolbar.setSubtitleTextColor(color)
    }

    fun setSubTitleTextAppearance(@StyleRes resId: Int) {
        toolbar.setSubtitleTextAppearance(context, resId)
    }

    fun setTextColor(@ColorInt color: Int) {
        setTitleTextColor(color)
        setSubTitleTextColor(color)
    }

    /**
     * 设置颜色滤镜（用于主题切换时统一修改图标颜色）
     */
    fun setColorFilter(@ColorInt color: Int) {
        val colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        toolbar.children.firstOrNull { it is ImageView }?.background?.colorFilter = colorFilter
        toolbar.navigationIcon?.colorFilter = colorFilter
        toolbar.overflowIcon?.colorFilter = colorFilter
        toolbar.menu.children.forEach {
            it.icon?.colorFilter = colorFilter
        }
    }

    override fun setBackgroundColor(color: Int) {
        if (color.alpha < 255) {
            // 这里不能改为 0f，改为 0f 在横屏模式下文字和图标颜色会变
            elevation = 0.1f
        }
        super.setBackgroundColor(color)
    }

    override fun setBackground(background: Drawable?) {
        if (background is ColorDrawable) {
            if (background.alpha < 255) {
                elevation = 0.1f
            }
        }
        super.setBackground(background)
    }

    /**
     * 多窗口模式变化回调
     */
    fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, fullScreen: Boolean) {
        // 可在此处理分屏模式下的特殊逻辑
    }

    // ========== 私有方法 ==========
    
    private fun attachToActivity() {
        if (attachToActivity) {
            activity?.let {
                it.setSupportActionBar(toolbar)
                it.supportActionBar?.setDisplayHomeAsUpEnabled(displayHomeAsUp)
            }
        }
    }
}
```

### 4.2 关键代码解析

#### 4.2.1 双布局策略

```kotlin
// 根据 themeMode 属性选择布局
when (a.getInt(R.styleable.TitleBar_themeMode, 0)) {
    1 -> inflate(context, R.layout.view_title_bar_dark, this)  // 暗色主题
    else -> inflate(context, R.layout.view_title_bar, this)     // 亮色主题
}
```

**优势：**
- 不同主题使用不同布局，避免运行时动态修改样式
- 布局文件简洁，只包含 Toolbar

#### 4.2.2 WindowInsets 适配

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    if (fitStatusBar) {
        topPadding = insets.top  // 状态栏高度
    }
    if (fitNavigationBar) {
        bottomPadding = insets.bottom  // 导航栏高度
    }
    windowInsets
}
```

**原理：**
- 使用 `WindowInsetsCompat` 获取系统栏高度
- 动态设置 padding 避免内容被系统栏遮挡

#### 4.2.3 E-Ink 模式适配

```kotlin
if (AppConfig.isEInkMode) {
    setBackgroundResource(R.drawable.bg_eink_border_bottom)
} else {
    setBackgroundColor(context.primaryColor)
}

stateListAnimator = null  // 禁用阴影动画
elevation = context.elevation
```

**原因：** 墨水屏设备不支持阴影和渐变，需要特殊处理

---

## 五、自定义属性详解

### 5.1 属性列表

在 `res/values/attrs.xml` 中定义：

```xml
<declare-styleable name="TitleBar">
    <!-- 基础属性 -->
    <attr name="title" />
    <attr name="subtitle" />
    <attr name="titleTextAppearance" />
    <attr name="titleTextColor" />
    <attr name="subtitleTextAppearance" />
    <attr name="subtitleTextColor" />
    
    <!-- 内容边距 -->
    <attr name="contentInsetEnd" />
    <attr name="contentInsetEndWithActions" />
    <attr name="contentInsetStart" />
    <attr name="contentInsetStartWithNavigation" />
    <attr name="contentInsetLeft" />
    <attr name="contentInsetRight" />
    
    <!-- 自定义属性 -->
    <attr name="contentLayout" format="reference" />
    <attr name="attachToActivity" format="boolean" />
    <attr name="displayHomeAsUp" format="boolean" />
    <attr name="navigationIcon" format="reference" />
    <attr name="fitStatusBar" format="boolean" />
    <attr name="fitNavigationBar" format="boolean" />
    <attr name="navigationContentDescription" format="reference|string" />
    <attr name="navigationIconTint" format="color|reference" />
    <attr name="themeMode" />
    <attr name="navigationIconTintMode" format="enum">
        <enum name="clear" value="0" />
        <enum name="src" value="1" />
        <enum name="src_atop" value="9" />
        <!-- ... 更多模式 -->
    </attr>
</declare-styleable>
```

### 5.2 属性说明表

| 属性名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `title` | string | null | 标题文字 |
| `subtitle` | string | null | 副标题文字 |
| `titleTextAppearance` | reference | 0 | 标题文字样式 |
| `titleTextColor` | color | -1 | 标题文字颜色 |
| `subtitleTextAppearance` | reference | 0 | 副标题文字样式 |
| `subtitleTextColor` | color | -1 | 副标题文字颜色 |
| `contentLayout` | reference | 0 | 自定义内容布局 |
| `attachToActivity` | boolean | true | 是否关联到 Activity |
| `displayHomeAsUp` | boolean | true | 是否显示返回按钮 |
| `fitStatusBar` | boolean | true | 是否适配状态栏 |
| `fitNavigationBar` | boolean | false | 是否适配导航栏 |
| `themeMode` | enum | 0 (auto) | 主题模式：auto/dark/light |
| `navigationIcon` | reference | null | 导航图标 |
| `navigationIconTint` | color | null | 导航图标着色 |

---

## 六、布局文件分析

### 6.1 亮色主题布局

`res/layout/view_title_bar.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.appcompat.widget.Toolbar 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/toolbar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:theme="?attr/actionBarStyle"
    app:titleTextAppearance="@style/ToolbarTitle"
    app:popupTheme="@style/AppTheme.PopupOverlay"/>
```

### 6.2 暗色主题布局

`res/layout/view_title_bar_dark.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.appcompat.widget.Toolbar 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/toolbar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:theme="@style/AppTheme.AppBarOverlay.Dark"
    app:titleTextAppearance="@style/ToolbarTitle"
    app:popupTheme="@style/AppTheme.PopupOverlay" />
```

**区别：**
- 亮色：`android:theme="?attr/actionBarStyle"`（跟随主题）
- 暗色：`android:theme="@style/AppTheme.AppBarOverlay.Dark"`（固定暗色）

### 6.3 样式定义

`res/values/styles.xml`：

```xml
<!-- 亮色主题 AppBar -->
<style name="AppTheme.AppBarOverlay.Light" parent="ThemeOverlay.AppCompat.Light">
    <item name="colorAccent">@color/md_grey_900</item>
</style>

<!-- 暗色主题 AppBar -->
<style name="AppTheme.AppBarOverlay.Dark" parent="ThemeOverlay.AppCompat.Dark">
    <item name="colorAccent">@color/md_grey_50</item>
</style>

<!-- 弹出菜单主题 -->
<style name="AppTheme.PopupOverlay" parent="ThemeOverlay.AppCompat.Light">
    <item name="overlapAnchor">false</item>
    <item name="colorAccent">@color/md_grey_900</item>
</style>

<!-- Toolbar 标题样式 -->
<style name="ToolbarTitle" parent="@style/TextAppearance.Widget.AppCompat.Toolbar.Title">
    <item name="android:textSize">20sp</item>
</style>
```

---

## 七、使用示例

### 7.1 基本使用

**XML 布局：**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <io.legado.app.ui.widget.TitleBar
        android:id="@+id/title_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:title="@string/about" />

    <!-- 内容区域 -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</LinearLayout>
```

**Activity 代码：**

```kotlin
class AboutActivity : VMBaseActivity<ActivityAboutBinding, AboutViewModel>() {
    
    override val binding by viewBinding(ActivityAboutBinding::inflate)
    override val viewModel by viewModels<AboutViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // TitleBar 已自动关联 Activity，无需额外设置
        // 可通过 binding.titleBar 访问
        binding.titleBar.setNavigationOnClickListener {
            finish()
        }
    }
}
```

### 7.2 带副标题

```xml
<io.legado.app.ui.widget.TitleBar
    android:id="@+id/title_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:title="书籍详情"
    app:subtitle="作者：张三" />
```

### 7.3 暗色主题

```xml
<io.legado.app.ui.widget.TitleBar
    android:id="@+id/title_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:themeMode="dark"
    app:title="@string/book_info" />
```

### 7.4 自定义内容

```xml
<io.legado.app.ui.widget.TitleBar
    android:id="@+id/title_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:title="@string/about">

    <!-- 自定义内容布局 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:layout_margin="6dp"
        android:padding="10dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="自定义内容"
            android:textSize="20sp"
            android:textStyle="bold" />

    </LinearLayout>

</io.legado.app.ui.widget.TitleBar>
```

### 7.5 代码动态设置

```kotlin
// 设置标题
binding.titleBar.title = "新标题"
binding.titleBar.setTitle(R.string.app_name)

// 设置副标题
binding.titleBar.subtitle = "副标题"
binding.titleBar.setSubTitle(R.string.subtitle)

// 设置标题颜色
binding.titleBar.setTitleTextColor(Color.WHITE)
binding.titleBar.setTextColor(Color.BLACK)  // 同时设置标题和副标题

// 设置标题样式
binding.titleBar.setTitleTextAppearance(R.style.ToolbarTitle)

// 设置导航点击监听
binding.titleBar.setNavigationOnClickListener {
    onBackPressed()
}

// 获取 Menu 添加菜单项
binding.titleBar.menu.apply {
    add("菜单1").setIcon(R.drawable.ic_menu1).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    add("菜单2").setIcon(R.drawable.ic_menu2).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
}

// 设置颜色滤镜（主题切换）
binding.titleBar.setColorFilter(Color.RED)
```

---

## 八、主题与样式集成

### 8.1 主题配置

在 `AndroidManifest.xml` 中设置应用主题：

```xml
<application
    android:theme="@style/AppTheme.Light">
    <!-- ... -->
</application>
```

### 8.2 动态主题切换

```kotlin
// 切换亮色主题
setTheme(R.style.AppTheme_Light)
recreate()

// 切换暗色主题
setTheme(R.style.AppTheme_Dark)
recreate()
```

### 8.3 TitleBar 主题属性

```xml
<style name="Base.AppTheme" parent="Theme.AppCompat.DayNight.NoActionBar">
    <!-- 标题栏样式 -->
    <item name="titleBarStyle">@style/TitleBarStyle</item>
    <item name="actionBarStyle">@style/AppTheme.AppBarOverlay.Light</item>
</style>

<style name="TitleBarStyle">
    <item name="fitStatusBar">true</item>
    <item name="displayHomeAsUp">true</item>
</style>
```

---

## 九、沉浸式状态栏适配

### 9.1 沉浸式原理

Android 沉浸式状态栏是指让应用内容延伸到状态栏下方，使状态栏与应用界面融为一体。

### 9.2 TitleBar 的适配方案

```kotlin
// 1. 在 BaseActivity 中设置透明状态栏
fun setupSystemBar() {
    if (fullScreen && !isInMultiWindow) {
        fullScreen()  // 设置全屏
    }
    
    val isTransparentStatusBar = AppConfig.isTransparentStatusBar
    val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
    setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, fullScreen)
}

// 2. TitleBar 自动适配状态栏高度
init {
    if (fitStatusBar) {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            topPadding = insets.top  // 状态栏高度作为顶部 padding
            windowInsets
        }
    }
}
```

### 9.3 使用工具类

```kotlin
// 设置透明状态栏
fun Window.setTransparentStatusBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        setDecorFitsSystemWindows(false)
    } else {
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

// 设置状态栏颜色
fun Activity.setStatusBarColor(@ColorInt color: Int) {
    window.statusBarColor = color
}

// 设置状态栏图标颜色（深色/浅色）
fun Activity.setLightStatusBar(isLight: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView)?.apply {
        isAppearanceLightStatusBars = isLight
    }
}
```

### 9.4 多窗口模式适配

```kotlin
// TitleBar.kt
fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, fullScreen: Boolean) {
    // 分屏模式下可能需要调整布局
    // 例如：在非全屏的多窗口模式下，不需要状态栏高度 padding
}

// BaseActivity.kt
override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
    super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
    findViewById<TitleBar>(R.id.title_bar)
        ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
    setupSystemBar()
}
```

---

## 十、扩展与定制

### 10.1 添加搜索功能

```kotlin
class SearchTitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TitleBar(context, attrs) {

    private val searchEditText: EditText

    init {
        // 添加搜索输入框
        val searchView = LayoutInflater.from(context)
            .inflate(R.layout.view_search_input, this, false)
        addView(searchView)
        
        searchEditText = searchView.findViewById(R.id.et_search)
    }

    fun setOnSearchListener(listener: (String) -> Unit) {
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                listener(searchEditText.text.toString())
                true
            } else {
                false
            }
        }
    }
}
```

### 10.2 添加进度条

```kotlin
fun TitleBar.setProgress(progress: Int) {
    // 添加或更新进度条
    var progressBar = findViewById<ProgressBar>(R.id.progress_bar)
    if (progressBar == null) {
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.id = R.id.progress_bar
        progressBar.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            4.dp
        )
        addView(progressBar)
    }
    progressBar.progress = progress
}
```

### 10.3 自定义返回按钮行为

```kotlin
// 在 Activity 中重写
binding.titleBar.setNavigationOnClickListener {
    // 自定义返回逻辑
    if (isTaskRoot) {
        // 如果是根 Activity，回到桌面
        moveTaskToBack(true)
    } else {
        finish()
    }
}
```

---

## 十一、常见问题与解决方案

### 11.1 问题一：状态栏高度不正确

**现象：** TitleBar 顶部出现空白或内容被状态栏遮挡

**原因：**
- WindowInsets 未正确获取
- 主题未设置透明状态栏

**解决：**
```kotlin
// 确保 Activity 设置透明状态栏
window.statusBarColor = Color.TRANSPARENT

// 确保 fitStatusBar 属性为 true
<io.legado.app.ui.widget.TitleBar
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:fitStatusBar="true" />
```

### 11.2 问题二：菜单不显示

**现象：** 添加了菜单项但不显示

**原因：** TitleBar 未关联到 Activity

**解决：**
```xml
<!-- 确保 attachToActivity 为 true -->
<io.legado.app.ui.widget.TitleBar
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:attachToActivity="true" />
```

或在代码中手动关联：
```kotlin
setSupportActionBar(binding.titleBar.toolbar)
```

### 11.3 问题三：主题切换后颜色不更新

**现象：** 切换亮色/暗色主题后，TitleBar 颜色未改变

**解决：**
```kotlin
override fun recreate() {
    // 重新创建 Activity 应用新主题
    super.recreate()
}

// 或在 onResume 中更新颜色
override fun onResume() {
    super.onResume()
    binding.titleBar.setBackgroundColor(context.primaryColor)
}
```

### 11.4 问题四：返回按钮不显示

**现象：** 没有显示返回箭头

**解决：**
```xml
<io.legado.app.ui.widget.TitleBar
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:displayHomeAsUp="true"
    app:navigationIcon="@drawable/ic_arrow_back" />
```

### 11.5 问题五：分屏模式下布局错乱

**现象：** 进入分屏模式后，TitleBar 高度异常

**解决：**
```kotlin
// 在 Activity 中监听多窗口模式变化
override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
    super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
    binding.titleBar.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
}
```

---

## 总结

Legado 项目的 TitleBar 是一个功能完善的自定义标题栏组件，通过封装 Toolbar 提供了：

1. **简洁的 API**：比直接使用 Toolbar 更简单
2. **主题适配**：内置亮色/暗色主题切换
3. **系统适配**：自动处理状态栏、导航栏、多窗口等场景
4. **高度可定制**：丰富的 XML 属性和公共方法
5. **E-Ink 支持**：特殊适配墨水屏设备

### 最佳实践

1. **统一使用**：整个应用统一使用 TitleBar，保持一致性
2. **主题配置**：在主题中统一配置 TitleBar 样式
3. **基类封装**：在 BaseActivity 中统一处理 TitleBar 初始化
4. **避免嵌套**：不要在 TitleBar 内部嵌套过深的布局层级

---

**参考资源：**
- [Android Toolbar 官方文档](https://developer.android.com/reference/androidx/appcompat/widget/Toolbar)
- [Material Design App Bar](https://material.io/components/app-bars-top)
- Legado 项目源码：
  - `app/src/main/java/io/legado/app/ui/widget/TitleBar.kt`
  - `app/src/main/res/layout/view_title_bar.xml`
  - `app/src/main/res/values/attrs.xml`

---

> 文档生成时间：2026年
> 基于 Legado 项目版本：3.x
