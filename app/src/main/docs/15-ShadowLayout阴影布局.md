# ShadowLayout 阴影布局详解

> 基于 Legado 项目的 ShadowLayout 实现，深入解析 Android 自定义阴影布局的原理、实现和最佳实践。

## 目录

- [一、基础概述](#一基础概述)
- [二、核心原理](#二核心原理)
- [三、完整代码实现](#三完整代码实现)
- [四、位运算控制阴影边界](#四位运算控制阴影边界)
- [五、阴影绘制流程](#五阴影绘制流程)
- [六、自定义属性](#六自定义属性)
- [七、性能优化](#七性能优化)
- [八、使用示例](#八使用示例)
- [九、常见问题](#九常见问题)
- [十、最佳实践](#十最佳实践)

---

## 一、基础概述

### 1.1 为什么要自定义阴影布局

| 方案 | 优点 | 缺点 |
|------|------|------|
| **Material CardView** | 系统原生，使用简单 | 阴影效果固定，定制性弱 |
| **第三方库** | 功能丰富 | 引入依赖，包体积增大 |
| **自定义 ShadowLayout** | 完全可控，无依赖 | 需要维护代码 |

### 1.2 ShadowLayout 特性

- ✅ 完全自定义阴影颜色
- ✅ 可调节阴影模糊半径
- ✅ 支持阴影偏移（x/y 轴）
- ✅ 四边独立控制阴影显示
- ✅ 支持矩形和圆形阴影
- ✅ 无第三方依赖

### 1.3 继承体系

```
android.view.ViewGroup
    └── android.widget.RelativeLayout
            └── ShadowLayout
```

---

## 二、核心原理

### 2.1 Paint.setShadowLayer

Android 提供 `Paint.setShadowLayer()` 方法来绘制阴影：

```kotlin
paint.setShadowLayer(
    radius,     // 阴影模糊半径
    dx,         // x 轴偏移量
    dy,         // y 轴偏移量
    color       // 阴影颜色
)
```

**工作原理**：
```
绘制流程：
1. 绘制阴影层（模糊效果）
2. 偏移指定距离（dx, dy）
3. 在阴影上绘制内容

效果示意：
┌─────────────────────────┐
│         阴影层          │  ← 模糊 + 偏移
│    ┌─────────────┐      │
│    │   内容层    │      │  ← 实际内容
│    └─────────────┘      │
└─────────────────────────┘
```

### 2.2 硬件加速限制

**关键**：阴影效果需要关闭硬件加速

```kotlin
init {
    // 必须关闭硬件加速，否则阴影不显示
    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
}
```

**原因**：
- 硬件加速不支持 `setShadowLayer` 的模糊效果
- `LAYER_TYPE_SOFTWARE` 使用 CPU 渲染，支持所有 Paint 效果

### 2.3 为什么继承 RelativeLayout

```kotlin
class ShadowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RelativeLayout(context, attrs)  // 继承 RelativeLayout
```

**优势**：
- RelativeLayout 灵活性强
- 支持复杂的子 View 布局规则
- 性能优于 LinearLayout（复杂布局时）

---

## 三、完整代码实现

### 3.1 核心代码（185 行）

```kotlin
class ShadowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RelativeLayout(context, attrs) {

    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mRectF = RectF()

    // 阴影配置参数
    private var mShadowColor = Color.TRANSPARENT      // 阴影颜色
    private var mShadowRadius = 0f                    // 阴影模糊半径
    private var mShadowDx = 0f                        // x 轴偏移
    private var mShadowDy = 0f                        // y 轴偏移
    private var mShadowSide = ALL                     // 阴影显示边界
    private var mShadowShape = SHAPE_RECTANGLE        // 阴影形状

    init {
        // 1. 关闭硬件加速（关键！）
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        
        // 2. 启用自定义绘制
        setWillNotDraw(false)
        
        // 3. 读取自定义属性
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ShadowLayout)
        mShadowColor = typedArray.getColor(
            R.styleable.ShadowLayout_shadowColor,
            context.getCompatColor(android.R.color.black)
        )
        mShadowRadius = typedArray.getDimension(
            R.styleable.ShadowLayout_shadowRadius, 
            dip2px(0f)
        )
        mShadowDx = typedArray.getDimension(
            R.styleable.ShadowLayout_shadowDx, 
            dip2px(0f)
        )
        mShadowDy = typedArray.getDimension(
            R.styleable.ShadowLayout_shadowDy, 
            dip2px(0f)
        )
        mShadowSide = typedArray.getInt(
            R.styleable.ShadowLayout_shadowSide, 
            ALL
        )
        mShadowShape = typedArray.getInt(
            R.styleable.ShadowLayout_shadowShape,
            SHAPE_RECTANGLE
        )
        typedArray.recycle()

        setUpShadowPaint()
    }

    /**
     * 测量阶段：计算阴影区域和 Padding
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        
        // 计算阴影影响范围（半径 + 额外缓冲）
        val effect = mShadowRadius + dip2px(5f)
        
        var rectLeft = 0f
        var rectTop = 0f
        var rectRight = measuredWidth.toFloat()
        var rectBottom = measuredHeight.toFloat()
        
        var paddingLeft = 0
        var paddingTop = 0
        var paddingRight = 0
        var paddingBottom = 0

        // 根据阴影边界位运算结果，调整绘制区域和 padding
        if (mShadowSide and LEFT == LEFT) {
            rectLeft = effect
            paddingLeft = effect.toInt()
        }
        if (mShadowSide and TOP == TOP) {
            rectTop = effect
            paddingTop = effect.toInt()
        }
        if (mShadowSide and RIGHT == RIGHT) {
            rectRight = measuredWidth - effect
            paddingRight = effect.toInt()
        }
        if (mShadowSide and BOTTOM == BOTTOM) {
            rectBottom = measuredHeight - effect
            paddingBottom = effect.toInt()
        }

        // 处理偏移量
        if (mShadowDy != 0.0f) {
            rectBottom -= mShadowDy
            paddingBottom += mShadowDy.toInt()
        }
        if (mShadowDx != 0.0f) {
            rectRight -= mShadowDx
            paddingRight += mShadowDx.toInt()
        }

        // 设置阴影绘制区域
        mRectF.set(rectLeft, rectTop, rectRight, rectBottom)
        
        // 设置 padding，防止阴影被裁剪
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    /**
     * 绘制阶段：绘制阴影
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        setUpShadowPaint()
        
        when (mShadowShape) {
            SHAPE_RECTANGLE -> {
                // 绘制矩形阴影
                canvas.drawRect(mRectF, mPaint)
            }
            SHAPE_OVAL -> {
                // 绘制圆形阴影
                canvas.drawCircle(
                    mRectF.centerX(),
                    mRectF.centerY(),
                    min(mRectF.width(), mRectF.height()) / 2,
                    mPaint
                )
            }
        }
    }

    /**
     * 配置阴影画笔
     */
    private fun setUpShadowPaint() {
        mPaint.reset()
        mPaint.isAntiAlias = true
        mPaint.color = Color.TRANSPARENT  // 内容透明，只显示阴影
        mPaint.setShadowLayer(
            mShadowRadius, 
            mShadowDx, 
            mShadowDy, 
            mShadowColor
        )
    }

    /**
     * dp 转 px
     */
    private fun dip2px(dpValue: Float): Float {
        return dpValue * context.resources.displayMetrics.density + 0.5f
    }

    companion object {
        // 阴影边界常量（位运算）
        const val ALL = 0x1111
        const val LEFT = 0x0001
        const val TOP = 0x0010
        const val RIGHT = 0x0100
        const val BOTTOM = 0x1000
        
        // 阴影形状常量
        const val SHAPE_RECTANGLE = 0x0001
        const val SHAPE_OVAL = 0x0010
    }
}
```

---

## 四、位运算控制阴影边界

### 4.1 位运算原理

使用位运算（bitmask）可以高效地控制多个布尔选项：

```kotlin
companion object {
    const val ALL = 0x1111      // 二进制：0001 0001 0001 0001
    const val LEFT = 0x0001     // 二进制：0000 0000 0000 0001
    const val TOP = 0x0010      // 二进制：0000 0000 0001 0000
    const val RIGHT = 0x0100    // 二进制：0000 0001 0000 0000
    const val BOTTOM = 0x1000   // 二进制：0001 0000 0000 0000
}
```

### 4.2 位运算操作

```kotlin
// 1. 检查是否包含某个方向
if (mShadowSide and LEFT == LEFT) {
    // 包含左边阴影
}

// 2. 组合多个方向
val topBottom = TOP or BOTTOM  // 上下阴影
val leftRight = LEFT or RIGHT  // 左右阴影

// 3. 添加方向
mShadowSide = mShadowSide or LEFT  // 添加左边阴影

// 4. 移除方向
mShadowSide = mShadowSide and LEFT.inv()  // 移除左边阴影
```

### 4.3 可视化理解

```
位运算示意：

mShadowSide = 0x1111 (ALL)
二进制：0001 0001 0001 0001
             │    │    │    │
            左   上   右   下

检查 LEFT：
  0001 0001 0001 0001  (mShadowSide)
& 0000 0000 0000 0001  (LEFT)
= 0000 0000 0000 0001  != 0  ✓ 包含 LEFT

检查 TOP：
  0001 0001 0001 0001
& 0000 0000 0001 0000  (TOP)
= 0000 0000 0001 0000  != 0  ✓ 包含 TOP
```

### 4.4 实际应用

```kotlin
// 只显示底部阴影
mShadowSide = BOTTOM

// 显示上下阴影
mShadowSide = TOP or BOTTOM

// 显示四边阴影
mShadowSide = ALL
```

---

## 五、阴影绘制流程

### 5.1 完整绘制流程图

```
┌─────────────────────────────────────┐
│           构造函数                   │
│  1. 关闭硬件加速                     │
│  2. 读取自定义属性                   │
│  3. 初始化 Paint                     │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│         onMeasure()                 │
│  1. 计算阴影影响范围                  │
│  2. 确定绘制区域（mRectF）            │
│  3. 设置 Padding                     │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│          onDraw()                   │
│  1. 配置阴影画笔（setShadowLayer）    │
│  2. 绘制阴影形状（Rect/Circle）       │
│  3. 系统绘制子 View                  │
└─────────────────────────────────────┘
```

### 5.2 测量阶段详解

```kotlin
override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    
    // 计算阴影需要的最小空间
    val effect = mShadowRadius + dip2px(5f)
    
    // 根据显示边界，为阴影预留空间
    if (mShadowSide and LEFT == LEFT) {
        rectLeft = effect           // 绘制区域右移
        paddingLeft = effect.toInt() // 左 padding
    }
    // ... 其他方向同理
    
    // 设置绘制区域
    mRectF.set(rectLeft, rectTop, rectRight, rectBottom)
    
    // 设置 padding，防止阴影被父布局裁剪
    setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
}
```

**关键点**：
- `padding` 是为了给阴影留出空间
- 子 View 会在 padding 区域内布局
- 阴影在 padding 区域绘制

### 5.3 绘制阶段详解

```kotlin
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)  // 先调用父类绘制
    
    // 配置画笔
    mPaint.setShadowLayer(radius, dx, dy, color)
    
    // 绘制阴影（只绘制阴影，内容是透明的）
    canvas.drawRect(mRectF, mPaint)
    
    // 注意：子 View 的绘制在 super.onDraw 中已经处理
}
```

**绘制顺序**：
1. 先绘制阴影（在底层）
2. 再绘制子 View（在上层）
3. 子 View 遮挡部分阴影，形成立体效果

---

## 六、自定义属性

### 6.1 attrs.xml 定义

```xml
<declare-styleable name="ShadowLayout">
    <!-- 阴影颜色 -->
    <attr name="shadowColor" format="color" />
    
    <!-- 阴影模糊半径 -->
    <attr name="shadowRadius" format="dimension" />
    
    <!-- x 轴偏移量 -->
    <attr name="shadowDx" format="dimension" />
    
    <!-- y 轴偏移量 -->
    <attr name="shadowDy" format="dimension" />
    
    <!-- 阴影显示边界 -->
    <attr name="shadowSide" format="enum">
        <enum name="all" value="4369" />       <!-- 0x1111 -->
        <enum name="left" value="1" />         <!-- 0x0001 -->
        <enum name="top" value="16" />         <!-- 0x0010 -->
        <enum name="right" value="256" />      <!-- 0x0100 -->
        <enum name="bottom" value="4096" />    <!-- 0x1000 -->
    </attr>
    
    <!-- 阴影形状 -->
    <attr name="shadowShape" format="enum">
        <enum name="rectangle" value="1" />
        <enum name="oval" value="16" />
    </attr>
</declare-styleable>
```

### 6.2 属性读取

```kotlin
init {
    val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ShadowLayout)
    
    // 读取颜色
    mShadowColor = typedArray.getColor(
        R.styleable.ShadowLayout_shadowColor,
        Color.BLACK
    )
    
    // 读取尺寸（返回的是 px 值）
    mShadowRadius = typedArray.getDimension(
        R.styleable.ShadowLayout_shadowRadius,
        0f
    )
    
    // 读取枚举值（返回的是 int）
    mShadowSide = typedArray.getInt(
        R.styleable.ShadowLayout_shadowSide,
        ALL
    )
    
    typedArray.recycle()
}
```

---

## 七、性能优化

### 7.1 关闭硬件加速的影响

```kotlin
// 必须关闭硬件加速
setLayerType(View.LAYER_TYPE_SOFTWARE, null)
```

**影响**：
- ✅ 支持所有 Paint 效果
- ❌ 增加 CPU 负担
- ❌ 可能降低帧率

**优化建议**：
- 只在需要的 View 上关闭硬件加速
- 避免在大面积布局上使用阴影
- 考虑使用 CardView 替代（简单场景）

### 7.2 抗锯齿

```kotlin
private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
// 或
mPaint.isAntiAlias = true
```

**作用**：阴影边缘更平滑，减少锯齿

### 7.3 减少重绘

```kotlin
// 动态修改属性时，按需刷新
fun setShadowColor(color: Int) {
    mShadowColor = color
    requestLayout()      // 布局可能改变
    postInvalidate()     // 重绘
}
```

**何时使用**：
- `requestLayout()`：尺寸或位置可能改变
- `invalidate()`：仅内容改变
- `postInvalidate()`：异步刷新

---

## 八、使用示例

### 8.1 基础使用

```xml
<!-- 基础阴影卡片 -->
<io.legado.app.ui.widget.ShadowLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="16dp"
    app:shadowColor="#20000000"
    app:shadowRadius="8dp"
    app:shadowDx="0dp"
    app:shadowDy="4dp"
    app:shadowSide="all">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:text="带阴影的内容"
        android:background="@color/white" />
</io.legado.app.ui.widget.ShadowLayout>
```

### 8.2 底部阴影（Material 风格）

```xml
<io.legado.app.ui.widget.ShadowLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:shadowColor="#30000000"
    app:shadowRadius="12dp"
    app:shadowDy="8dp"
    app:shadowSide="bottom">  <!-- 只显示底部阴影 -->

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">
        
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="底部阴影卡片" />
    </LinearLayout>
</io.legado.app.ui.widget.ShadowLayout>
```

### 8.3 圆形阴影按钮

```xml
<io.legado.app.ui.widget.ShadowLayout
    android:layout_width="64dp"
    android:layout_height="64dp"
    app:shadowColor="#40000000"
    app:shadowRadius="16dp"
    app:shadowShape="oval">  <!-- 圆形阴影 -->

    <ImageButton
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:src="@drawable/ic_add"
        android:background="@drawable/circle_background" />
</io.legado.app.ui.widget.ShadowLayout>
```

### 8.4 代码动态设置

```kotlin
val shadowLayout = findViewById<ShadowLayout>(R.id.shadow_layout)

// 修改阴影颜色
shadowLayout.setShadowColor(Color.parseColor("#20000000"))

// 修改阴影半径
shadowLayout.setShadowRadius(16f)

// 同时修改多个属性
shadowLayout.apply {
    setShadowColor(context.getCompatColor(R.color.shadow_color))
    setShadowRadius(resources.getDimension(R.dimen.shadow_radius))
}
```

---

## 九、常见问题

### 9.1 阴影不显示

**原因 1**：没有关闭硬件加速
```kotlin
// 正确
setLayerType(View.LAYER_TYPE_SOFTWARE, null)

// 错误：默认硬件加速，阴影不显示
```

**原因 2**：没有设置 padding
```kotlin
// 必须在 onMeasure 中设置 padding
setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
```

### 9.2 阴影被裁剪

**原因**：父布局没有留出足够空间

**解决**：
```xml
<!-- 父布局留出足够的 margin -->
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="16dp">  <!-- 给阴影留出空间 -->
    
    <io.legado.app.ui.widget.ShadowLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:shadowRadius="8dp">
        
        <!-- 内容 -->
    </io.legado.app.ui.widget.ShadowLayout>
</FrameLayout>
```

### 9.3 子 View 没有背景

**现象**：阴影显示，但子 View 透明

**解决**：给子 View 添加背景
```xml
<io.legado.app.ui.widget.ShadowLayout ...>
    <TextView
        android:background="@color/white"  <!-- 必须添加背景 -->
        ... />
</io.legado.app.ui.widget.ShadowLayout>
```

---

## 十、最佳实践

### 10.1 何时使用 ShadowLayout

| 场景 | 推荐方案 |
|------|---------|
| 简单卡片阴影 | CardView（性能更好） |
| 自定义阴影颜色/形状 | ShadowLayout |
| 只需底部阴影 | ShadowLayout |
| 动态阴影效果 | ShadowLayout |

### 10.2 性能建议

```kotlin
// 1. 避免在列表中大量使用
// 如果必须使用，考虑复用

// 2. 合理设置阴影半径
// 过大的 radius 会影响性能
app:shadowRadius="8dp"  // 推荐范围：4dp ~ 16dp

// 3. 使用半透明白色/黑色阴影
// 与背景融合更好
app:shadowColor="#20000000"  // 12.5% 透明度黑色
```

### 10.3 设计规范

```xml
<!-- Material Design 风格阴影 -->
<io.legado.app.ui.widget.ShadowLayout
    app:shadowColor="#14000000"   <!-- 8% 透明度 -->
    app:shadowRadius="4dp"
    app:shadowDy="2dp" />

<!-- 悬浮按钮风格 -->
<io.legado.app.ui.widget.ShadowLayout
    app:shadowColor="#40000000"   <!-- 25% 透明度 -->
    app:shadowRadius="12dp"
    app:shadowDy="6dp" />
```

---

## 参考资源

- **Android 官方文档**: [Hardware Acceleration](https://developer.android.com/guide/topics/graphics/hardware-accel)
- **Paint 文档**: [Paint.setShadowLayer](https://developer.android.com/reference/android/graphics/Paint#setShadowLayer(float,%20float,%20float,%20int))
- **项目文件**: `app/src/main/java/io/legado/app/ui/widget/ShadowLayout.kt`

---

> 文档生成时间：2026年3月
> 代码行数：185 行
> 核心知识点：Paint.setShadowLayer、位运算控制、硬件加速、自定义属性
