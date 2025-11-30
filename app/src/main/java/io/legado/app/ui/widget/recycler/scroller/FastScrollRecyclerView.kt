package io.legado.app.ui.widget.recycler.scroller

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R

/**
 * FastScrollRecyclerView 是一个自定义的 RecyclerView 子类，主要作用是为 RecyclerView 添加快速滚动功能。它通过集成一个 FastScroller 组件来实现以下核心功能：
 * 主要功能特点：
 * 快速滚动条：在列表右侧显示一个可拖动的滚动条，用户可以快速拖动到列表的任意位置
 * 章节索引：当适配器实现 FastScroller.SectionIndexer 接口时，会显示章节气泡提示当前滚动位置对应的章节标题
 * 自定义样式：支持自定义滚动条、轨道、气泡的颜色和可见性
 * 状态监听：提供滚动状态变化的监听接口
 * 工作原理：
 * 在 onAttachedToWindow() 中，将 FastScroller 组件添加到 RecyclerView 的父容器中
 * 通过适配器接口获取章节信息来显示索引气泡
 * 同步 RecyclerView 的滚动位置与快速滚动条的位置
 * 在 onDetachedFromWindow() 中正确清理资源，避免内存泄漏
 * 这个类特别适用于需要处理大量数据的列表场景，让用户能够快速导航到特定位置，提升用户体验。
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class FastScrollRecyclerView : RecyclerView {

    private lateinit var mFastScroller: FastScroller

    constructor(context: Context) : super(context) {
        layout(context, null)
        layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int = 0
    ) : super(context, attrs, defStyleAttr) {
        layout(context, attrs)
    }

    private fun layout(context: Context, attrs: AttributeSet?) {
        mFastScroller = FastScroller(context, attrs)
        mFastScroller.id = R.id.fast_scroller
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)
        if (adapter is FastScroller.SectionIndexer) {
            setSectionIndexer(adapter as FastScroller.SectionIndexer?)
        } else if (adapter == null) {
            setSectionIndexer(null)
        }
    }


    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        mFastScroller.visibility = visibility
    }


    /**
     * Set the [FastScroller.SectionIndexer] for the [FastScroller].
     *
     * @param sectionIndexer The SectionIndexer that provides section text for the FastScroller
     */
    fun setSectionIndexer(sectionIndexer: FastScroller.SectionIndexer?) {
        mFastScroller.setSectionIndexer(sectionIndexer)
    }


    /**
     * Set the enabled state of fast scrolling.
     *
     * @param enabled True to enable fast scrolling, false otherwise
     */
    fun setFastScrollEnabled(enabled: Boolean) {
        mFastScroller.isEnabled = enabled
    }


    /**
     * Hide the scrollbar when not scrolling.
     *
     * @param hideScrollbar True to hide the scrollbar, false to show
     */
    fun setHideScrollbar(hideScrollbar: Boolean) {
        mFastScroller.setFadeScrollbar(hideScrollbar)
    }

    /**
     * Display a scroll track while scrolling.
     *
     * @param visible True to show scroll track, false to hide
     */
    fun setTrackVisible(visible: Boolean) {
        mFastScroller.setTrackVisible(visible)
    }

    /**
     * Set the color of the scroll track.
     *
     * @param color The color for the scroll track
     */
    fun setTrackColor(@ColorInt color: Int) {
        mFastScroller.setTrackColor(color)
    }


    /**
     * Set the color for the scroll handle.
     *
     * @param color The color for the scroll handle
     */
    fun setHandleColor(@ColorInt color: Int) {
        mFastScroller.setHandleColor(color)
    }


    /**
     * Show the section bubble while scrolling.
     *
     * @param visible True to show the bubble, false to hide
     */
    fun setBubbleVisible(visible: Boolean) {
        mFastScroller.setBubbleVisible(visible)
    }


    /**
     * Set the background color of the index bubble.
     *
     * @param color The background color for the index bubble
     */
    fun setBubbleColor(@ColorInt color: Int) {
        mFastScroller.setBubbleColor(color)
    }


    /**
     * Set the text color of the index bubble.
     *
     * @param color The text color for the index bubble
     */
    fun setBubbleTextColor(@ColorInt color: Int) {
        mFastScroller.setBubbleTextColor(color)
    }


    /**
     * Set the fast scroll state change listener.
     *
     * @param fastScrollStateChangeListener The interface that will listen to fastscroll state change events
     */
    fun setFastScrollStateChangeListener(fastScrollStateChangeListener: FastScrollStateChangeListener) {
        mFastScroller.setFastScrollStateChangeListener(fastScrollStateChangeListener)
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mFastScroller.attachRecyclerView(this)
        var parent = parent
        while (parent != null) {
            when (parent) {
                is ConstraintLayout, is CoordinatorLayout, is FrameLayout, is RelativeLayout -> break
                else -> parent = parent.parent
            }
        }
        if (parent is ViewGroup && parent.indexOfChild(mFastScroller) == -1) {
            parent.addView(mFastScroller)
            mFastScroller.setLayoutParams(parent)
        }
    }


    override fun onDetachedFromWindow() {
        mFastScroller.detachRecyclerView()
        super.onDetachedFromWindow()
    }

}