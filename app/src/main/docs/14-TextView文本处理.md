# TextView 文本处理详解

> 基于 Legado 项目的自定义 TextView 实践，深入解析文本样式、描边、背景、滚动、角标等高级文本处理技术。

## 目录

- [一、基础概述](#一基础概述)
- [二、主题色文本控件](#二主题色文本控件)
- [三、描边文本控件](#三描边文本控件)
- [四、背景文本控件](#四背景文本控件)
- [五、角标控件](#五角标控件)
- [六、斜角标签](#六斜角标签)
- [七、滚动文本](#七滚动文本)
- [八、多行文本](#八多行文本)
- [九、自定义属性](#九自定义属性)
- [十、最佳实践](#十最佳实践)

---

## 一、基础概述

### 1.1 为什么要自定义 TextView

| 需求 | 原生 TextView | 自定义 TextView |
|------|--------------|----------------|
| 主题适配 | 需手动设置颜色 | 自动跟随主题 |
| 描边效果 | 不支持 | 自定义实现 |
| 角标显示 | 需额外布局 | 内置支持 |
| 滚动效果 | 有限 | 自定义惯性滚动 |
| 动态背景 | 需代码设置 | XML 直接配置 |

### 1.2 继承体系

```
android.widget.TextView
    ├── AppCompatTextView (推荐继承)
    │       ├── PrimaryTextView
    │       ├── SecondaryTextView
    │       ├── AccentTextView
    │       ├── StrokeTextView
    │       ├── AccentBgTextView
    │       ├── ScrollTextView
    │       └── ...
    └── View
            └── BevelLabelView
```

### 1.3 项目中的 TextView 家族

```
ui/widget/text/
├── PrimaryTextView.kt          # 主题主色文本
├── SecondaryTextView.kt        # 主题次色文本
├── AccentTextView.kt           # 强调色文本
├── StrokeTextView.kt           # 描边文本
├── AccentStrokeTextView.kt     # 强调色描边
├── AccentBgTextView.kt         # 强调色背景
├── BadgeView.kt                # 角标
├── BevelLabelView.kt           # 斜角标签
├── ScrollTextView.kt           # 滚动文本
├── MultilineTextView.kt        # 多行文本
├── AutoCompleteTextView.kt     # 自动完成
└── TextInputLayout.kt          # 输入框布局
```

---

## 二、主题色文本控件

### 2.1 PrimaryTextView - 主题主色

```kotlin
class PrimaryTextView(context: Context, attrs: AttributeSet) :
    AppCompatTextView(context, attrs) {

    init {
        // 自动设置主题主色
        setTextColor(ThemeStore.textColorPrimary(context))
    }
}
```

**功能**：自动应用当前主题的**主文本颜色**。

**使用场景**：
```xml
<!-- 标题文本，自动适配主题 -->
<io.legado.app.ui.widget.text.PrimaryTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="书籍标题"
    android:textSize="18sp" />
```

### 2.2 SecondaryTextView - 主题次色

```kotlin
class SecondaryTextView(context: Context, attrs: AttributeSet) :
    AppCompatTextView(context, attrs) {

    init {
        // 自动设置主题次色
        setTextColor(context.secondaryTextColor)
    }
}
```

**功能**：自动应用当前主题的**次文本颜色**（通常是灰色）。

**使用场景**：
```xml
<!-- 描述文本，次要信息 -->
<io.legado.app.ui.widget.text.SecondaryTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="作者信息"
    android:textSize="14sp" />
```

### 2.3 AccentTextView - 强调色

```kotlin
class AccentTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    init {
        if (!isInEditMode) {
            setTextColor(context.accentColor)
        } else {
            // 编辑器模式下使用默认颜色
            setTextColor(context.getCompatColor(R.color.accent))
        }
    }
}
```

**功能**：自动应用当前主题的**强调色**（通常是蓝色或应用主色调）。

**特点**：
- 支持预览模式（`isInEditMode`）
- 用于需要突出显示的文本

**使用场景**：
```xml
<!-- 重要提示、按钮文字 -->
<io.legado.app.ui.widget.text.AccentTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="立即阅读"
    android:textSize="16sp"
    android:textStyle="bold" />
```

### 2.4 主题适配原理

```kotlin
object ThemeStore {
    
    // 主文本颜色（通常是黑色或白色，根据主题）
    fun textColorPrimary(context: Context): Int {
        return context.getColorFromAttr(R.attr.colorPrimaryText)
    }
    
    // 次文本颜色（灰色）
    fun textColorSecondary(context: Context): Int {
        return context.getColorFromAttr(R.attr.colorSecondaryText)
    }
    
    // 强调色（应用主色调）
    fun accentColor(context: Context): Int {
        return context.getColorFromAttr(R.attr.colorAccent)
    }
}
```

**优势**：
- 自动适配深色/浅色主题
- 一处修改，全局生效
- 减少硬编码颜色值

---

## 三、描边文本控件

### 3.1 StrokeTextView - 基础描边

```kotlin
open class StrokeTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    private var radius = 1.dpToPx()  // 圆角半径
    private val isBottomBackground: Boolean

    init {
        // 读取自定义属性
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.StrokeTextView)
        radius = typedArray.getDimensionPixelOffset(
            R.styleable.StrokeTextView_radius, radius
        )
        isBottomBackground = typedArray.getBoolean(
            R.styleable.StrokeTextView_isBottomBackground, false
        )
        typedArray.recycle()
        
        upBackground()  // 更新背景
    }

    // 动态设置圆角
    fun setRadius(radius: Int) {
        this.radius = radius.dpToPx()
        upBackground()
    }

    private fun upBackground() {
        when {
            isInEditMode -> {
                // 编辑器模式使用默认颜色
                background = Selector.shapeBuild()
                    .setCornerRadius(radius)
                    .setStrokeWidth(1.dpToPx())
                    .setDefaultStrokeColor(context.getCompatColor(R.color.secondaryText))
                    .setSelectedStrokeColor(context.getCompatColor(R.color.accent))
                    .create()
            }
            isBottomBackground -> {
                // 底部背景模式：自适应颜色
                val isLight = ColorUtils.isColorLight(context.bottomBackground)
                background = Selector.shapeBuild()
                    .setCornerRadius(radius)
                    .setStrokeWidth(1.dpToPx())
                    .setDefaultStrokeColor(context.getPrimaryTextColor(isLight))
                    .setSelectedStrokeColor(context.accentColor)
                    .create()
            }
            else -> {
                // 普通模式
                background = Selector.shapeBuild()
                    .setCornerRadius(radius)
                    .setStrokeWidth(1.dpToPx())
                    .setDefaultStrokeColor(ThemeStore.textColorSecondary(context))
                    .setSelectedStrokeColor(ThemeStore.accentColor(context))
                    .create()
            }
        }
    }
}
```

**核心功能**：
- 圆角边框
- 描边颜色（支持主题适配）
- 选中状态颜色变化
- 按压效果

### 3.2 AccentStrokeTextView - 强调色描边

继承 `StrokeTextView`，使用强调色作为默认描边色。

### 3.3 Selector 构建器

```kotlin
object Selector {
    
    // 创建形状 Drawable
    fun shapeBuild(): ShapeBuilder {
        return ShapeBuilder()
    }
    
    class ShapeBuilder {
        private var cornerRadius: Int = 0
        private var strokeWidth: Int = 0
        private var strokeColor: Int = 0
        private var bgColor: Int = 0

        fun setCornerRadius(radius: Int) = apply { this.cornerRadius = radius }
        fun setStrokeWidth(width: Int) = apply { this.strokeWidth = width }
        fun setDefaultStrokeColor(color: Int) = apply { this.strokeColor = color }
        fun setDefaultBgColor(color: Int) = apply { this.bgColor = color }

        fun create(): Drawable {
            return GradientDrawable().apply {
                cornerRadius = this@ShapeBuilder.cornerRadius.toFloat()
                if (strokeWidth > 0) {
                    setStroke(strokeWidth, strokeColor)
                }
                setColor(bgColor)
            }
        }
    }
}
```

### 3.4 使用示例

```xml
<!-- 基础描边文本 -->
<io.legado.app.ui.widget.text.StrokeTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:padding="8dp"
    android:text="标签"
    app:radius="4dp" />

<!-- 圆角标签 -->
<io.legado.app.ui.widget.text.StrokeTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:paddingHorizontal="12dp"
    android:paddingVertical="4dp"
    android:text="完结"
    app:radius="12dp"
    app:isBottomBackground="true" />
```

### 3.5 效果对比

| 控件 | 效果 |
|------|------|
| `StrokeTextView` | 灰色边框，选中变强调色 |
| `AccentStrokeTextView` | 强调色边框 |

---

## 四、背景文本控件

### 4.1 AccentBgTextView - 强调色背景

```kotlin
class AccentBgTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private var radius = 0

    init {
        // 读取圆角属性
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AccentBgTextView)
        radius = typedArray.getDimensionPixelOffset(
            R.styleable.AccentBgTextView_radius, radius
        )
        typedArray.recycle()
        upBackground()
    }

    private fun upBackground() {
        val accentColor = if (isInEditMode) {
            context.getCompatColor(R.color.accent)
        } else {
            ThemeStore.accentColor(context)
        }

        // 创建背景
        background = Selector.shapeBuild()
            .setCornerRadius(radius)
            .setDefaultBgColor(accentColor)
            .setPressedBgColor(ColorUtils.darkenColor(accentColor))  // 按压变暗
            .create()

        // 自动设置文字颜色（根据背景亮度）
        setTextColor(
            if (ColorUtils.isColorLight(accentColor)) {
                Color.BLACK  // 浅色背景用黑色文字
            } else {
                Color.WHITE  // 深色背景用白色文字
            }
        )
    }
}
```

**核心功能**：
- 强调色背景
- 圆角设置
- 按压效果（颜色变暗）
- 自动适配文字颜色（黑白自动切换）

### 4.2 使用示例

```xml
<!-- 标签按钮 -->
<io.legado.app.ui.widget.text.AccentBgTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:paddingHorizontal="16dp"
    android:paddingVertical="8dp"
    android:text="确认"
    app:radius="8dp" />

<!-- 圆角胶囊按钮 -->
<io.legado.app.ui.widget.text.AccentBgTextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:paddingHorizontal="20dp"
    android:paddingVertical="10dp"
    android:text="立即下载"
    app:radius="20dp" />
```

### 4.3 自动文字颜色原理

```kotlin
object ColorUtils {
    
    // 判断颜色是浅色还是深色
    fun isColorLight(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 
                           0.587 * Color.green(color) + 
                           0.114 * Color.blue(color)) / 255
        return darkness < 0.5
    }
    
    // 颜色变暗
    fun darkenColor(color: Int, factor: Float = 0.8f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= factor  // 降低亮度
        return Color.HSVToColor(hsv)
    }
}
```

---

## 五、角标控件

### 5.1 BadgeView - 角标

```kotlin
class BadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    var isHideOnNull = true  // 空值时隐藏
    private var radius: Float = 8f
    
    // 获取角标数值
    val badgeCount: Int?
        get() = text?.toString()?.toIntOrNull()

    init {
        // 默认样式
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setPadding(dip2Px(5f), dip2Px(1f), dip2Px(5f), dip2Px(1f))
        gravity = Gravity.CENTER
        minWidth = dip2Px(16f)
        minHeight = dip2Px(16f)
        
        // 默认背景
        setBackground(radius, context.accentColor)
    }

    // 设置圆角背景
    fun setBackground(dipRadius: Float, badgeColor: Int) {
        val radius = dip2Px(dipRadius).toFloat()
        val radiusArray = FloatArray(8) { radius }
        
        val roundRect = RoundRectShape(radiusArray, null, null)
        val bgDrawable = ShapeDrawable(roundRect)
        bgDrawable.paint.color = badgeColor
        background = bgDrawable
        
        // 自动设置文字颜色
        setTextColor(
            if (ColorUtils.isColorLight(badgeColor)) Color.BLACK else Color.WHITE
        )
    }

    // 空值时自动隐藏
    override fun setText(text: CharSequence, type: BufferType) {
        if (isHideOnNull && TextUtils.isEmpty(text)) {
            invisible()
        } else {
            visible()
        }
        super.setText(text, type)
    }

    // 设置数值
    fun setBadgeCount(count: Int) {
        text = if (count == 0) "" else count.toString()
    }

    // 增减数值
    fun incrementBadgeCount(increment: Int) {
        val count = badgeCount ?: 0
        setBadgeCount(count + increment)
    }

    fun decrementBadgeCount(decrement: Int) {
        incrementBadgeCount(-decrement)
    }

    // 关联到目标视图
    fun setTargetView(target: View?) {
        // 将角标添加到目标视图的父布局
        if (target?.parent is FrameLayout) {
            (target.parent as FrameLayout).addView(this)
        } else if (target?.parent is ViewGroup) {
            // 创建 FrameLayout 作为容器
            val parentContainer = target.parent as ViewGroup
            val groupIndex = parentContainer.indexOfChild(target)
            parentContainer.removeView(target)
            
            val badgeContainer = FrameLayout(context)
            badgeContainer.layoutParams = target.layoutParams
            target.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            parentContainer.addView(badgeContainer, groupIndex, target.layoutParams)
            badgeContainer.addView(target)
            badgeContainer.addView(this)
        }
    }
}
```

### 5.2 核心功能

| 功能 | 说明 |
|------|------|
| **自动隐藏** | 数量为 0 或空时自动隐藏 |
| **自动颜色** | 根据背景色自动设置黑白文字 |
| **数值操作** | 支持增减数值 |
| **动态关联** | 自动创建容器布局 |

### 5.3 使用示例

```kotlin
// Kotlin 代码中使用
val badgeView = BadgeView(context)
badgeView.setTargetView(menuItemView)
badgeView.setBadgeCount(5)

// 增加未读数
badgeView.incrementBadgeCount(1)

// 减少未读数
badgeView.decrementBadgeCount(1)
```

```xml
<!-- XML 中使用 -->
<FrameLayout
    android:layout_width="wrap_content"
    android:layout_height="wrap_content">
    
    <ImageView
        android:id="@+id/iv_menu"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_message" />
    
    <io.legado.app.ui.widget.text.BadgeView
        android:id="@+id/badge"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|end"
        android:layout_margin="4dp"
        android:text="99+" />
</FrameLayout>
```

---

## 六、斜角标签

### 6.1 BevelLabelView - 斜角标签

```kotlin
class BevelLabelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // 8 种模式
    companion object {
        const val MODE_LEFT_TOP = 0           // 左上
        const val MODE_RIGHT_TOP = 1          // 右上
        const val MODE_LEFT_BOTTOM = 2        // 左下
        const val MODE_RIGHT_BOTTOM = 3       // 右下
        const val MODE_LEFT_TOP_FILL = 4      // 左上铺满
        const val MODE_RIGHT_TOP_FILL = 5     // 右上铺满
        const val MODE_LEFT_BOTTOM_FILL = 6   // 左下铺满
        const val MODE_RIGHT_BOTTOM_FILL = 7  // 右下铺满
    }

    private var mBgColor: Int
    private var mText: String
    private var mTextSize: Int
    private var mTextColor: Int
    private var mLength: Int      // 标签长度
    private var mCorner: Int      // 圆角
    private var mMode: Int        // 模式
    private var mRotate = 45      // 旋转角度

    private var mPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var path: Path = Path()

    init {
        // 读取属性
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BevelLabelView)
        mBgColor = typedArray.getColor(R.styleable.BevelLabelView_label_bg_color, context.accentColor)
        mText = typedArray.getString(R.styleable.BevelLabelView_label_text) ?: ""
        mTextSize = typedArray.getDimensionPixelOffset(
            R.styleable.BevelLabelView_label_text_size, sp2px(11)
        )
        mTextColor = typedArray.getColor(R.styleable.BevelLabelView_label_text_color, Color.WHITE)
        mLength = typedArray.getDimensionPixelOffset(
            R.styleable.BevelLabelView_label_length, dip2px(40)
        )
        mCorner = typedArray.getDimensionPixelOffset(R.styleable.BevelLabelView_label_corner, 0)
        mMode = typedArray.getInt(R.styleable.BevelLabelView_label_mode, MODE_RIGHT_TOP)
        typedArray.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        mPaint.color = mBgColor
        drawBackgroundText(canvas)
    }

    private fun drawBackgroundText(canvas: Canvas) {
        when (mMode) {
            MODE_LEFT_TOP -> {
                mRotate = -45
                leftTopMeasure()
                getLeftTop()
            }
            MODE_RIGHT_TOP -> {
                mRotate = 45
                rightTopMeasure()
                getRightTop()
            }
            // ... 其他模式
        }
        
        // 绘制背景路径
        canvas.drawPath(path, mPaint)
        
        // 绘制文字
        mPaint.textSize = mTextSize.toFloat()
        mPaint.textAlign = Paint.Align.CENTER
        mPaint.color = mTextColor
        canvas.translate(mX.toFloat(), mY.toFloat())
        canvas.rotate(mRotate.toFloat())
        
        // 计算基线位置
        val baseLineY = (-(mPaint.descent() + mPaint.ascent())).toInt() / 2
        canvas.drawText(mText, 0f, baseLineY.toFloat(), mPaint)
    }
}
```

### 6.2 8 种显示模式

```
┌─────────────────┐  ┌─────────────────┐
│ 左上           │  │           右上  │  MODE_LEFT_TOP / MODE_RIGHT_TOP
│    ╲           │  │           ╱    │
│     ╲          │  │          ╱     │
│      ╲         │  │         ╱      │
└─────────────────┘  └─────────────────┘

┌─────────────────┐  ┌─────────────────┐
│                 │  │                 │
│                 │  │                 │
│     ╱          │  │          ╲     │  MODE_LEFT_BOTTOM / MODE_RIGHT_BOTTOM
│    ╱           │  │           ╲    │
│ 左下           │  │           右下  │
└─────────────────┘  └─────────────────┘
```

### 6.3 使用示例

```xml
<!-- 右上角"VIP"标签 -->
<io.legado.app.ui.widget.text.BevelLabelView
    android:layout_width="60dp"
    android:layout_height="60dp"
    app:label_text="VIP"
    app:label_bg_color="@color/red"
    app:label_text_color="@color/white"
    app:label_mode="right_top"
    app:label_length="40dp" />

<!-- 左上角"NEW"标签 -->
<io.legado.app.ui.widget.text.BevelLabelView
    android:layout_width="60dp"
    android:layout_height="60dp"
    app:label_text="NEW"
    app:label_bg_color="@color/green"
    app:label_mode="left_top" />
```

---

## 七、滚动文本

### 7.1 ScrollTextView - 惯性滚动

```kotlin
/**
 * 嵌套惯性滚动 TextView
 * 解决 RecyclerView 嵌套 TextView 的滑动冲突
 */
class ScrollTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    private val scrollStateIdle = 0
    private val scrollStateDragging = 1
    private val scrollStateSettling = 2
    
    private val mViewFling: ViewFling by lazy { ViewFling() }
    private val velocityTracker: VelocityTracker by lazy { VelocityTracker.obtain() }
    private var mScrollState = scrollStateIdle
    
    // 滑动边界
    private var mOffsetHeight: Int = 0

    init {
        movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        initOffsetHeight()
    }

    // 计算可滑动高度
    private fun initOffsetHeight() {
        val layout = layout ?: return
        val layoutHeight = layout.height
        val paddingTop = totalPaddingTop
        val paddingBottom = totalPaddingBottom
        val height = measuredHeight
        
        mOffsetHeight = layoutHeight + paddingTop + paddingBottom - height
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (lineCount > maxLines) {
            gestureDetector.onTouchEvent(event)
        }
        velocityTracker.addMovement(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                setScrollState(scrollStateIdle)
                mLastTouchY = (event.y + 0.5f).toInt()
            }
            MotionEvent.ACTION_MOVE -> {
                // 处理滑动
                handleMove(event)
            }
            MotionEvent.ACTION_UP -> {
                // 处理惯性滑动
                velocityTracker.computeCurrentVelocity(1000, mMaxFlingVelocity.toFloat())
                val yVelocity = velocityTracker.yVelocity
                if (abs(yVelocity) > mMinFlingVelocity) {
                    mViewFling.fling(-yVelocity.toInt())
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    // 惯性滑动类
    private inner class ViewFling : Runnable {
        private val mScroller: OverScroller = OverScroller(context, sQuinticInterpolator)

        override fun run() {
            if (mScroller.computeScrollOffset()) {
                val y = mScroller.currY
                val dy = y - mLastFlingY
                mLastFlingY = y
                
                // 边界检查
                if (dy < 0 && scrollY > 0) {
                    scrollBy(0, max(dy, -scrollY))
                } else if (dy > 0 && scrollY < mOffsetHeight) {
                    scrollBy(0, min(dy, mOffsetHeight - scrollY))
                }
                postOnAnimation()
            }
        }

        fun fling(velocityY: Int) {
            mLastFlingY = 0
            setScrollState(scrollStateSettling)
            mScroller.fling(0, 0, 0, velocityY, 
                Integer.MIN_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE)
            postOnAnimation()
        }
    }
}
```

### 7.2 核心特性

| 特性 | 实现 |
|------|------|
| **滑动冲突处理** | 判断滑动边界，适时让出事件 |
| **惯性滚动** | VelocityTracker + OverScroller |
| **边界限制** | 计算最大滑动距离 |
| **平滑插值器** | QuinticInterpolator (x-1)^5 + 1 |

### 7.3 滑动冲突解决

```kotlin
// 当滑动到边界时，让父布局处理事件
override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
    val y = scrollY + distanceY
    if (y < 0 || y > mOffsetHeight) {
        // 到达边界，不拦截事件
        parent.requestDisallowInterceptTouchEvent(false)
    } else {
        // 在范围内，拦截事件
        parent.requestDisallowInterceptTouchEvent(true)
    }
    return true
}
```

### 7.4 使用场景

```xml
<!-- RecyclerView 中的长文本 -->
<androidx.recyclerview.widget.RecyclerView
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <!-- item 布局 -->
    <io.legado.app.ui.widget.text.ScrollTextView
        android:layout_width="match_parent"
        android:layout_height="100dp"
        android:maxLines="5"
        android:text="很长的内容..." />
</androidx.recyclerview.widget.RecyclerView>
```

---

## 八、多行文本

### 8.1 MultilineTextView - 自适应行数

```kotlin
class MultilineTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    init {
        // Android 13+ 禁用回退行间距
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isFallbackLineSpacing = false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        calculateLines(heightSize)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun calculateLines(measuredHeight: Int) {
        val lineHeight = lineHeight
        val lines = measuredHeight / lineHeight
        setLines(lines)
    }
}
```

**功能**：根据控件高度自动计算可显示的行数。

### 8.2 使用示例

```xml
<!-- 固定高度，自动计算行数 -->
<io.legado.app.ui.widget.text.MultilineTextView
    android:layout_width="match_parent"
    android:layout_height="120dp"
    android:text="多行文本内容..." />
```

---

## 九、自定义属性

### 9.1 attrs.xml 定义

```xml
<!-- res/values/attrs.xml -->
<resources>
    <!-- StrokeTextView -->
    <declare-styleable name="StrokeTextView">
        <attr name="radius" format="dimension" />
        <attr name="isBottomBackground" format="boolean" />
    </declare-styleable>

    <!-- AccentBgTextView -->
    <declare-styleable name="AccentBgTextView">
        <attr name="radius" format="dimension" />
    </declare-styleable>

    <!-- BadgeView -->
    <declare-styleable name="BadgeView">
        <attr name="radius" format="dimension" />
        <attr name="up_flat_angle" format="boolean" />
    </declare-styleable>

    <!-- BevelLabelView -->
    <declare-styleable name="BevelLabelView">
        <attr name="label_text" format="string" />
        <attr name="label_text_size" format="dimension" />
        <attr name="label_text_color" format="color" />
        <attr name="label_bg_color" format="color" />
        <attr name="label_length" format="dimension" />
        <attr name="label_corner" format="dimension" />
        <attr name="label_mode" format="enum">
            <enum name="left_top" value="0" />
            <enum name="right_top" value="1" />
            <enum name="left_bottom" value="2" />
            <enum name="right_bottom" value="3" />
            <enum name="left_top_fill" value="4" />
            <enum name="right_top_fill" value="5" />
            <enum name="left_bottom_fill" value="6" />
            <enum name="right_bottom_fill" value="7" />
        </attr>
    </declare-styleable>
</resources>
```

### 9.2 属性使用

```kotlin
// 读取属性
val typedArray = context.obtainStyledAttributes(attrs, R.styleable.StrokeTextView)
val radius = typedArray.getDimensionPixelOffset(
    R.styleable.StrokeTextView_radius, defaultValue
)
val isBottom = typedArray.getBoolean(
    R.styleable.StrokeTextView_isBottomBackground, false
)
typedArray.recycle()  // 必须回收
```

---

## 十、最佳实践

### 10.1 控件选择指南

| 场景 | 推荐控件 |
|------|---------|
| 标题文本 | `PrimaryTextView` |
| 描述文本 | `SecondaryTextView` |
| 按钮/强调 | `AccentTextView` |
| 标签/徽章 | `StrokeTextView` / `AccentBgTextView` |
| 未读提示 | `BadgeView` |
| 促销标签 | `BevelLabelView` |
| 列表内长文 | `ScrollTextView` |

### 10.2 性能优化

```kotlin
// ✅ 在 init 中一次性设置
init {
    setTextColor(color)  // 只执行一次
}

// ❌ 避免在 onDraw 中设置
override fun onDraw(canvas: Canvas) {
    setTextColor(color)  // 每帧都执行，性能差
    super.onDraw(canvas)
}
```

### 10.3 主题适配

```kotlin
// 支持预览模式
if (!isInEditMode) {
    setTextColor(ThemeStore.accentColor(context))
} else {
    setTextColor(context.getCompatColor(R.color.accent))
}
```

### 10.4 代码示例汇总

```xml
<!-- 完整示例布局 -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <!-- 标题 -->
    <io.legado.app.ui.widget.text.PrimaryTextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="书籍详情"
        android:textSize="20sp"
        android:textStyle="bold" />

    <!-- 标签组 -->
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <io.legado.app.ui.widget.text.StrokeTextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:padding="4dp 8dp"
            android:text="玄幻"
            app:radius="4dp" />

        <io.legado.app.ui.widget.text.AccentBgTextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:padding="4dp 8dp"
            android:text="完结"
            app:radius="4dp" />
    </LinearLayout>

    <!-- 简介 -->
    <io.legado.app.ui.widget.text.SecondaryTextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxLines="3"
        android:text="这是一个很长的简介..." />

    <!-- 操作按钮 -->
    <io.legado.app.ui.widget.text.AccentBgTextView
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:gravity="center"
        android:text="开始阅读"
        app:radius="24dp" />
</LinearLayout>
```

---

## 参考资源

- **Android 官方文档**: [Custom View](https://developer.android.com/guide/topics/ui/custom-components)
- **Paint 和 Canvas**: [Canvas and Drawables](https://developer.android.com/guide/topics/graphics/2d-graphics)
- **项目文件**:
  - `app/src/main/java/io/legado/app/ui/widget/text/`
  - `app/src/main/res/values/attrs.xml`

---

> 文档生成时间：2026年3月
> 覆盖控件：14 个自定义 TextView 相关类
