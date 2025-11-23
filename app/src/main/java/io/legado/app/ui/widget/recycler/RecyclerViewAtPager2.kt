package io.legado.app.ui.widget.recycler

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class RecyclerViewAtPager2 : RecyclerView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private var startX = 0
    private var startY = 0

    /**
     * 按下时（ACTION_DOWN）：记录初始触摸位置，并通过requestDisallowInterceptTouchEvent(true)阻止父容器立即拦截事件，确保RecyclerView能接收后续触摸事件。
     * 滑动时（ACTION_MOVE）：通过比较水平滑动距离（disX）和垂直滑动距离（disY）判断滑动方向。当水平滑动距离超过50px且大于垂直滑动距离时，允许父容器拦截事件（通常用于ViewPager2的页面切换）；否则保持由RecyclerView处理（用于列表垂直滚动）。
     * 抬起/取消时（ACTION_UP/ACTION_CANCEL）：恢复父容器的事件拦截能力，避免影响后续交互。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x.toInt()
                startY = ev.y.toInt()
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val endX = ev.x.toInt()
                val endY = ev.y.toInt()
                val disX = abs(endX - startX)
                val disY = abs(endY - startY)
                if (disX > disY) {
                    if (disX > 50) {
                        parent.requestDisallowInterceptTouchEvent(false)
                    }
                } else {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(ev)
    }

}