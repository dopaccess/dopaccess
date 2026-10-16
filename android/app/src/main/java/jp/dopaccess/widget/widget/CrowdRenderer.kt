package jp.dopaccess.widget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import jp.dopaccess.widget.data.RealtimeResponse
import jp.dopaccess.widget.data.Tier
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Draws the widget picture. Placeholder vector figures until real sprites land. */
object CrowdRenderer {
    private const val MAX_EDGE = 600

    private data class Palette(val top: Int, val bottom: Int, val ground: Int, val people: IntArray, val text: Int)

    private fun palette(tier: Tier, dark: Boolean): Palette = when (tier) {
        Tier.KYOMU, Tier.ERROR -> Palette(0xFF3A3A3A.toInt(), 0xFF1E1E1E.toInt(), 0xFF2A2A2A.toInt(),
            intArrayOf(0xFF9E9E9E.toInt(), 0xFF8A8A8A.toInt(), 0xFFB0B0B0.toInt(), 0xFF7A7A7A.toInt()), Color.WHITE)
        Tier.GOD_BURST, Tier.BAKU_AGE -> Palette(0xFFFF8A00.toInt(), 0xFFFF2D6F.toInt(), 0xFF7A1F3D.toInt(),
            intArrayOf(0xFFFFF176.toInt(), 0xFFFFFFFF.toInt(), 0xFF80DEEA.toInt(), 0xFFB9F6CA.toInt()), Color.WHITE)
        else -> if (dark) Palette(0xFF1B2A49.toInt(), 0xFF0D1526.toInt(), 0xFF22344F.toInt(),
            intArrayOf(0xFF64B5F6.toInt(), 0xFFFFB74D.toInt(), 0xFF81C784.toInt(), 0xFFE57373.toInt()), Color.WHITE)
        else Palette(0xFF8FD3FF.toInt(), 0xFFE6F6FF.toInt(), 0xFFB7E4A1.toInt(),
            intArrayOf(0xFF1E88E5.toInt(), 0xFFFB8C00.toInt(), 0xFF43A047.toInt(), 0xFFE53935.toInt()), 0xFF102030.toInt())
    }

    /** faded = stale or last fetch failed: wash the picture out so it reads as old. */
    fun render(widthPx: Int, heightPx: Int, r: RealtimeResponse?, dark: Boolean, faded: Boolean = false): Bitmap {
        val s = min(1f, MAX_EDGE.toFloat() / max(widthPx, heightPx))
        val w = max(1, (widthPx * s).toInt())
        val h = max(1, (heightPx * s).toInt())
        return try {
            draw(w, h, r, dark, faded)
        } catch (e: OutOfMemoryError) {
            draw(w / 2, h / 2, r, dark, faded)
        }
    }

    private fun draw(w: Int, h: Int, r: RealtimeResponse?, dark: Boolean, faded: Boolean): Bitmap {
        val tier = if (r == null) Tier.ERROR else Tier.parse(r.tier)
        val pal = palette(tier, dark)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), pal.top, pal.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        if (tier == Tier.GOD_BURST) drawRays(c, w, h, p)

        // Sky only needs room for the tier label; the crowd gets the rest.
        val groundTop = h * 0.36f
        p.color = pal.ground
        c.drawRect(0f, groundTop, w.toFloat(), h.toFloat(), p)

        if (r == null) {
            drawFigure(c, p, w * 0.5f, h * 0.85f, h * 0.3f, pose = -1, color = pal.people[0])
        } else {
            val band = h - groundTop
            val bottomPad = h * 0.07f
            val n = max(1, r.crowd.walkers + r.crowd.giants * 4)
            // Size figures from the space each one gets, so a wide widget isn't sparse.
            val unit = (sqrt(w * band / n) * 0.85f).coerceIn(h * 0.16f, h * 0.34f)
            val seed = r.fetchedAt.hashCode().toLong()
            for (f in CrowdLayout.place(r.crowd.walkers, r.crowd.giants, tier, seed)) {
                val depth = 0.75f + 0.35f * f.y
                val size = min(unit * f.scale * depth, band * 0.95f)
                // Feet stay inside the ground band; heads may overlap the horizon a little.
                val minFoot = groundTop + size * 0.45f
                val maxFoot = h - bottomPad
                val footY = if (maxFoot > minFoot) minFoot + (maxFoot - minFoot) * f.y else maxFoot
                val x = (size * 0.3f) + (w - size * 0.6f) * f.x
                val pose = if (tier == Tier.KYOMU) -2 else f.pose
                drawFigure(c, p, x, footY, size, pose, pal.people[f.color])
            }
        }
        if (faded) {
            p.color = 0x88808080.toInt()
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        return bmp
    }

    private fun drawRays(c: Canvas, w: Int, h: Int, p: Paint) {
        p.color = 0x33FFFFFF
        val cx = w / 2f
        val cy = h * 0.4f
        val len = max(w, h).toFloat()
        p.strokeWidth = w / 30f
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            c.drawLine(cx, cy, cx + (cos(a) * len).toFloat(), cy + (sin(a) * len).toFloat(), p)
        }
    }

    /** pose -1 = error (sitting), -2 = lying down (KYOMU), 0..7 = walking variants. */
    private fun drawFigure(c: Canvas, p: Paint, x: Float, footY: Float, size: Float, pose: Int, color: Int) {
        p.color = color
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = size * 0.14f
        val head = size * 0.16f
        if (pose == -2) {
            c.drawLine(x - size * 0.35f, footY, x + size * 0.25f, footY, p)
            c.drawCircle(x + size * 0.4f, footY - head * 0.3f, head, p)
            return
        }
        val hip = footY - size * 0.4f
        val neck = footY - size * 0.75f
        if (pose == -1) {
            c.drawLine(x, hip, x, neck, p)
            c.drawLine(x, hip, x + size * 0.3f, hip, p)
            c.drawCircle(x, neck - head, head, p)
            return
        }
        val stride = size * 0.12f * ((pose % 4) - 1.5f)
        c.drawLine(x, hip, x - stride, footY, p)
        c.drawLine(x, hip, x + stride, footY, p)
        c.drawLine(x, hip, x, neck, p)
        val arm = if (pose >= 4) -size * 0.25f else size * 0.05f
        c.drawLine(x, neck + size * 0.05f, x - size * 0.18f, neck + size * 0.05f + arm, p)
        c.drawLine(x, neck + size * 0.05f, x + size * 0.18f, neck + size * 0.05f + arm, p)
        c.drawCircle(x, neck - head, head, p)
    }
}
