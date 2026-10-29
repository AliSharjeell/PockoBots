package dev.pocket.agent

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Native Android implementation of the Cupertino / Telegram continuous chat bubble shape
 * modeled directly after 6xingyv/compound (BubbleContinuousShape.kt).
 */
class CupertinoChatBubbleDrawable(
    private val isOutgoing: Boolean,
    private val hasTail: Boolean,
    private val primaryColor: Int,
    private val secondaryColor: Int? = null,
    private val cornerRadiusPx: Float = 44f
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
    }
    private val path = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updatePath(bounds.width().toFloat(), bounds.height().toFloat())
    }

    private fun updatePath(w: Float, h: Float) {
        path.reset()
        if (w <= 0f || h <= 0f) return

        if (secondaryColor != null && secondaryColor != primaryColor) {
            paint.shader = LinearGradient(
                0f, 0f, w, h,
                primaryColor, secondaryColor,
                Shader.TileMode.CLAMP
            )
        } else {
            paint.shader = null
            paint.color = primaryColor
        }

        val r = cornerRadiusPx.coerceAtMost(h / 2.2f).coerceAtMost(w / 2.2f)

        if (!hasTail) {
            // Symmetrical continuous rounded rectangle
            val rect = RectF(0f, 0f, w, h)
            path.addRoundRect(rect, r, r, Path.Direction.CW)
            return
        }

        // Tail math matching 6xingyv/compound BubbleContinuousShape.kt:
        val scale = r / 20f
        val tailHeight = 7f * scale
        val bodyHeight = (h - tailHeight).coerceAtLeast(r * 1.5f)

        val mainPath = Path()
        val rect = RectF(0f, 0f, w, bodyHeight)

        // Radii order: top-left, top-right, bottom-right, bottom-left (each x, y)
        val radii = if (isOutgoing) {
            floatArrayOf(r, r, r, r, 0f, 0f, r, r)
        } else {
            floatArrayOf(r, r, r, r, r, r, 0f, 0f)
        }
        mainPath.addRoundRect(rect, radii, Path.Direction.CW)

        // Diagonal cut off where the tail joins
        val cutSize = r * 0.98f
        val cutter = Path()
        if (isOutgoing) {
            cutter.moveTo(w, bodyHeight - cutSize)
            cutter.lineTo(w, bodyHeight)
            cutter.lineTo(w - cutSize, bodyHeight)
            cutter.close()
        } else {
            cutter.moveTo(0f, bodyHeight - cutSize)
            cutter.lineTo(0f, bodyHeight)
            cutter.lineTo(cutSize, bodyHeight)
            cutter.close()
        }
        mainPath.op(cutter, Path.Op.DIFFERENCE)

        val tail = Path()
        if (isOutgoing) {
            tail.moveTo(w - r, bodyHeight)
            tail.lineTo(w, bodyHeight - (20f * scale))
            tail.cubicTo(w, bodyHeight - (13.55f * scale), w - (3.06f * scale), bodyHeight - (7.81f * scale), w - (7.8f * scale), bodyHeight - (4.15f * scale))
            tail.cubicTo(w - (7.91f * scale), bodyHeight - (4.07f * scale), w - (8.01f * scale), bodyHeight - (3.99f * scale), w - (8.08f * scale), bodyHeight - (3.92f * scale))
            tail.cubicTo(w - (9.12f * scale), bodyHeight - (3f * scale), w - (10.44f * scale), bodyHeight - (1.62f * scale), w - (10.44f * scale), bodyHeight + (0.24f * scale))
            tail.cubicTo(w - (10.44f * scale), bodyHeight + (1.75f * scale), w - (10.29f * scale), bodyHeight + (2.28f * scale), w - (9.81f * scale), bodyHeight + (3.29f * scale))
            tail.cubicTo(w - (9.59f * scale), bodyHeight + (3.74f * scale), w - (9.03f * scale), bodyHeight + (4.57f * scale), w - (8.54f * scale), bodyHeight + (5.21f * scale))
            tail.cubicTo(w - (8.05f * scale), bodyHeight + (5.85f * scale), w - (8.21f * scale), bodyHeight + (7.04f * scale), w - (9.47f * scale), bodyHeight + (6.94f * scale))
            tail.cubicTo(w - (9.89f * scale), bodyHeight + (6.91f * scale), w - (11.57f * scale), bodyHeight + (6.17f * scale), w - (13.53f * scale), bodyHeight + (5.09f * scale))
            tail.cubicTo(w - (15.8f * scale), bodyHeight + (3.84f * scale), w - (18.54f * scale), bodyHeight + (1.74f * scale), w - (19.25f * scale), bodyHeight + (1.24f * scale))
            tail.cubicTo(w - (20.58f * scale), bodyHeight + (0.31f * scale), w - (21.23f * scale), bodyHeight, w - (22.22f * scale), bodyHeight)
        } else {
            tail.moveTo(r, bodyHeight)
            tail.lineTo(0f, bodyHeight - (20f * scale))
            tail.cubicTo(0f, bodyHeight - (13.55f * scale), 3.06f * scale, bodyHeight - (7.81f * scale), 7.8f * scale, bodyHeight - (4.15f * scale))
            tail.cubicTo(7.91f * scale, bodyHeight - (4.07f * scale), 8.01f * scale, bodyHeight - (3.99f * scale), 8.08f * scale, bodyHeight - (3.92f * scale))
            tail.cubicTo(9.12f * scale, bodyHeight - (3f * scale), 10.44f * scale, bodyHeight - (1.62f * scale), 10.44f * scale, bodyHeight + (0.24f * scale))
            tail.cubicTo(10.44f * scale, bodyHeight + (1.75f * scale), 10.29f * scale, bodyHeight + (2.28f * scale), 9.81f * scale, bodyHeight + (3.29f * scale))
            tail.cubicTo(9.59f * scale, bodyHeight + (3.74f * scale), 9.03f * scale, bodyHeight + (4.57f * scale), 8.54f * scale, bodyHeight + (5.21f * scale))
            tail.cubicTo(8.05f * scale, bodyHeight + (5.85f * scale), 8.21f * scale, bodyHeight + (7.04f * scale), 9.47f * scale, bodyHeight + (6.94f * scale))
            tail.cubicTo(9.89f * scale, bodyHeight + (6.91f * scale), 11.57f * scale, bodyHeight + (6.17f * scale), 13.53f * scale, bodyHeight + (5.09f * scale))
            tail.cubicTo(15.8f * scale, bodyHeight + (3.84f * scale), 18.54f * scale, bodyHeight + (1.74f * scale), 19.25f * scale, bodyHeight + (1.24f * scale))
            tail.cubicTo(20.58f * scale, bodyHeight + (0.31f * scale), 21.23f * scale, bodyHeight, 22.22f * scale, bodyHeight)
        }
        tail.close()

        mainPath.op(tail, Path.Op.UNION)
        path.set(mainPath)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
