package dev.pocket.agent

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native Android View implementing the customizable Bloub avatar generator
 * inspired by jeremy-prt/bloub (x.ai / Grok bot avatar recreation).
 *
 * Supports:
 * - 8 Body Shapes: circle, pebble, squircle, capsule, triangle, hexagon, cloud, droplet
 * - 12 Colors: ink, cream, brown, red, orange, amber, green, turquoise, blue, violet, pink, gray
 * - 16 Expressions: neutral, attentive, surprised, excited, happy, laughing, angry, sad,
 *                   scared, suspicious, confused, curious, proud, shy, unamused, sleepy
 */
class BloubView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var shapeId: String = "cercle"
        set(value) {
            field = value
            invalidate()
        }

    var colorHex: String = "#3b93f0"
        set(value) {
            field = value
            invalidate()
        }

    var expressionId: String = "neutre"
        set(value) {
            field = value
            invalidate()
        }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var workBlink: Float = 1f
        set(value) { field = value; invalidate() }

    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val availableWidth = (w - paddingLeft - paddingRight).coerceAtLeast(0f)
        val availableHeight = (h - paddingTop - paddingBottom).coerceAtLeast(0f)
        val cx = paddingLeft + availableWidth / 2f
        val cy = paddingTop + availableHeight / 2f
        val extent = when (shapeId.lowercase()) {
            "capsule", "droplet", "goutte" -> 1.18f
            "pebble", "galet", "triangle" -> 1.10f
            else -> 1f
        }
        val radius = minOf(availableWidth, availableHeight) * 0.43f / extent

        // 1. Resolve body color
        val parsedColor = try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            0xFF3B93F0.toInt()
        }
        bodyPaint.color = parsedColor

        // 3. Draw Body Shape
        drawBody(canvas, cx, cy, radius)

        // 4. Resolve eye color based on luminance
        val r = Color.red(parsedColor)
        val g = Color.green(parsedColor)
        val b = Color.blue(parsedColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        eyePaint.color = if (luminance > 0.65) 0xFF0A0A0C.toInt() else 0xFFFFFFFF.toInt()

        // 5. Draw Eyes based on expression
        canvas.save()
        canvas.scale(1f, workBlink, cx, cy)
        drawEyes(canvas, cx, cy, radius)
        canvas.restore()
    }

    private fun drawBody(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        when (shapeId.lowercase()) {
            "cercle", "circle" -> {
                canvas.drawCircle(cx, cy, r, bodyPaint)
            }
            "squircle" -> {
                val left = cx - r
                val top = cy - r
                val right = cx + r
                val bottom = cy + r
                val corner = r * 0.55f
                canvas.drawRoundRect(RectF(left, top, right, bottom), corner, corner, bodyPaint)
            }
            "galet", "pebble" -> {
                val left = cx - r * 1.02f
                val top = cy - r * 0.94f
                val right = cx + r * 1.02f
                val bottom = cy + r * 1.04f
                path.moveTo(cx, top)
                path.cubicTo(right + r * 0.05f, top + r * 0.2f, right, bottom - r * 0.2f, cx + r * 0.1f, bottom)
                path.cubicTo(left + r * 0.1f, bottom + r * 0.05f, left - r * 0.05f, cy, left + r * 0.1f, top + r * 0.3f)
                path.close()
                canvas.drawPath(path, bodyPaint)
            }
            "capsule" -> {
                val left = cx - r * 1.15f
                val top = cy - r * 0.72f
                val right = cx + r * 1.15f
                val bottom = cy + r * 0.72f
                val corner = (bottom - top) / 2f
                canvas.drawRoundRect(RectF(left, top, right, bottom), corner, corner, bodyPaint)
            }
            "triangle" -> {
                path.moveTo(cx, cy - r * 1.05f)
                path.lineTo(cx + r * 1.05f, cy + r * 0.85f)
                path.lineTo(cx - r * 1.05f, cy + r * 0.85f)
                path.close()
                val cornerPathEffect = CornerPathEffect(r * 0.35f)
                val origEffect = bodyPaint.pathEffect
                bodyPaint.pathEffect = cornerPathEffect
                canvas.drawPath(path, bodyPaint)
                bodyPaint.pathEffect = origEffect
            }
            "hexagone", "hexagon" -> {
                val points = 6
                for (i in 0 until points) {
                    val angle = (i * 60.0 - 30.0) * Math.PI / 180.0
                    val px = (cx + r * 1.02f * cos(angle)).toFloat()
                    val py = (cy + r * 1.02f * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                val cornerPathEffect = CornerPathEffect(r * 0.28f)
                val origEffect = bodyPaint.pathEffect
                bodyPaint.pathEffect = cornerPathEffect
                canvas.drawPath(path, bodyPaint)
                bodyPaint.pathEffect = origEffect
            }
            "nuage", "cloud" -> {
                // Multi-lobe cloud shape
                canvas.drawCircle(cx - r * 0.44f, cy + r * 0.15f, r * 0.52f, bodyPaint)
                canvas.drawCircle(cx + r * 0.44f, cy + r * 0.15f, r * 0.48f, bodyPaint)
                canvas.drawCircle(cx, cy + r * 0.22f, r * 0.58f, bodyPaint)
                canvas.drawCircle(cx - r * 0.22f, cy - r * 0.28f, r * 0.46f, bodyPaint)
                canvas.drawCircle(cx + r * 0.28f, cy - r * 0.22f, r * 0.42f, bodyPaint)
            }
            "goutte", "droplet" -> {
                path.moveTo(cx, cy - r * 1.15f)
                path.cubicTo(cx + r * 0.2f, cy - r * 0.5f, cx + r, cy + r * 0.1f, cx + r * 0.95f, cy + r * 0.45f)
                path.cubicTo(cx + r * 0.9f, cy + r, cx - r * 0.9f, cy + r, cx - r * 0.95f, cy + r * 0.45f)
                path.cubicTo(cx - r, cy + r * 0.1f, cx - r * 0.2f, cy - r * 0.5f, cx, cy - r * 1.15f)
                path.close()
                canvas.drawPath(path, bodyPaint)
            }
            else -> {
                canvas.drawCircle(cx, cy, r, bodyPaint)
            }
        }
    }

    private fun drawEyes(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val baseW = r * 0.22f
        val baseH = r * 0.48f
        val baseSplit = r * 0.38f

        // Parameters per expression: (wFactor, hFactor, tiltDegL, tiltDegR, yOffsetFactor, splitFactor, lidOpen)
        var wFac = 1.0f
        var hFac = 1.0f
        var tiltL = 0f
        var tiltR = 0f
        var yOff = 0f
        var splitFac = 1.0f
        var lidOpen = 1.0f
        var eyeAsym = false
        var rightEyeHFac = 1.0f

        when (expressionId.lowercase()) {
            "neutre", "neutral" -> {
                // standard calm pill eyes
            }
            "attentif", "attentive" -> {
                wFac = 0.95f
                hFac = 1.05f
                splitFac = 0.95f
                yOff = -0.05f
            }
            "surpris", "surprised" -> {
                wFac = 1.45f
                hFac = 1.35f
                splitFac = 1.15f
                yOff = -0.1f
            }
            "excite", "excited" -> {
                wFac = 1.3f
                hFac = 1.4f
                tiltL = 12f
                tiltR = -12f
                splitFac = 1.18f
                yOff = -0.15f
            }
            "heureux", "happy" -> {
                wFac = 1.15f
                hFac = 0.55f
                tiltL = -14f
                tiltR = 14f
                yOff = -0.02f
            }
            "hilare", "laughing" -> {
                wFac = 1.25f
                hFac = 0.4f
                tiltL = -20f
                tiltR = 20f
                yOff = 0.05f
            }
            "colere", "angry" -> {
                wFac = 1.15f
                hFac = 0.7f
                tiltL = 28f
                tiltR = -28f
                yOff = 0.02f
            }
            "triste", "sad" -> {
                wFac = 0.95f
                hFac = 0.9f
                tiltL = -26f
                tiltR = 26f
                yOff = 0.08f
            }
            "effraye", "scared" -> {
                wFac = 1.25f
                hFac = 1.5f
                splitFac = 1.22f
                yOff = -0.2f
            }
            "mefiant", "suspicious" -> {
                eyeAsym = true
                wFac = 0.95f
                hFac = 0.95f
                rightEyeHFac = 0.4f
                tiltL = 4f
                tiltR = -4f
            }
            "confus", "confused" -> {
                eyeAsym = true
                wFac = 0.9f
                hFac = 1.1f
                rightEyeHFac = 0.5f
                tiltL = 16f
                tiltR = 12f
            }
            "curieux", "curious" -> {
                wFac = 1.1f
                hFac = 1.1f
                tiltL = 14f
                tiltR = 14f
                yOff = -0.08f
            }
            "fier", "proud" -> {
                wFac = 1.15f
                hFac = 0.6f
                tiltL = -18f
                tiltR = 18f
                yOff = -0.12f
            }
            "timide", "shy" -> {
                wFac = 0.75f
                hFac = 0.7f
                splitFac = 0.85f
                yOff = 0.15f
            }
            "blase", "unamused" -> {
                wFac = 1.2f
                hFac = 0.32f
                yOff = 0.02f
            }
            "somnolent", "sleepy" -> {
                wFac = 0.9f
                hFac = 0.95f
                lidOpen = 0.42f
                yOff = 0.06f
            }
        }

        val lx = cx - baseSplit * splitFac
        val rx = cx + baseSplit * splitFac
        val ey = cy + r * yOff

        val eyeW = baseW * wFac
        val eyeHL = baseH * hFac * lidOpen
        val eyeHR = baseH * (if (eyeAsym) rightEyeHFac else hFac) * lidOpen

        // Left eye
        canvas.save()
        canvas.rotate(tiltL, lx, ey)
        val lRect = RectF(lx - eyeW / 2f, ey - eyeHL / 2f, lx + eyeW / 2f, ey + eyeHL / 2f)
        canvas.drawRoundRect(lRect, eyeW / 2f, eyeW / 2f, eyePaint)
        canvas.restore()

        // Right eye
        canvas.save()
        canvas.rotate(tiltR, rx, ey)
        val rRect = RectF(rx - eyeW / 2f, ey - eyeHR / 2f, rx + eyeW / 2f, ey + eyeHR / 2f)
        canvas.drawRoundRect(rRect, eyeW / 2f, eyeW / 2f, eyePaint)
        canvas.restore()
    }

    companion object {
        data class ShapeOption(val id: String, val label: String, val icon: String)
        data class ColorOption(val id: String, val hex: String, val label: String)
        data class ExpressionOption(val id: String, val label: String, val emoji: String)

        val SHAPES = listOf(
            ShapeOption("cercle", "Circle", "●"),
            ShapeOption("pebble", "Pebble", "◌"),
            ShapeOption("squircle", "Squircle", "■"),
            ShapeOption("capsule", "Capsule", "⬭"),
            ShapeOption("triangle", "Triangle", "▲"),
            ShapeOption("hexagon", "Hexagon", "⬡"),
            ShapeOption("cloud", "Cloud", "☁"),
            ShapeOption("droplet", "Droplet", "💧")
        )

        val COLORS = listOf(
            ColorOption("encre", "#0a0a0c", "Ink"),
            ColorOption("bleu", "#3b93f0", "Blue"),
            ColorOption("violet", "#8b5cf6", "Violet"),
            ColorOption("rose", "#e152b0", "Pink"),
            ColorOption("vert", "#3ecf8e", "Green"),
            ColorOption("orange", "#f08a24", "Orange"),
            ColorOption("rouge", "#e8483f", "Red"),
            ColorOption("ambre", "#f0b429", "Amber"),
            ColorOption("turquoise", "#2fbfa0", "Turquoise"),
            ColorOption("creme", "#f1efe9", "Cream"),
            ColorOption("brun", "#8b5e3c", "Brown"),
            ColorOption("gris", "#a3a3a3", "Gray")
        )

        val EXPRESSIONS = listOf(
            ExpressionOption("neutre", "Neutral", "😐"),
            ExpressionOption("heureux", "Happy", "😊"),
            ExpressionOption("hilare", "Laughing", "😄"),
            ExpressionOption("excite", "Excited", "🤩"),
            ExpressionOption("surpris", "Surprised", "😲"),
            ExpressionOption("attentif", "Attentive", "🧐"),
            ExpressionOption("curieux", "Curious", "🤔"),
            ExpressionOption("fier", "Proud", "😎"),
            ExpressionOption("confus", "Confused", "🤨"),
            ExpressionOption("mefiant", "Suspicious", "😏"),
            ExpressionOption("timide", "Shy", "🥺"),
            ExpressionOption("colere", "Angry", "😠"),
            ExpressionOption("triste", "Sad", "😢"),
            ExpressionOption("effraye", "Scared", "😨"),
            ExpressionOption("somnolent", "Sleepy", "😴"),
            ExpressionOption("blase", "Unamused", "😑")
        )
    }
}
