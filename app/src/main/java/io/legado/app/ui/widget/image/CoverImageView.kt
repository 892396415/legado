package io.legado.app.ui.widget.image

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.constant.AppPattern
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.BookCover
import io.legado.app.utils.textHeight
import io.legado.app.utils.toStringArray

/**
 * 封面
 */
@Suppress("unused")
class CoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private var filletPath = Path()
    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f
    private var defaultCover = true
    var bitmapPath: String? = null
        private set
    private var name: String? = null
    private var author: String? = null
    private var nameHeight = 0f
    private var authorHeight = 0f
    private val namePaint by lazy {
        val textPaint = TextPaint()
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint
    }
    private val authorPaint by lazy {
        val textPaint = TextPaint()
        textPaint.typeface = Typeface.DEFAULT
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint
    }

    /**
     * 设置布局参数时，根据宽度计算高度，保持封面比例为7:5
     */
    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        if (params != null) {
            val width = params.width
            if (width >= 0) {
                params.height = width * 7 / 5
            } else {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        super.setLayoutParams(params)
    }

    /**
     * 测量时，根据宽度计算高度，保持封面比例为7:5
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = measuredWidth * 7 / 5
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        )
    }

    /**
     * 在 onLayout 中通过 Path 绘制圆角矩形路径（filletPath），
     * 并在 onDraw 中使用 canvas.clipPath(filletPath) 裁剪画布，
     * 使封面图片呈现圆角效果，提升视觉美观度。
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // 1. 调用父类的 onLayout 方法
        /**
         * 作用: 这是 必须 的一步。它会触发父类 ViewGroup 或 View 的默认布局逻辑。
         * 对于 ViewGroup， 这会负责测量和定位其子 View。即使你的自定义 View 没有子 View，
         * 调用 super.onLayout 也是一个良好的编程习惯，以确保 View 状态的正确性
         */
        super.onLayout(changed, left, top, right, bottom)
        // 2. 记录当前 View 的宽高
        viewWidth = width.toFloat()
        viewHeight = height.toFloat()
        // 3. 重置路径（Path）
        // 是一个 android.graphics.Path 对象，用于存储一个几何路径。
        // 这里用于绘制圆角矩形路径，后续在 onDraw 中使用 canvas.clipPath(filletPath) 裁剪画布。
        filletPath.reset()
        // 4. 路径构建：绘制左上角和右上角的圆角
        /**
         * 总结 onLayout 的作用：在 View 大小确定后，构建一个描述 “四个角都是圆角” 形状的 Path 对象 (filletPath)。
         * 这个路径将在 onDraw 中被用来裁剪画布。
         */
        if (width > 10 && viewHeight > 10) {
            filletPath.apply {
                moveTo(10f, 0f)                          // a. 移动到左上角圆角的右下角点
                lineTo(viewWidth - 10, 0f)               // b. 画直线到右上角圆角的左下角点
                quadTo(viewWidth, 0f, viewWidth, 10f)   // c. 画右上角的贝塞尔曲线
                lineTo(viewWidth, viewHeight - 10)       // d. 画直线到右下角（直角）
                quadTo(viewWidth, viewHeight, viewWidth - 10, viewHeight) // e. 画右下角的贝塞尔曲线
                lineTo(10f, viewHeight)                  // f. 画直线到左下角（直角）
                quadTo(0f, viewHeight, 0f, viewHeight - 10) // g. 画左下角的贝塞尔曲线
                lineTo(0f, 10f)                          // h. 画直线到左上角圆角的右上角点
                quadTo(0f, 0f, 10f, 0f)                 // i. 画左上角的贝塞尔曲线
                close()                                  // j. 闭合路径
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // 1. 使用路径裁剪画布
        // 作用: 裁剪画布，只绘制 filletPath 路径内的区域。
        // 这里确保封面图片只显示在圆角矩形区域内，避免超出圆角部分
        if (!filletPath.isEmpty) {
            canvas.clipPath(filletPath)
        }
        super.onDraw(canvas)
        if (defaultCover && !isInEditMode) {
            drawNameAuthor(canvas)
        }
    }

    /**
     * 当封面图片加载失败或使用默认封面时（defaultCover = true），通过 drawNameAuthor 方法在封面中央绘制书名和作者文字：
     * 书名：使用粗体、较大字号（宽度的 1/6），白色描边 + 应用强调色填充，垂直排列，超出范围时自动换行。
     * 作者：使用常规字体、较小字号（宽度的 1/10），同样白色描边 + 强调色填充，位于封面右下角区域。
     * 绘制逻辑受 BookCover.drawBookName 和 BookCover.drawBookAuthor 配置控制，支持开关显示。
     */
    private fun drawNameAuthor(canvas: Canvas) {
        if (!BookCover.drawBookName) return
        var startX = width * 0.2f
        var startY = viewHeight * 0.2f
        name?.toStringArray()?.let { name ->
            namePaint.textSize = viewWidth / 6
            namePaint.strokeWidth = namePaint.textSize / 5
            name.forEachIndexed { index, char ->
                namePaint.color = Color.WHITE
                namePaint.style = Paint.Style.STROKE
                canvas.drawText(char, startX, startY, namePaint)
                namePaint.color = context.accentColor
                namePaint.style = Paint.Style.FILL
                canvas.drawText(char, startX, startY, namePaint)
                startY += namePaint.textHeight
                if (startY > viewHeight * 0.8) {
                    startX += namePaint.textSize
                    namePaint.textSize = viewWidth / 10
                    startY = (viewHeight - (name.size - index - 1) * namePaint.textHeight) / 2
                }
            }
        }
        if (!BookCover.drawBookAuthor) return
        author?.toStringArray()?.let { author ->
            authorPaint.textSize = viewWidth / 10
            authorPaint.strokeWidth = authorPaint.textSize / 5
            startX = width * 0.8f
            startY = viewHeight * 0.95f - author.size * authorPaint.textHeight
            startY = maxOf(startY, viewHeight * 0.3f)
            author.forEach {
                authorPaint.color = Color.WHITE
                authorPaint.style = Paint.Style.STROKE
                canvas.drawText(it, startX, startY, authorPaint)
                authorPaint.color = context.accentColor
                authorPaint.style = Paint.Style.FILL
                canvas.drawText(it, startX, startY, authorPaint)
                startY += authorPaint.textHeight
                if (startY > viewHeight * 0.95) {
                    return@let
                }
            }
        }
    }

    fun setHeight(height: Int) {
        val width = height * 5 / 7
        minimumWidth = width
    }

    private val glideListener by lazy {
        object : RequestListener<Drawable> {

            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                defaultCover = true
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                defaultCover = false
                return false
            }

        }
    }

    /**
     * 通过 load 方法封装 Glide 图片加载流程，支持：
     * 传入封面路径、书名、作者等参数，自动关联默认封面文字。
     * 加载状态监听：成功加载图片时隐藏文字（defaultCover = false），失败时显示默认封面文字（defaultCover = true）。
     * 支持仅 WiFi 加载、来源标识等高级配置，适配应用网络策略。
     */
    fun load(
        path: String? = null,
        name: String? = null,
        author: String? = null,
        loadOnlyWifi: Boolean = false,
        sourceOrigin: String? = null,
        fragment: Fragment? = null,
        lifecycle: Lifecycle? = null,
        onLoadFinish: (() -> Unit)? = null
    ) {
        this.bitmapPath = path
        this.name = name?.replace(AppPattern.bdRegex, "")?.trim()
        this.author = author?.replace(AppPattern.bdRegex, "")?.trim()
        defaultCover = true
        invalidate()
        if (AppConfig.useDefaultCover) {
            ImageLoader.load(context, BookCover.defaultDrawable)
                .centerCrop()
                .into(this)
        } else {
            var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
            if (sourceOrigin != null) {
                options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
            }
            var builder = if (fragment != null && lifecycle != null) {
                ImageLoader.load(fragment, lifecycle, path)
            } else {
                ImageLoader.load(context, path)//Glide自动识别http://,content://和file://
            }
            builder = builder.apply(options)
                .placeholder(BookCover.defaultDrawable)
                .error(BookCover.defaultDrawable)
                .listener(glideListener)
            if (onLoadFinish != null) {
                builder = builder.addListener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean
                    ): Boolean {
                        onLoadFinish.invoke()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable?>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        onLoadFinish.invoke()
                        return false
                    }
                })
            }
            builder
                .centerCrop()
                .into(this)
        }
    }

}
