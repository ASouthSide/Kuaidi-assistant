package com.kuaidi.assistant

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.max

/** Samples a low-resolution wallpaper only beneath the translucent panel. */
internal class FrostedPanelBackground(
    private val panel: View,
    private val wallpaper: View,
    radius: Int,
    private val bottomInset: Int = 0
) : Drawable() {
    private val glass = requireNotNull(panel.background).mutate()
    private val radiusPx = radius.toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val transform = Matrix()
    private val wallpaperPosition = IntArray(2)
    private val panelPosition = IntArray(2)
    private var texture: Bitmap? = null

    init {
        panel.background = this
    }

    fun setTexture(bitmap: Bitmap?) {
        texture = bitmap
        paint.shader = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val source = texture
        if (source != null && wallpaper.width > 0 && wallpaper.height > 0) {
            wallpaper.getLocationInWindow(wallpaperPosition)
            panel.getLocationInWindow(panelPosition)
            val scale = max(wallpaper.width.toFloat() / source.width, wallpaper.height.toFloat() / source.height)
            val x = wallpaperPosition[0] - panelPosition[0] + (wallpaper.width - source.width * scale) / 2
            val y = wallpaperPosition[1] - panelPosition[1] + (wallpaper.height - source.height * scale) / 2
            transform.setScale(scale, scale)
            transform.postTranslate(x, y)
            paint.shader?.setLocalMatrix(transform)
            canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(),
                (bounds.bottom - bottomInset).toFloat(), radiusPx, radiusPx, paint)
        }
        glass.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        glass.bounds = bounds
    }

    override fun getPadding(padding: Rect): Boolean = glass.getPadding(padding)

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom - bottomInset, radiusPx)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        glass.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter
        glass.colorFilter = filter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

