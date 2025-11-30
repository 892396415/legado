package io.legado.app.ui.widget.recycler.scroller

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.widget.*
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getCompatColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


@Suppress("SameParameterValue")
class FastScroller : LinearLayout {
    @ColorInt
    private var mBubbleColor: Int = 0

    @ColorInt
    private var mHandleColor: Int = 0
    private var mBubbleHeight: Int = 0
    private var mHandleHeight: Int = 0
    private var mViewHeight: Int = 0
    private var mFadeScrollbar: Boolean = false
    private var mShowBubble: Boolean = false
    private var mSectionIndexer: SectionIndexer? = null
    private var mScrollbarAnimator: ViewPropertyAnimator? = null
    private var mBubbleAnimator: ViewPropertyAnimator? = null
    private var mRecyclerView: RecyclerView? = null
    private lateinit var mBubbleView: TextView
    private lateinit var mHandleView: ImageView
    private lateinit var mTrackView: ImageView
    private lateinit var mScrollbar: View
    private var mBubbleImage: Drawable? = null
    private var mHandleImage: Drawable? = null
    private var mTrackImage: Drawable? = null
    private var mFastScrollStateChangeListener: FastScrollStateChangeListener? = null
    private val mScrollbarHider = Runnable { this.hideScrollbar() }

    private val mScrollListener = object : RecyclerView.OnScrollListener() {
        // 作用：列表滚动时，计算滚动比例并更新手柄 / 气泡位置（仅手柄未被选中时触发，避免手动拖动时冲突）
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!mHandleView.isSelected && isEnabled) {
                setViewPositions(getScrollProportion(recyclerView))
            }
        }

        /**
         * 作用：列表滚动状态变化时，控制滚动条的显示 / 隐藏：
         * 拖拽状态（DRAGGING）：移除延迟隐藏任务、取消动画、显示滚动条；
         * 闲置状态（IDLE）：若开启淡入淡出，延迟 1 秒触发隐藏滚动条。
         */
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (isEnabled) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        handler.removeCallbacks(mScrollbarHider)
                        cancelAnimation(mScrollbarAnimator)
                        if (!isViewVisible(mScrollbar)) {
                            showScrollbar()
                        }
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> if (mFadeScrollbar && !mHandleView.isSelected) {
                        handler.postDelayed(mScrollbarHider, sScrollbarHideDelay.toLong())
                    }
                }
            }
        }
    }

    constructor(context: Context) : super(context) {
        layout(context, null)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
    }

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = 0) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        layout(context, attrs)
        layoutParams = generateLayoutParams(attrs)
    }

    /**
     * 作用：强制覆盖布局参数 —— 宽度固定为 WRAP_CONTENT，避免外部修改导致样式异常；
     * 关键逻辑：先修改 params.width = WRAP_CONTENT，再调用父类方法。
     */
    override fun setLayoutParams(params: ViewGroup.LayoutParams) {
        params.width = LayoutParams.WRAP_CONTENT
        super.setLayoutParams(params)
    }

    /**
     * 其贴靠 RecyclerView 右侧；
     * 核心逻辑：
     * 先获取 RecyclerView 的 ID 和上下边距；
     * 按父布局类型分别设置约束：
     * ConstraintLayout：通过 ConstraintSet 绑定 “上下与 RecyclerView 对齐、右侧贴 RecyclerView 右侧”；
     * CoordinatorLayout：设置锚点为 RecyclerView，锚点重力为右侧；
     * FrameLayout：设置重力为右侧；
     * RelativeLayout：添加 “对齐 RecyclerView 上下 / 右侧” 的规则；
     * 最后更新控件高度（updateViewHeights()）
     */
    fun setLayoutParams(viewGroup: ViewGroup) {
        @IdRes val recyclerViewId = mRecyclerView?.id ?: View.NO_ID
        val marginTop = resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_margin_top)
        val marginBottom =
            resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_margin_bottom)
        require(recyclerViewId != View.NO_ID) { "RecyclerView must have a view ID" }
        when (viewGroup) {
            is ConstraintLayout -> {
                val constraintSet = ConstraintSet()
                @IdRes val layoutId = id
                constraintSet.clone(viewGroup)
                constraintSet.connect(
                    layoutId,
                    ConstraintSet.TOP,
                    recyclerViewId,
                    ConstraintSet.TOP
                )
                constraintSet.connect(
                    layoutId,
                    ConstraintSet.BOTTOM,
                    recyclerViewId,
                    ConstraintSet.BOTTOM
                )
                constraintSet.connect(
                    layoutId,
                    ConstraintSet.END,
                    recyclerViewId,
                    ConstraintSet.END
                )
                constraintSet.applyTo(viewGroup)
                val layoutParams = layoutParams as ConstraintLayout.LayoutParams
                layoutParams.setMargins(0, marginTop, 0, marginBottom)
                setLayoutParams(layoutParams)
            }
            is CoordinatorLayout -> {
                val layoutParams = layoutParams as CoordinatorLayout.LayoutParams
                layoutParams.anchorId = recyclerViewId
                layoutParams.anchorGravity = GravityCompat.END
                layoutParams.setMargins(0, marginTop, 0, marginBottom)
                setLayoutParams(layoutParams)
            }
            is FrameLayout -> {
                val layoutParams = layoutParams as FrameLayout.LayoutParams
                layoutParams.gravity = GravityCompat.END
                layoutParams.setMargins(0, marginTop, 0, marginBottom)
                setLayoutParams(layoutParams)
            }
            is RelativeLayout -> {
                val layoutParams = layoutParams as RelativeLayout.LayoutParams
                val endRule = RelativeLayout.ALIGN_END
                layoutParams.addRule(RelativeLayout.ALIGN_TOP, recyclerViewId)
                layoutParams.addRule(RelativeLayout.ALIGN_BOTTOM, recyclerViewId)
                layoutParams.addRule(endRule, recyclerViewId)
                layoutParams.setMargins(0, marginTop, 0, marginBottom)
                setLayoutParams(layoutParams)
            }
            else -> throw IllegalArgumentException("Parent ViewGroup must be a ConstraintLayout, CoordinatorLayout, FrameLayout, or RelativeLayout")
        }
        updateViewHeights()
    }

    /**
     * 作用：设置索引器，让气泡能显示 “列表位置→分区文本” 的映射（如字母索引、章节索引）；
     * 逻辑：保存 SectionIndexer 接口引用，用于后续根据滚动位置获取分区文本。
     */
    fun setSectionIndexer(sectionIndexer: SectionIndexer?) {
        mSectionIndexer = sectionIndexer
    }

    /**
     * fun attachRecyclerView(recyclerView: RecyclerView)
     * 作用：绑定目标 RecyclerView，添加滚动监听器，初始化手柄 / 气泡的初始位置；
     * 关键逻辑：
     * 保存 RecyclerView 引用，添加 mScrollListener（列表滚动时同步更新手柄位置）；
     * 通过 post 延迟初始化手柄 / 气泡位置（保证 RecyclerView 已完成布局
     */
    fun attachRecyclerView(recyclerView: RecyclerView) {
        mRecyclerView = recyclerView
        mRecyclerView!!.addOnScrollListener(mScrollListener)
        post {
            // set initial positions for bubble and handle
            setViewPositions(getScrollProportion(mRecyclerView))
        }
    }

    /**
     * 作用：解绑 RecyclerView，移除滚动监听器，避免内存泄漏；
     * 关键逻辑：移除 mScrollListener，清空 RecyclerView 引用
     */
    fun detachRecyclerView() {
        if (mRecyclerView != null) {
            mRecyclerView!!.removeOnScrollListener(mScrollListener)
            mRecyclerView = null
        }
    }

    /**
     * 作用：设置是否开启滚动条自动淡入淡出；
     * 逻辑：更新 mFadeScrollbar，并设置滚动条初始可见性（关闭淡入淡出则始终显示
     * Hide the scrollbar when not scrolling.
     * @param fadeScrollbar True to hide the scrollbar, false to show
     */
    fun setFadeScrollbar(fadeScrollbar: Boolean) {
        mFadeScrollbar = fadeScrollbar
        mScrollbar.visibility = if (fadeScrollbar) View.INVISIBLE else View.VISIBLE
    }

    /**
     * 作用：设置是否显示索引气泡（滚动时展示分区文本）；
     * 逻辑：更新 mShowBubble 标记
     * Show the section bubble while scrolling.
     * @param visible True to show the bubble, false to hide
     */
    fun setBubbleVisible(visible: Boolean) {
        mShowBubble = visible
    }

    /**
     * 作用：设置是否显示滚动轨道；
     * 逻辑：直接修改 mTrackView 的可见性
     * Display a scroll track while scrolling.
     * @param visible True to show scroll track, false to hide
     */
    fun setTrackVisible(visible: Boolean) {
        mTrackView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    /**
     * 作用：设置轨道 / 手柄 / 气泡的颜色；
     * 核心逻辑（以 setHandleColor 为例）：
     * 若手柄 Drawable 未初始化，加载默认 Drawable 并通过 DrawableCompat.wrap 兼容不同 Android 版本；
     * 用 DrawableCompat.setTint 给 Drawable 染色；
     * 将染色后的 Drawable 设置给手柄控件
     * Set the color of the scroll track.
     * @param color The color for the scroll track
     */
    fun setTrackColor(@ColorInt color: Int) {
        if (mTrackImage == null) {
            val drawable = ContextCompat.getDrawable(context, R.drawable.fastscroll_track)
            if (drawable != null) {
                mTrackImage = DrawableCompat.wrap(drawable)
            }
        }
        DrawableCompat.setTint(mTrackImage!!, color)
        mTrackView.setImageDrawable(mTrackImage)
    }

    /**
     * 作用：设置索引气泡内文本的颜色；
     * 逻辑：直接修改 mBubbleView 的文本颜色。
     * Set the color for the scroll handle.
     * @param color The color for the scroll handle
     */
    fun setHandleColor(@ColorInt color: Int) {
        mHandleColor = color
        if (mHandleImage == null) {
            val drawable = ContextCompat.getDrawable(context, R.drawable.fastscroll_handle)
            if (drawable != null) {
                mHandleImage = DrawableCompat.wrap(drawable)
            }
        }
        DrawableCompat.setTint(mHandleImage!!, mHandleColor)
        mHandleView.setImageDrawable(mHandleImage)
    }

    /**
     * Set the background color of the index bubble.
     * @param color The background color for the index bubble
     */
    fun setBubbleColor(@ColorInt color: Int) {
        mBubbleColor = color
        if (mBubbleImage == null) {
            val drawable = ContextCompat.getDrawable(context, R.drawable.fastscroll_bubble)
            if (drawable != null) {
                mBubbleImage = DrawableCompat.wrap(drawable)
            }
        }
        DrawableCompat.setTint(mBubbleImage!!, mBubbleColor)
        mBubbleView.background = mBubbleImage
    }

    /**
     * 作用：设置索引气泡内文本的颜色；
     * 逻辑：直接修改 mBubbleView 的文本颜色。
     * Set the text color of the index bubble.
     * @param color The text color for the index bubble
     */
    fun setBubbleTextColor(@ColorInt color: Int) {
        mBubbleView.setTextColor(color)
    }

    /**
     * 作用：设置快滑状态监听（快滑开始 / 停止时回调）；
     * 逻辑：保存监听接口引用
     * Set the fast scroll state change listener.
     * @param fastScrollStateChangeListener The interface that will listen to fastscroll state change events
     */
    fun setFastScrollStateChangeListener(fastScrollStateChangeListener: FastScrollStateChangeListener) {
        mFastScrollStateChangeListener = fastScrollStateChangeListener
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    /**
     * 作用：处理触摸事件，实现 “拖动手柄滚动列表” 的核心交互
     */
    @Suppress("DEPRECATION")
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x < mHandleView.x - ViewCompat.getPaddingStart(mHandleView)) {
                    return false
                }
                if (!mScrollbar.isVisible) {
                    return false
                }
                /**
                 * 景：拖动手柄时，避免父布局（如 RecyclerView）拦截触摸事件（否则拖动会触发列表滚动，冲突）；
                 * 原理：通过requestDisallowInterceptTouchEvent告诉父布局 “不要拦截我的触摸事件”，保证快滑器能完整消费拖动事件
                 */
                requestDisallowInterceptTouchEvent(true)
                setHandleSelected(true)
                handler.removeCallbacks(mScrollbarHider)
                cancelAnimation(mScrollbarAnimator)
                cancelAnimation(mBubbleAnimator)
                if (mShowBubble && mSectionIndexer != null) {
                    showBubble()
                }
                if (mFastScrollStateChangeListener != null) {
                    mFastScrollStateChangeListener!!.onFastScrollStart(this)
                }
                val y = event.y
                setViewPositions(y)
                setRecyclerViewPosition(y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val y = event.y
                setViewPositions(y)
                setRecyclerViewPosition(y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                requestDisallowInterceptTouchEvent(false)
                setHandleSelected(false)
                if (mFadeScrollbar) {
                    handler.postDelayed(mScrollbarHider, sScrollbarHideDelay.toLong())
                }
                hideBubble()
                if (mFastScrollStateChangeListener != null) {
                    mFastScrollStateChangeListener!!.onFastScrollStop(this)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 作用：快滑器尺寸变化时，记录自身高度 mViewHeight（后续计算滚动比例的核心参数）；
     * 触发时机：控件首次布局 / 屏幕旋转 / 父布局尺寸变化时。
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mViewHeight = h
    }

    /**
     * 作用：根据触摸 Y 坐标计算滚动比例，联动 RecyclerView 滚动到对应位置；
     * 核心逻辑：
     * 计算滚动比例：手柄贴顶部→0，贴底部→1，否则按 Y 坐标 / 快滑器高度计算；
     * 按比例计算目标列表位置：proportion * itemCount（反向布局则取反）；
     * 调用 layoutManager.scrollToPosition 滚动列表；
     * 若开启气泡，通过 SectionIndexer 获取当前位置的索引文本并设置到气泡。
     */
    private fun setRecyclerViewPosition(y: Float) {
        mRecyclerView?.adapter?.let { adapter ->
            val itemCount = adapter.itemCount
            val proportion: Float = when {
                mHandleView.y == 0f -> 0f
                mHandleView.y + mHandleHeight >= mViewHeight - sTrackSnapRange -> 1f
                else -> y / mViewHeight.toFloat()
            }
            var scrolledItemCount = (proportion * itemCount).roundToInt()
            if (isLayoutReversed(mRecyclerView?.layoutManager)) {
                scrolledItemCount = itemCount - scrolledItemCount
            }
            val targetPos = getValueInRange(0, itemCount - 1, scrolledItemCount)
            mRecyclerView?.layoutManager?.scrollToPosition(targetPos)
            mSectionIndexer?.let { sectionIndexer ->
                if (mShowBubble) {
                    mBubbleView.text = sectionIndexer.getSectionText(targetPos)
                }
            }
        }
    }

    /**
     * 作用：计算 RecyclerView 的滚动比例，返回手柄应处的 Y 坐标；
     * 公式：(垂直滚动偏移量 / 滚动范围差值) * 快滑器高度；
     * 注：滚动范围差值 = 列表总高度 - 快滑器高度（避免分母为 0）
     */
    private fun getScrollProportion(recyclerView: RecyclerView?): Float {
        recyclerView ?: return 0f
        val verticalScrollOffset = recyclerView.computeVerticalScrollOffset()
        val verticalScrollRange = recyclerView.computeVerticalScrollRange()
        val rangeDiff = (verticalScrollRange - mViewHeight).toFloat()
        val proportion = verticalScrollOffset.toFloat() / if (rangeDiff > 0) rangeDiff else 1f
        return mViewHeight * proportion
    }

    /**
     * 作用：限制数值在 [min, max] 范围内（避免手柄超出快滑器边界）；
     * 逻辑：先取value和min的最大值，再取结果和max的最小值
     */
    private fun getValueInRange(min: Int, max: Int, value: Int): Int {
        val minimum = max(min, value)
        return min(minimum, max)
    }

    /**
     * 作用：根据触摸 Y 坐标，计算并设置气泡、手柄的 Y 坐标（保证不超出边界）；
     * 逻辑：
     * 计算气泡 Y 坐标：y - 气泡高度，限制在 0~(快滑器高度 - 气泡高度 - 手柄高度 / 2)；
     * 计算手柄 Y 坐标：y - 手柄高度/2，限制在 0~(快滑器高度 - 手柄高度)；
     * 若开启气泡则更新气泡位置，始终更新手柄位置
     */
    private fun setViewPositions(y: Float) {
        mBubbleHeight = mBubbleView.height
        mHandleHeight = mHandleView.height
        val bubbleY = getValueInRange(
            0,
            mViewHeight - mBubbleHeight - mHandleHeight / 2,
            (y - mBubbleHeight).toInt()
        )
        val handleY =
            getValueInRange(0, mViewHeight - mHandleHeight, (y - mHandleHeight / 2).toInt())
        if (mShowBubble) {
            mBubbleView.y = bubbleY.toFloat()
        }
        mHandleView.y = handleY.toFloat()
    }

    /**
     * 作用：测量气泡、手柄的实际高度（mBubbleHeight/mHandleHeight），为后续位置计算提供依据；
     * 关键逻辑：用 MeasureSpec.UNSPECIFIED 测量控件（不限制尺寸），获取测量后的高度。
     */
    private fun updateViewHeights() {
        val measureSpec =
            MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        mBubbleView.measure(measureSpec, measureSpec)
        mBubbleHeight = mBubbleView.measuredHeight
        mHandleView.measure(measureSpec, measureSpec)
        mHandleHeight = mHandleView.measuredHeight
    }

    /**
     * private fun isLayoutReversed(layoutManager: RecyclerView.LayoutManager?): Boolean
     * 作用：判断 RecyclerView 的布局是否为反向（如从下到上滚动的列表）；
     * 逻辑：判断布局管理器是否为LinearLayoutManager/StaggeredGridLayoutManager，并返回其reverseLayout属性。
     */
    private fun isLayoutReversed(layoutManager: RecyclerView.LayoutManager?): Boolean {
        if (layoutManager is LinearLayoutManager) {
            return layoutManager.reverseLayout
        } else if (layoutManager is StaggeredGridLayoutManager) {
            return layoutManager.reverseLayout
        }
        return false
    }

    private fun isViewVisible(view: View?): Boolean {
        return view != null && view.visibility == View.VISIBLE
    }

    private fun cancelAnimation(animator: ViewPropertyAnimator?) {
        animator?.cancel()
    }

    /**
     * private fun showBubble/hideBubble()
     * 作用：显示 / 隐藏索引气泡（带动画）；
     * 逻辑：
     * 显示：设置气泡可见，执行 alpha 从 0→1 的动画（100ms）；
     * 隐藏：执行 alpha 从 1→0 的动画（100ms），动画结束后设置气泡不可见
     */
    private fun showBubble() {
        if (!isViewVisible(mBubbleView)) {
            mBubbleView.visibility = View.VISIBLE
            mBubbleAnimator = mBubbleView.animate().alpha(1f)
                .setDuration(sBubbleAnimDuration.toLong())
                .setListener(object : AnimatorListenerAdapter() {

                    // adapter required for new alpha value to stick
                })
        }
    }

    private fun hideBubble() {
        if (isViewVisible(mBubbleView)) {
            mBubbleAnimator = mBubbleView.animate().alpha(0f)
                .setDuration(sBubbleAnimDuration.toLong())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        mBubbleView.visibility = View.INVISIBLE
                        mBubbleAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        super.onAnimationCancel(animation)
                        mBubbleView.visibility = View.INVISIBLE
                        mBubbleAnimator = null
                    }
                })
        }
    }

    /**
     * 作用：显示 / 隐藏滚动条（带动画）；
     * 逻辑：
     * 显示：设置滚动条可见，执行 “平移 + 透明度” 动画（从右侧滑入，300ms）；
     * 隐藏：执行 “平移 + 透明度” 动画（滑出右侧，300ms），动画结束后设置不可见
     */
    private fun showScrollbar() {
        mRecyclerView?.let { mRecyclerView ->
            if (mRecyclerView.computeVerticalScrollRange() - mViewHeight > 0) {
                val transX =
                    resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end)
                        .toFloat()
                mScrollbar.translationX = transX
                mScrollbar.visibility = View.VISIBLE
                mScrollbarAnimator = mScrollbar.animate().translationX(0f).alpha(1f)
                    .setDuration(sScrollbarAnimDuration.toLong())
                    .setListener(object : AnimatorListenerAdapter() {

                        // adapter required for new alpha value to stick
                    })
            }
        }
    }

    private fun hideScrollbar() {
        val transX =
            resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_padding_end).toFloat()
        mScrollbarAnimator = mScrollbar.animate().translationX(transX).alpha(0f)
            .setDuration(sScrollbarAnimDuration.toLong())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    mScrollbar.visibility = View.INVISIBLE
                    mScrollbarAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    super.onAnimationCancel(animation)
                    mScrollbar.visibility = View.INVISIBLE
                    mScrollbarAnimator = null
                }
            })
    }

    /**
     * 作用：设置手柄选中状态，并切换手柄颜色（选中时用气泡色，未选中时用手柄色）；
     * 逻辑：修改mHandleView.isSelected，并通过DrawableCompat.setTint修改手柄 Drawable 颜色
     */
    private fun setHandleSelected(selected: Boolean) {
        mHandleView.isSelected = selected
        DrawableCompat.setTint(mHandleImage!!, if (selected) mBubbleColor else mHandleColor)
    }

    private fun layout(context: Context, attrs: AttributeSet?) {
        View.inflate(context, R.layout.view_fastscroller, this)
        clipChildren = false
        orientation = HORIZONTAL
        mBubbleView = findViewById(R.id.fastscroll_bubble)
        mHandleView = findViewById(R.id.fastscroll_handle)
        mTrackView = findViewById(R.id.fastscroll_track)
        mScrollbar = findViewById(R.id.fastscroll_scrollbar)
        @ColorInt var bubbleColor = ColorUtils.adjustAlpha(context.accentColor, 0.8f)
        @ColorInt var handleColor = context.accentColor
        @ColorInt var trackColor = context.getCompatColor(R.color.transparent30)
        @ColorInt var textColor =
            if (ColorUtils.isColorLight(bubbleColor)) Color.BLACK else Color.WHITE
        var fadeScrollbar = true
        var showBubble = false
        var showTrack = true
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FastScroller, 0, 0)
            try {
                bubbleColor = typedArray.getColor(R.styleable.FastScroller_bubbleColor, bubbleColor)
                handleColor = typedArray.getColor(R.styleable.FastScroller_handleColor, handleColor)
                trackColor = typedArray.getColor(R.styleable.FastScroller_trackColor, trackColor)
                textColor = typedArray.getColor(R.styleable.FastScroller_bubbleTextColor, textColor)
                fadeScrollbar =
                    typedArray.getBoolean(R.styleable.FastScroller_fadeScrollbar, fadeScrollbar)
                showBubble = typedArray.getBoolean(R.styleable.FastScroller_showBubble, showBubble)
                showTrack = typedArray.getBoolean(R.styleable.FastScroller_showTrack, showTrack)
            } finally {
                typedArray.recycle()
            }
        }
        setTrackColor(trackColor)
        setHandleColor(handleColor)
        setBubbleColor(bubbleColor)
        setBubbleTextColor(textColor)
        setFadeScrollbar(fadeScrollbar)
        setBubbleVisible(showBubble)
        setTrackVisible(showTrack)
    }

    interface SectionIndexer {
        fun getSectionText(position: Int): String
    }

    companion object {
        private const val sBubbleAnimDuration = 100
        private const val sScrollbarAnimDuration = 300
        private const val sScrollbarHideDelay = 1000
        private const val sTrackSnapRange = 5
    }

}
