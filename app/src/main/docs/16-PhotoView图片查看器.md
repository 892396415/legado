# PhotoView 图片查看器详解

> 基于 Legado 项目的 PhotoView 实现，深入解析图片手势缩放、拖拽、旋转、惯性滑动等高级交互技术。

## 目录

- [一、基础概述](#一基础概述)
- [二、Matrix 矩阵变换](#二matrix-矩阵变换)
- [三、手势检测系统](#三手势检测系统)
- [四、核心属性与状态](#四核心属性与状态)
- [五、初始化与测量](#五初始化与测量)
- [六、动画系统 Transform](#六动画系统-transform)
- [七、手势处理详解](#七手势处理详解)
- [八、边界检测与回弹](#八边界检测与回弹)
- [九、过渡动画实现](#九过渡动画实现)
- [十、最佳实践](#十最佳实践)

---

## 一、基础概述

### 1.1 PhotoView 特性

| 功能 | 实现方式 |
|------|---------|
| **双击缩放** | GestureDetector.onDoubleTap |
| **手势缩放** | ScaleGestureDetector |
| **拖拽平移** | GestureDetector.onScroll |
| **惯性滑动** | OverScroller + GestureDetector.onFling |
| **旋转** | RotateGestureDetector |
| **动画过渡** | Transform 类管理所有动画 |
| **边界回弹** | onUp() 自动检测并回弹 |

### 1.2 继承体系

```
android.widget.ImageView
    └── androidx.appcompat.widget.AppCompatImageView
            └── PhotoView
```

### 1.3 核心组件

```kotlin
class PhotoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    // 矩阵变换
    private val mBaseMatrix: Matrix = Matrix()      // 基础矩阵
    private val mAnimMatrix: Matrix = Matrix()      // 动画矩阵
    private val mSynthesisMatrix: Matrix = Matrix() // 合成矩阵
    
    // 手势检测器
    private val mRotateDetector: RotateGestureDetector   // 旋转
    private val mDetector: GestureDetector              // 点击、双击、滑动
    private val mScaleDetector: ScaleGestureDetector    // 缩放
    
    // 动画管理
    private val mTranslate: Transform = Transform()
}
```

---

## 二、Matrix 矩阵变换

### 2.1 Matrix 基础

Matrix 是 Android 中用于图形变换的 3x3 矩阵：

```
[ MSCALE_X  MSKEW_X   MTRANS_X ]
[ MSKEW_Y   MSCALE_Y  MTRANS_Y ]
[ MPERSP_0  MPERSP_1  MPERSP_2 ]

简化理解：
[ 缩放X   倾斜X   位移X ]
[ 倾斜Y   缩放Y   位移Y ]
[ 透视0   透视1   透视2 ]
```

### 2.2 PhotoView 中的矩阵分层

```kotlin
class PhotoView {
    // 三层矩阵架构
    private val mBaseMatrix: Matrix = Matrix()   // 基础：初始布局、适配
    private val mAnimMatrix: Matrix = Matrix()   // 动画：手势操作产生的变换
    private val mSynthesisMatrix: Matrix = Matrix() // 合成：Base + Anim

    fun executeTranslate() {
        // 合成矩阵 = 基础矩阵 × 动画矩阵
        mSynthesisMatrix.set(mBaseMatrix)
        mSynthesisMatrix.postConcat(mAnimMatrix)
        imageMatrix = mSynthesisMatrix
    }
}
```

**分层优势**：
- `mBaseMatrix`：保持不变，存储初始状态
- `mAnimMatrix`：动态变化，响应手势
- 合成后应用到 ImageView

### 2.3 矩阵操作常用方法

```kotlin
// 1. 平移
matrix.postTranslate(dx, dy)

// 2. 缩放（以某点为中心）
matrix.postScale(scaleX, scaleY, centerX, centerY)

// 3. 旋转（以某点为中心）
matrix.postRotate(degrees, centerX, centerY)

// 4. 矩阵乘法（右乘）
matrix.postConcat(otherMatrix)

// 5. 应用矩阵到矩形
matrix.mapRect(outputRect, inputRect)
```

### 2.4 坐标变换流程

```
原始图片坐标系          矩阵变换           屏幕显示坐标系
┌──────────────┐                     ┌──────────────┐
│              │                     │              │
│   图片原点    │  ── Matrix.map ──>  │  屏幕位置     │
│   (0,0)      │                     │              │
│              │                     │              │
└──────────────┘                     └──────────────┘

实际应用：
1. 手指在屏幕滑动 (dx, dy)
2. 转换为矩阵变换 matrix.postTranslate(-dx, -dy)
3. 应用到图片，图片反向移动，产生跟随手指效果
```

---

## 三、手势检测系统

### 3.1 三种手势检测器

```kotlin
init {
    // 1. 旋转手势检测
    mRotateDetector = RotateGestureDetector(mRotateListener)
    
    // 2. 点击、双击、滑动、惯性滑动检测
    mDetector = GestureDetector(context, mGestureListener)
    
    // 3. 缩放手势检测
    mScaleDetector = ScaleGestureDetector(context, mScaleListener)
}
```

### 3.2 手势分发

```kotlin
override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    if (!isEnable) return super.dispatchTouchEvent(event)
    
    val action = event.actionMasked
    if (event.pointerCount >= 2) hasMultiTouch = true
    
    // 1. 处理点击、双击、滑动
    mDetector.onTouchEvent(event)
    
    // 2. 处理旋转（如果启用）
    if (isRotateEnable) {
        mRotateDetector.onTouchEvent(event)
    }
    
    // 3. 处理缩放
    mScaleDetector.onTouchEvent(event)
    
    // 4. 手指抬起时处理边界回弹
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
        onUp()
    }
    
    return true
}
```

### 3.3 各检测器职责

| 检测器 | 功能 | 触发条件 |
|--------|------|----------|
| GestureDetector | 单击、双击、长按、滑动、惯性滑动 | 单指/双指 |
| ScaleGestureDetector | 双指缩放 | 双指 |
| RotateGestureDetector | 双指旋转 | 双指 |

---

## 四、核心属性与状态

### 4.1 状态标记

```kotlin
class PhotoView {
    // 功能开关
    var isEnable = true           // 是否启用手势
    var isRotateEnable = false    // 是否启用旋转
    
    // 状态标记
    private var hasMultiTouch = false    // 是否多指触摸
    private var hasDrawable = false      // 是否有图片
    private var isKnowSize = false       // 是否已知尺寸
    private var isZoonUp = false         // 是否处于放大状态
    private var hasOverTranslate = false // 是否超出边界
    
    // 变换参数
    private var mScale = 1.0f      // 当前缩放比例
    private var mDegrees = 0f      // 当前旋转角度
    private var mTranslateX = 0    // X轴位移
    private var mTranslateY = 0    // Y轴位移
}
```

### 4.2 矩形区域定义

```kotlin
class PhotoView {
    // 各个矩形区域
    private val mWidgetRect = RectF()   // 控件区域（屏幕上的View）
    private val mBaseRect = RectF()     // 图片基础区域（初始适配后）
    private val mImgRect = RectF()      // 图片当前显示区域
    private val mTmpRect = RectF()      // 临时计算用
}
```

**区域关系**：
```
mWidgetRect: View 在屏幕上的矩形（固定）
    └── mBaseRect: 图片初始适配后的矩形（基准）
            └── mImgRect: 经过手势变换后的当前矩形（动态）
```

### 4.3 常量配置

```kotlin
companion object {
    const val MIN_ROTATE = 35           // 最小旋转触发角度
    const val ANIMA_DURING = 340        // 动画持续时间（毫秒）
    const val MAX_SCALE = 2.5f          // 最大缩放比例
}

// 可配置参数
var MAX_OVER_SCROLL = 0               // 最大越界距离
var MAX_FLING_OVER_SCROLL = 0         // 惯性滑动越界距离
var MAX_OVER_RESISTANCE = 0           // 越界阻力
var MAX_ANIM_FROM_WAITE = 500         // 动画等待超时时间
```

---

## 五、初始化与测量

### 5.1 初始化基础矩阵

```kotlin
private fun initBase() {
    if (!hasDrawable || !isKnowSize) return
    
    mBaseMatrix.reset()
    mAnimMatrix.reset()
    isZoonUp = false
    
    val img = drawable
    val imgW = getDrawableWidth(img)
    val imgH = getDrawableHeight(img)
    
    // 1. 设置图片原始矩形
    mBaseRect.set(0f, 0f, imgW.toFloat(), imgH.toFloat())
    
    // 2. 居中平移
    val tx = (width - imgW) / 2
    val ty = (height - imgH) / 2
    mBaseMatrix.postTranslate(tx.toFloat(), ty.toFloat())
    
    // 3. 缩放适配（默认不超出屏幕）
    var sx = 1f
    var sy = 1f
    if (imgW > width) sx = width.toFloat() / imgW
    if (imgH > height) sy = height.toFloat() / imgH
    val scale = min(sx, sy)
    
    mBaseMatrix.postScale(scale, scale, mScreenCenter.x, mScreenCenter.y)
    mBaseMatrix.mapRect(mBaseRect)
    
    // 4. 记录半宽高（用于后续计算）
    mHalfBaseRectWidth = mBaseRect.width() / 2
    mHalfBaseRectHeight = mBaseRect.height() / 2
    
    // 5. 设置中心和缩放中心
    mScaleCenter.set(mScreenCenter)
    mRotateCenter.set(mScreenCenter)
    
    // 6. 应用变换
    executeTranslate()
    
    // 7. 根据 ScaleType 进一步调整
    when (mScaleType) {
        ScaleType.CENTER -> initCenter()
        ScaleType.CENTER_CROP -> initCenterCrop()
        ScaleType.CENTER_INSIDE -> initCenterInside()
        // ... 其他模式
    }
    
    isInit = true
}
```

### 5.2 ScaleType 处理

```kotlin
// CENTER_CROP: 保持比例填充整个控件（可能裁剪）
private fun initCenterCrop() {
    if (mImgRect.width() < mWidgetRect.width() || 
        mImgRect.height() < mWidgetRect.height()) {
        val scaleX = mWidgetRect.width() / mImgRect.width()
        val scaleY = mWidgetRect.height() / mImgRect.height()
        mScale = max(scaleX, scaleY)
        mAnimMatrix.postScale(mScale, mScale, mScreenCenter.x, mScreenCenter.y)
        executeTranslate()
        resetBase()
    }
}

// CENTER_INSIDE: 保持比例完整显示（可能留白）
private fun initCenterInside() {
    if (mImgRect.width() > mWidgetRect.width() || 
        mImgRect.height() > mWidgetRect.height()) {
        val scaleX = mWidgetRect.width() / mImgRect.width()
        val scaleY = mWidgetRect.height() / mImgRect.height()
        mScale = min(scaleX, scaleY)
        mAnimMatrix.postScale(mScale, mScale, mScreenCenter.x, mScreenCenter.y)
        executeTranslate()
        resetBase()
    }
}
```

### 5.3 自定义测量

```kotlin
override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (!hasDrawable) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        return
    }
    
    val d = drawable
    val drawableW = getDrawableWidth(d)
    val drawableH = getDrawableHeight(d)
    
    val pWidth = MeasureSpec.getSize(widthMeasureSpec)
    val pHeight = MeasureSpec.getSize(heightMeasureSpec)
    val widthMode = MeasureSpec.getMode(widthMeasureSpec)
    val heightMode = MeasureSpec.getMode(heightMeasureSpec)
    
    // 根据 MeasureSpec 计算实际尺寸
    var width = calculateWidth(widthMode, pWidth, drawableW)
    var height = calculateHeight(heightMode, pHeight, drawableH)
    
    // adjustViewBounds 处理
    if (mAdjustViewBounds) {
        val hScale = height.toFloat() / drawableH
        val wScale = width.toFloat() / drawableW
        val scale = min(hScale, wScale)
        width = (drawableW * scale).toInt()
        height = (drawableH * scale).toInt()
    }
    
    setMeasuredDimension(width, height)
}
```

---

## 六、动画系统 Transform

### 6.1 Transform 类结构

```kotlin
private inner class Transform : Runnable {
    var isRunning = false
    
    // 各种动画的 Scroller
    var mTranslateScroller: OverScroller    // 平移动画
    var mScaleScroller: Scroller            // 缩放动画
    var mFlingScroller: OverScroller        // 惯性滑动
    var mRotateScroller: Scroller           // 旋转动画
    var mClipScroller: Scroller             // 裁剪动画
    
    // 动画配置
    var mInterpolatorProxy = InterpolatorProxy()
}
```

### 6.2 启动动画

```kotlin
// 平移动画
fun withTranslate(startX: Int, startY: Int, deltaX: Int, deltaY: Int) {
    mLastTranslateX = 0
    mLastTranslateY = 0
    mTranslateScroller.startScroll(0, 0, deltaX, deltaY, mAnimaDuring)
}

// 缩放动画
fun withScale(from: Float, to: Float) {
    // 使用整型存储浮点值（精度：1/10000）
    mScaleScroller.startScroll(
        (from * 10000).toInt(), 0,
        ((to - from) * 10000).toInt(), 0,
        mAnimaDuring
    )
}

// 惯性滑动
fun withFling(velocityX: Float, velocityY: Float) {
    mFlingScroller.fling(
        startX, startY,
        velocityX.toInt(), velocityY.toInt(),
        minX, maxX, minY, maxY,
        overX, overY
    )
}

// 启动所有动画
fun start() {
    isRunning = true
    postExecute()
}
```

### 6.3 动画执行循环

```kotlin
override fun run() {
    var endAnima = true
    
    // 1. 处理缩放动画
    if (mScaleScroller.computeScrollOffset()) {
        mScale = mScaleScroller.currX / 10000f
        endAnima = false
    }
    
    // 2. 处理平移动画
    if (mTranslateScroller.computeScrollOffset()) {
        val tx = mTranslateScroller.currX - mLastTranslateX
        val ty = mTranslateScroller.currY - mLastTranslateY
        mTranslateX += tx
        mTranslateY += ty
        mLastTranslateX = mTranslateScroller.currX
        mLastTranslateY = mTranslateScroller.currY
        endAnima = false
    }
    
    // 3. 处理惯性滑动
    if (mFlingScroller.computeScrollOffset()) {
        val x = mFlingScroller.currX - mLastFlingX
        val y = mFlingScroller.currY - mLastFlingY
        mLastFlingX = mFlingScroller.currX
        mLastFlingY = mFlingScroller.currY
        mTranslateX += x
        mTranslateY += y
        endAnima = false
    }
    
    // 4. 处理旋转动画
    if (mRotateScroller.computeScrollOffset()) {
        mDegrees = mRotateScroller.currX.toFloat()
        endAnima = false
    }
    
    if (!endAnima) {
        // 应用所有变换
        applyAnima()
        // 继续下一帧
        postExecute()
    } else {
        // 动画结束
        isRunning = false
        // 边界修正
        fixBorderGap()
        mCompleteCallBack?.run()
    }
}

private fun applyAnima() {
    mAnimMatrix.reset()
    mAnimMatrix.postTranslate(-mBaseRect.left, -mBaseRect.top)
    mAnimMatrix.postTranslate(mRotateCenter.x, mRotateCenter.y)
    mAnimMatrix.postTranslate(-mHalfBaseRectWidth, -mHalfBaseRectHeight)
    mAnimMatrix.postRotate(mDegrees, mRotateCenter.x, mRotateCenter.y)
    mAnimMatrix.postScale(mScale, mScale, mScaleCenter.x, mScaleCenter.y)
    mAnimMatrix.postTranslate(mTranslateX.toFloat(), mTranslateY.toFloat())
    executeTranslate()
}
```

### 6.4 动画流程图

```
开始动画
    │
    ▼
初始化 Scroller
    │
    ▼
调用 start()
    │
    ▼
执行 run()
    │
    ├── 计算当前进度 (computeScrollOffset)
    │
    ├── 更新变换参数 (Scale, Translate, Rotate)
    │
    ├── applyAnima() 应用变换
    │
    └── 动画完成？
            │
            ├── 否 → postDelayed(run, 16ms) → 继续
            │
            └── 是 → 边界修正 → 回调
```

---

## 七、手势处理详解

### 7.1 双击缩放

```kotlin
inner class GestureListener : SimpleOnGestureListener() {
    
    override fun onDoubleTap(e: MotionEvent): Boolean {
        mTranslate.stop()
        
        val from: Float
        val to: Float
        
        // 计算当前图片中心
        val imgCx = mImgRect.left + mImgRect.width() / 2
        val imgCy = mImgRect.top + mImgRect.height() / 2
        mScaleCenter.set(imgCx, imgCy)
        mRotateCenter.set(imgCx, imgCy)
        mTranslateX = 0
        mTranslateY = 0
        
        if (isZoonUp) {
            // 当前放大状态 → 恢复原始大小
            from = mScale
            to = 1f
        } else {
            // 当前原始大小 → 放大到 MAX_SCALE
            from = mScale
            to = mMaxScale
            mScaleCenter.set(e.x, e.y)  // 以双击位置为中心
        }
        
        // 计算目标矩形并回弹
        mTmpMatrix.reset()
        mTmpMatrix.postScale(to, to, mScaleCenter.x, mScaleCenter.y)
        mTmpMatrix.mapRect(mTmpRect, mBaseRect)
        doTranslateReset(mTmpRect)
        
        isZoonUp = !isZoonUp
        mTranslate.withScale(from, to)
        mTranslate.start()
        
        return false
    }
}
```

### 7.2 双指缩放

```kotlin
inner class ScaleGestureListener : OnScaleGestureListener {
    
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val scaleFactor = detector.scaleFactor
        if (scaleFactor.isNaN() || scaleFactor.isInfinite()) return false
        
        mScale *= scaleFactor
        
        // 以双指中心为缩放中心
        mAnimMatrix.postScale(
            scaleFactor,
            scaleFactor,
            detector.focusX,
            detector.focusY
        )
        executeTranslate()
        return true
    }
    
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return true
    }
    
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
}
```

### 7.3 拖拽滑动

```kotlin
override fun onScroll(
    e1: MotionEvent?,
    e2: MotionEvent,
    distanceX: Float,
    distanceY: Float
): Boolean {
    var x = distanceX
    var y = distanceY
    
    if (mTranslate.isRunning) {
        mTranslate.stop()
    }
    
    // 水平滑动处理
    if (canScrollHorizontallySelf(x)) {
        // 边界限制
        if (x < 0 && mImgRect.left - x > mWidgetRect.left) {
            x = mImgRect.left
        }
        if (x > 0 && mImgRect.right - x < mWidgetRect.right) {
            x = mImgRect.right - mWidgetRect.right
        }
        mAnimMatrix.postTranslate(-x, 0f)
        mTranslateX -= x.toInt()
    } else if (imgLargeWidth || hasMultiTouch) {
        // 超出边界时添加阻力
        x = resistanceScrollByX(overScroll, x)
        mAnimMatrix.postTranslate(-x, 0f)
        hasOverTranslate = true
    }
    
    // 垂直滑动（类似处理）
    // ...
    
    executeTranslate()
    return true
}
```

### 7.4 惯性滑动

```kotlin
override fun onFling(
    e1: MotionEvent?,
    e2: MotionEvent,
    velocityX: Float,
    velocityY: Float
): Boolean {
    if (hasMultiTouch) return false
    if (!imgLargeWidth && !imgLargeHeight) return false
    if (mTranslate.isRunning) return false
    
    var vx = velocityX
    var vy = velocityY
    
    // 如果图片在边界内，不触发惯性滑动
    if (mImgRect.left >= mWidgetRect.left || mImgRect.right <= mWidgetRect.right) {
        vx = 0f
    }
    if (mImgRect.top >= mWidgetRect.top || mImgRect.bottom <= mWidgetRect.bottom) {
        vy = 0f
    }
    
    // 先回弹到边界内
    doTranslateReset(mImgRect)
    
    // 启动惯性滑动动画
    mTranslate.withFling(vx, vy)
    mTranslate.start()
    
    return super.onFling(e1, e2, velocityX, velocityY)
}
```

### 7.5 旋转

```kotlin
inner class RotateListener : OnRotateListener {
    override fun onRotate(degrees: Float, focusX: Float, focusY: Float) {
        mRotateFlag += degrees
        
        if (canRotate) {
            // 直接旋转
            mDegrees += degrees
            mAnimMatrix.postRotate(degrees, focusX, focusY)
        } else {
            // 累积角度，超过阈值才启用旋转
            if (abs(mRotateFlag) >= mMinRotate) {
                canRotate = true
                mRotateFlag = 0f
            }
        }
    }
}
```

---

## 八、边界检测与回弹

### 8.1 手指抬起处理

```kotlin
private fun onUp() {
    if (mTranslate.isRunning) return
    
    // 1. 处理旋转回正
    if (canRotate || mDegrees % 90 != 0f) {
        var toDegrees = (mDegrees / 90).toInt() * 90f
        val remainder = mDegrees % 90
        if (remainder > 45) toDegrees += 90f
        else if (remainder < -45) toDegrees -= 90f
        
        mTranslate.withRotate(mDegrees.toInt(), toDegrees.toInt())
        mDegrees = toDegrees
    }
    
    // 2. 处理缩放回弹
    var scale = mScale
    if (mScale < 1) {
        scale = 1f
        mTranslate.withScale(mScale, 1f)
    } else if (mScale > mMaxScale) {
        scale = mMaxScale
        mTranslate.withScale(mScale, mMaxScale)
    }
    
    // 3. 计算中心点
    val cx = mImgRect.left + mImgRect.width() / 2
    val cy = mImgRect.top + mImgRect.height() / 2
    mScaleCenter.set(cx, cy)
    mRotateCenter.set(cx, cy)
    
    // 4. 重置位移计数
    mTranslateX = 0
    mTranslateY = 0
    
    // 5. 计算目标矩形
    mTmpMatrix.reset()
    mTmpMatrix.postScale(scale, scale, cx, cy)
    mTmpMatrix.postRotate(mDegrees, cx, cy)
    mTmpMatrix.mapRect(mTmpRect, mBaseRect)
    
    // 6. 执行回弹
    doTranslateReset(mTmpRect)
    mTranslate.start()
}
```

### 8.2 回弹计算

```kotlin
private fun doTranslateReset(imgRect: RectF) {
    var tx = 0
    var ty = 0
    
    // 水平方向回弹
    if (imgRect.width() <= mWidgetRect.width()) {
        // 图片小于控件，居中显示
        if (!isImageCenterWidth(imgRect)) {
            tx = (-((mWidgetRect.width() - imgRect.width()) / 2 - imgRect.left)).toInt()
        }
    } else {
        // 图片大于控件，限制在边界内
        if (imgRect.left > mWidgetRect.left) {
            tx = (imgRect.left - mWidgetRect.left).toInt()
        } else if (imgRect.right < mWidgetRect.right) {
            tx = (imgRect.right - mWidgetRect.right).toInt()
        }
    }
    
    // 垂直方向回弹（类似）
    // ...
    
    if (tx != 0 || ty != 0) {
        mTranslate.withTranslate(mTranslateX, mTranslateY, -tx, -ty)
    }
}
```

### 8.3 越界阻力

```kotlin
private fun resistanceScrollByX(overScroll: Float, detalX: Float): Float {
    // 越界越远，阻力越大
    return detalX * (abs(abs(overScroll) - MAX_OVER_RESISTANCE) / MAX_OVER_RESISTANCE.toFloat())
}
```

---

## 九、过渡动画实现

### 9.1 从缩略图到大图（animaFrom）

```kotlin
fun animaFrom(info: Info) {
    if (isInit) {
        reset()
        val mine = getInfo()
        
        // 1. 计算缩放比例
        val scaleX = info.mImgRect.width() / mine.mImgRect.width()
        val scaleY = info.mImgRect.height() / mine.mImgRect.height()
        val scale = min(scaleX, scaleY)
        
        // 2. 计算中心点偏移
        val ocx = info.mRect.left + info.mRect.width() / 2
        val ocy = info.mRect.top + info.mRect.height() / 2
        val mcx = mine.mRect.left + mine.mRect.width() / 2
        val mcy = mine.mRect.top + mine.mRect.height() / 2
        
        // 3. 设置初始状态（缩略图大小位置）
        mAnimMatrix.postTranslate(ocx - mcx, ocy - mcy)
        mAnimMatrix.postScale(scale, scale, ocx, ocy)
        mAnimMatrix.postRotate(info.mDegrees, ocx, ocy)
        executeTranslate()
        
        // 4. 执行动画到正常状态
        mTranslate.withTranslate(0, 0, (-(ocx - mcx)).toInt(), (-(ocy - mcy)).toInt())
        mTranslate.withScale(scale, 1f)
        mTranslate.withRotate(info.mDegrees.toInt(), 0)
        mTranslate.start()
    } else {
        // 延迟执行
        mFromInfo = info
        mInfoTime = System.currentTimeMillis()
    }
}
```

### 9.2 从大图到缩略图（animaTo）

```kotlin
fun animaTo(info: Info, completeCallBack: Runnable) {
    if (isInit) {
        mTranslate.stop()
        
        // 1. 计算目标位置
        val tcx = info.mRect.left + info.mRect.width() / 2
        val tcy = info.mRect.top + info.mRect.height() / 2
        
        // 2. 计算目标缩放
        val scaleX = info.mImgRect.width() / mBaseRect.width()
        val scaleY = info.mImgRect.height() / mBaseRect.height()
        val scale = max(scaleX, scaleY)
        
        // 3. 执行动画
        mTranslate.withTranslate(0, 0, (tcx - mScaleCenter.x).toInt(), (tcy - mScaleCenter.y).toInt())
        mTranslate.withScale(mScale, scale)
        mTranslate.withRotate(mDegrees.toInt(), info.mDegrees.toInt())
        
        mCompleteCallBack = completeCallBack
        mTranslate.start()
    }
}
```

---

## 十、最佳实践

### 10.1 使用示例

```kotlin
// 基础使用
val photoView = findViewById<PhotoView>(R.id.photo_view)
photoView.setImageResource(R.drawable.large_image)

// 配置参数
photoView.apply {
    setMaxScale(3.0f)           // 最大缩放3倍
    setAnimDuring(300)          // 动画时长300ms
    isRotateEnable = true       // 启用旋转
}

// 监听点击
photoView.setOnClickListener {
    // 处理点击
}

// 长按监听
photoView.setOnLongClickListener {
    // 处理长按
    true
}
```

### 10.2 XML 配置

```xml
<io.legado.app.ui.widget.image.PhotoView
    android:id="@+id/photo_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:scaleType="fitCenter" />
```

### 10.3 性能优化

```kotlin
// 1. 及时停止动画
override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    mTranslate.stop()
}

// 2. 大图加载优化
Glide.with(context)
    .load(url)
    .override(1080, 1920)  // 限制加载尺寸
    .into(photoView)

// 3. 复用 PhotoView
// 在 RecyclerView 中使用 ViewHolder 模式
```

### 10.4 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 图片不显示 | 未设置 ScaleType | 设置 `scaleType="fitCenter"` |
| 手势不响应 | isEnable = false | 设置为 true |
| 动画卡顿 | 图片过大 | 压缩图片尺寸 |
| 双击缩放位置不对 | 未计算中心点 | 使用最新版本 |

---

## 参考资源

- **Matrix 官方文档**: [Matrix](https://developer.android.com/reference/android/graphics/Matrix)
- **手势检测**: [GestureDetector](https://developer.android.com/reference/android/view/GestureDetector)
- **Scroller**: [OverScroller](https://developer.android.com/reference/android/widget/OverScroller)
- **项目文件**: `app/src/main/java/io/legado/app/ui/widget/image/PhotoView.kt`

---

> 文档生成时间：2026年3月
> 代码行数：1260 行
> 核心知识点：Matrix 变换、手势检测、动画系统、边界处理
