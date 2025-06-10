package io.legado.app.ui.widget.seekbar

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

import androidx.core.view.ViewCompat
import kotlin.math.max

class VerticalSeekBarWrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /**
     * 通过 childSeekBar 属性获取 VerticalSeekBarWrapper 的第一个子视图，
     * 若该子视图是 VerticalSeekBar 类型则返回该实例，否则返回 null。
     */
    private val childSeekBar: VerticalSeekBar?
        get() {
            val child = if (childCount > 0) getChildAt(0) else null
            return if (child is VerticalSeekBar) child else null
        }

    /**
     * 重写 onSizeChanged 方法，根据 useViewRotation 方法的返回值，选择不同的方式处理尺寸变化：
     * onSizeChangedUseViewRotation：使用视图旋转的方式处理尺寸变化。
     * onSizeChangedTraditionalRotation：使用传统旋转的方式处理尺寸变化。
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (useViewRotation()) {
            onSizeChangedUseViewRotation(w, h, oldw, oldh)
        } else {
            onSizeChangedTraditionalRotation(w, h, oldw, oldh)
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun onSizeChangedTraditionalRotation(w: Int, h: Int, oldw: Int, oldh: Int) {
        val seekBar = childSeekBar

        if (seekBar != null) {
            val hPadding = paddingLeft + paddingRight
            val vPadding = paddingTop + paddingBottom
            val lp = seekBar.layoutParams as LayoutParams

            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.height = max(0, h - vPadding)
            seekBar.layoutParams = lp

            seekBar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)

            val seekBarMeasuredWidth = seekBar.measuredWidth
            seekBar.measure(
                MeasureSpec.makeMeasureSpec(
                    max(0, w - hPadding),
                    MeasureSpec.AT_MOST
                ),
                MeasureSpec.makeMeasureSpec(
                    max(0, h - vPadding),
                    MeasureSpec.EXACTLY
                )
            )

            lp.gravity = Gravity.TOP or Gravity.LEFT
            lp.leftMargin = (max(0, w - hPadding) - seekBarMeasuredWidth) / 2
            seekBar.layoutParams = lp
        }

        super.onSizeChanged(w, h, oldw, oldh)
    }

    private fun onSizeChangedUseViewRotation(w: Int, h: Int, oldw: Int, oldh: Int) {
        val seekBar = childSeekBar

        if (seekBar != null) {
            val hPadding = paddingLeft + paddingRight
            val vPadding = paddingTop + paddingBottom
            seekBar.measure(
                MeasureSpec.makeMeasureSpec(
                    max(0, h - vPadding),
                    MeasureSpec.EXACTLY
                ),
                MeasureSpec.makeMeasureSpec(
                    max(0, w - hPadding),
                    MeasureSpec.AT_MOST
                )
            )
        }

        applyViewRotation(w, h)
        super.onSizeChanged(w, h, oldw, oldh)
    }

    /**
     * 重写 onMeasure 方法，根据 VerticalSeekBar 是否存在以及宽度测量模式，
     * 计算并设置 VerticalSeekBarWrapper 的测量尺寸。
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val seekBar = childSeekBar
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        if (seekBar != null && widthMode != MeasureSpec.EXACTLY) {
            val seekBarWidth: Int
            val seekBarHeight: Int
            val hPadding = paddingLeft + paddingRight
            val vPadding = paddingTop + paddingBottom
            val innerContentWidthMeasureSpec =
                MeasureSpec.makeMeasureSpec(max(0, widthSize - hPadding), widthMode)
            val innerContentHeightMeasureSpec =
                MeasureSpec.makeMeasureSpec(max(0, heightSize - vPadding), heightMode)

            if (useViewRotation()) {
                seekBar.measure(innerContentHeightMeasureSpec, innerContentWidthMeasureSpec)
                seekBarWidth = seekBar.measuredHeight
                seekBarHeight = seekBar.measuredWidth
            } else {
                seekBar.measure(innerContentWidthMeasureSpec, innerContentHeightMeasureSpec)
                seekBarWidth = seekBar.measuredWidth
                seekBarHeight = seekBar.measuredHeight
            }

            val measuredWidth =
                View.resolveSizeAndState(seekBarWidth + hPadding, widthMeasureSpec, 0)
            val measuredHeight =
                View.resolveSizeAndState(seekBarHeight + vPadding, heightMeasureSpec, 0)

            setMeasuredDimension(measuredWidth, measuredHeight)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /*package*/
    internal fun applyViewRotation() {
        applyViewRotation(width, height)
    }

    @Suppress("DEPRECATION")
    private fun applyViewRotation(w: Int, h: Int) {
        val seekBar = childSeekBar

        if (seekBar != null) {
            val isLTR = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_LTR
            val rotationAngle = seekBar.rotationAngle
            val seekBarMeasuredWidth = seekBar.measuredWidth
            val seekBarMeasuredHeight = seekBar.measuredHeight
            val hPadding = paddingLeft + paddingRight
            val vPadding = paddingTop + paddingBottom
            val hOffset = (max(0, w - hPadding) - seekBarMeasuredHeight) * 0.5f
            val lp = seekBar.layoutParams

            lp.width = max(0, h - vPadding)
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT

            seekBar.layoutParams = lp

            seekBar.pivotX = (if (isLTR) 0 else max(0, h - vPadding)).toFloat()
            seekBar.pivotY = 0f

            when (rotationAngle) {
                VerticalSeekBar.ROTATION_ANGLE_CW_90 -> {
                    seekBar.rotation = 90f
                    if (isLTR) {
                        seekBar.translationX = seekBarMeasuredHeight + hOffset
                        seekBar.translationY = 0f
                    } else {
                        seekBar.translationX = -hOffset
                        seekBar.translationY = seekBarMeasuredWidth.toFloat()
                    }
                }
                VerticalSeekBar.ROTATION_ANGLE_CW_270 -> {
                    seekBar.rotation = 270f
                    if (isLTR) {
                        seekBar.translationX = hOffset
                        seekBar.translationY = seekBarMeasuredWidth.toFloat()
                    } else {
                        seekBar.translationX = -(seekBarMeasuredHeight + hOffset)
                        seekBar.translationY = 0f
                    }
                }
            }
        }
    }

    private fun useViewRotation(): Boolean {
        val seekBar = childSeekBar
        return seekBar?.useViewRotation() ?: false
    }
}
