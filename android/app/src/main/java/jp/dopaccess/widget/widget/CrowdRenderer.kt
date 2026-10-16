package jp.dopaccess.widget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import jp.dopaccess.widget.R
import jp.dopaccess.widget.data.RealtimeResponse
import jp.dopaccess.widget.data.Tier
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pixel-art crowd: a night sky, a lawn, and a jumble of people (Kenney RPG Urban Pack, CC0).
 * Drawn on a small canvas, then scaled up by a whole number without filtering so pixels stay square.
 */
object CrowdRenderer {
    private const val LH = 125          // low-res canvas height; width follows the widget's shape
    private const val MAX_EDGE = 1100
    private const val TILE = 16
    private const val MAX_WALKERS = 40
    private const val MAX_GIANTS = 3

    private var people: Bitmap? = null

    private class Look(val sky: IntArray, val ground: Int, val tuft: Int)

    private fun look(t: Tier) = when (t) {
        Tier.GOD_BURST, Tier.BAKU_AGE -> Look(intArrayOf(0xFFD64018.toInt(), 0xFFE83446.toInt(), 0xFFFF2D6F.toInt()), 0xFF7A1F3D.toInt(), 0xFF6A1733.toInt())
        Tier.KYOMU, Tier.ERROR -> Look(intArrayOf(0xFF3A3A3A.toInt(), 0xFF2E2E2E.toInt(), 0xFF242424.toInt()), 0xFF2C2C2C.toInt(), 0xFF262626.toInt())
        else -> Look(intArrayOf(0xFF283E6E.toInt(), 0xFF1E2E52.toInt(), 0xFF141F3A.toInt()), 0xFF345C3E.toInt(), 0xFF2C5036.toInt())
    }

    fun render(ctx: Context, widthPx: Int, heightPx: Int, r: RealtimeResponse?, faded: Boolean, mild: Boolean): Bitmap {
        if (people == null) {
            people = BitmapFactory.decodeResource(ctx.resources, R.drawable.people, BitmapFactory.Options().apply { inScaled = false })
        }
        return try {
            draw(widthPx, heightPx, r, faded, mild, MAX_EDGE)
        } catch (e: OutOfMemoryError) {
            draw(widthPx, heightPx, r, faded, mild, MAX_EDGE / 2)
        }
    }

    private fun draw(widthPx: Int, heightPx: Int, r: RealtimeResponse?, faded: Boolean, mild: Boolean, maxEdge: Int): Bitmap {
        val sheet = people!!
        val aspect = max(1, widthPx).toFloat() / max(1, heightPx)
        val lw = max(40, (LH * aspect).roundToInt())
        val lh = LH
        val tier = if (r == null) Tier.ERROR else Tier.parse(r.tier)
        val lk = look(tier)
        val rnd = Random((r?.fetchedAt ?: "").hashCode())

        val low = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        val c = Canvas(low)
        val px = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
        val gt = (lh * 0.42f).toInt()

        for (i in 0 until 3) rect(c, px, 0, i * gt / 3, lw, (i + 1) * gt / 3, lk.sky[i])
        when (tier) {
            Tier.GOD_BURST, Tier.BAKU_AGE -> {
                // sun rays rising from the horizon
                px.color = 0x66FFBE5A
                val cx = lw * 0.65f; val cy = gt.toFloat()
                for (i in 0 until 16) {
                    val a = Math.toRadians(180.0 + i * 12)
                    val path = Path().apply {
                        moveTo(cx, cy)
                        lineTo(cx + cos(a - 0.06).toFloat() * 400, cy + sin(a - 0.06).toFloat() * 400)
                        lineTo(cx + cos(a + 0.06).toFloat() * 400, cy + sin(a + 0.06).toFloat() * 400)
                        close()
                    }
                    c.drawPath(path, px)
                }
            }
            Tier.KYOMU, Tier.ERROR -> Unit
            else -> repeat(25) { val x = rnd.nextInt(lw); val y = rnd.nextInt(gt); rect(c, px, x, y, x, y, 0xFFFFFFDC.toInt()) }
        }
        rect(c, px, 0, gt, lw, lh, lk.ground)
        repeat(lw * lh / 300) { val x = rnd.nextInt(lw); val y = gt + 2 + rnd.nextInt(max(1, lh - gt - 2)); rect(c, px, x, y, x + 2, y, lk.tuft) }

        // Who shows up: walkers (and giants when it's really busy) come from the relay.
        val walkers = min(MAX_WALKERS, r?.crowd?.walkers ?: 0)
        val giants = min(MAX_GIANTS, r?.crowd?.giants ?: 0)
        class Fig(val x: Int, val foot: Int, val scale: Int)
        val figs = ArrayList<Fig>()
        repeat(walkers) {
            val y = rnd.nextFloat()
            val scale = if (y > 0.5f) 2 else 1
            figs += Fig(4 + (rnd.nextFloat() * (lw - 8 - TILE * scale)).toInt(), gt + 8 + (y * (lh - gt - 10)).toInt(), scale)
        }
        repeat(giants) {
            figs += Fig(4 + (rnd.nextFloat() * (lw - 8 - TILE * 3)).toInt(), gt + 14 + rnd.nextInt(max(1, lh - gt - 16)), 3)
        }
        val gray = (tier == Tier.KYOMU && !mild) || tier == Tier.ERROR
        for (f in figs.sortedBy { it.foot }) {
            val ch = rnd.nextInt(6)
            val s = TILE * f.scale
            when {
                tier == Tier.GOD_BURST || tier == Tier.BAKU_AGE -> {
                    val lift = intArrayOf(0, 3, 6)[rnd.nextInt(3)] * f.scale
                    blit(c, sheet, ch * 3 + 1 + rnd.nextInt(2), 1, f.x, f.foot - s - lift, s, px)
                }
                tier == Tier.KYOMU && !mild -> {
                    // collapsed on the lawn
                    c.save()
                    c.rotate(if (rnd.nextBoolean()) 90f else -90f, f.x + s / 2f, f.foot - s / 2f + 4)
                    blit(c, sheet, ch * 3, 1 + rnd.nextInt(2), f.x, f.foot - s + 4, s, px)
                    c.restore()
                }
                else -> blit(c, sheet, ch * 3 + rnd.nextInt(3), intArrayOf(0, 3, 1)[rnd.nextInt(3)], f.x, f.foot - s, s, px)
            }
        }

        val confetti = when (tier) { Tier.GOD_BURST -> 70; Tier.BAKU_AGE -> 40; Tier.WAKUWAKU -> 15; else -> 0 }
        val colors = intArrayOf(0xFFFFF176.toInt(), 0xFF80DEEA.toInt(), 0xFFB9F6CA.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF8A80.toInt())
        repeat(confetti) {
            val x = rnd.nextInt(lw); val y = rnd.nextInt(lh)
            if (x < 70 && y < 40) return@repeat // keep the count readable
            rect(c, px, x, y, x + 1, y + 2, colors[rnd.nextInt(colors.size)])
        }

        // Scale up by a whole number so every pixel is a clean square.
        val k = max(1, min(ceil(widthPx / lw.toFloat()).toInt(), maxEdge / max(lw, lh)))
        val out = Bitmap.createBitmap(lw * k, lh * k, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val up = Paint().apply { isFilterBitmap = false }
        if (gray) up.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        oc.drawBitmap(low, Rect(0, 0, lw, lh), Rect(0, 0, lw * k, lh * k), up)
        low.recycle()
        if (faded) oc.drawColor(0x88707070.toInt())
        return out
    }

    /** people.png: columns 0 left, 1 front, 2 back, 3 right; row = character * 3 + frame. */
    private fun blit(c: Canvas, sheet: Bitmap, row: Int, col: Int, x: Int, y: Int, size: Int, p: Paint) {
        val src = Rect(col * TILE, row * TILE, col * TILE + TILE, row * TILE + TILE)
        c.drawBitmap(sheet, src, Rect(x, y, x + size, y + size), p)
    }

    private fun rect(c: Canvas, p: Paint, l: Int, t: Int, r: Int, b: Int, color: Int) {
        p.color = color
        c.drawRect(l.toFloat(), t.toFloat(), (r + 1).toFloat(), (b + 1).toFloat(), p)
    }
}
