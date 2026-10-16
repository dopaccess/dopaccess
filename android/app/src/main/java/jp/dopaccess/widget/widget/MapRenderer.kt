package jp.dopaccess.widget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import jp.dopaccess.widget.R
import jp.dopaccess.widget.data.RealtimeResponse
import jp.dopaccess.widget.data.Spot
import jp.dopaccess.widget.data.Tier
import java.util.Random
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the pixel-art map of Japan with one little person per visitor (or per few visitors).
 * Everything is drawn on a small canvas first and then scaled up without filtering,
 * so the pixels stay crisp at any widget size.
 */
object MapRenderer {
    private const val MAX_EDGE = 1100
    private const val SEA = 0xFF1C3A62.toInt()
    private const val TILE = 16

    private var map: Bitmap? = null
    private var people: Bitmap? = null

    private fun load(ctx: Context) {
        if (map != null && people != null) return
        val o = BitmapFactory.Options().apply { inScaled = false }
        map = BitmapFactory.decodeResource(ctx.resources, R.drawable.jp_map, o)
        people = BitmapFactory.decodeResource(ctx.resources, R.drawable.people, o)
    }

    fun render(ctx: Context, widthPx: Int, heightPx: Int, r: RealtimeResponse?, faded: Boolean, mild: Boolean): Bitmap {
        load(ctx)
        return try {
            draw(widthPx, heightPx, r, faded, mild, MAX_EDGE)
        } catch (e: OutOfMemoryError) {
            draw(widthPx, heightPx, r, faded, mild, MAX_EDGE / 2)
        }
    }

    private fun draw(widthPx: Int, heightPx: Int, r: RealtimeResponse?, faded: Boolean, mild: Boolean, maxEdge: Int): Bitmap {
        val base = map!!
        val sheet = people!!
        val aspect = max(1, widthPx).toFloat() / max(1, heightPx)
        // Low-res canvas with the widget's aspect ratio; the map keeps its own shape inside it.
        val lw = max(MapData.W, (MapData.H * aspect).roundToInt())
        val lh = max(MapData.H, (MapData.W / aspect).roundToInt())
        val ox = (lw - MapData.W) / 2
        val oy = lh - MapData.H // extra sea goes on top, where the count sits

        val low = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        val c = Canvas(low)
        val px = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
        c.drawColor(SEA)
        c.drawBitmap(base, ox.toFloat(), oy.toFloat(), px)

        // Overseas box, bottom-right.
        val box = RectF((lw - MapData.OS_W - 4).toFloat(), (lh - MapData.OS_H - 4).toFloat(), (lw - 4).toFloat(), (lh - 4).toFloat())
        px.color = 0xFF14284A.toInt(); px.style = Paint.Style.FILL
        c.drawRoundRect(box, 4f, 4f, px)
        px.color = 0xFFDCE6F0.toInt(); px.style = Paint.Style.STROKE; px.strokeWidth = 1f
        c.drawRoundRect(box, 4f, 4f, px)
        px.style = Paint.Style.FILL

        val tier = if (r == null) Tier.ERROR else Tier.parse(r.tier)
        val rnd = Random((r?.fetchedAt ?: "").hashCode().toLong())
        val spots = r?.geo?.spots ?: fallbackSpots(r?.crowd?.walkers ?: 0)
        data class Placed(val x: Int, val y: Int, val f: Figure)
        val placed = MapLayout.figures(spots).map { f ->
            if (f.pref == -1) {
                Placed((box.left + 10 + rnd.nextFloat() * (box.width() - 20)).toInt(), (box.bottom - 2).toInt(), f)
            } else {
                val ax = ox + MapData.prefX[f.pref - 1]
                val ay = oy + MapData.prefY[f.pref - 1]
                Placed((ax + rnd.nextGaussian() * 3).toInt(), (ay + rnd.nextGaussian() * 2 + 4).toInt(), f)
            }
        }.sortedBy { it.y }

        val badges = ArrayList<Placed>()
        for (p in placed) {
            val ch = rnd.nextInt(6)
            val x = p.x - TILE / 2
            val y = p.y - TILE
            if (p.f.desktop) {
                blit(c, sheet, ch * 3, 1, x, y, px)
                // a little monitor in front of them
                rect(c, px, x + 3, y + 9, x + 12, y + 15, 0xFF282830.toInt())
                rect(c, px, x + 4, y + 10, x + 11, y + 13, 0xFF78DCFF.toInt())
                rect(c, px, x + 6, y + 15, x + 9, y + 16, 0xFF282830.toInt())
            } else {
                val frame = 1 + rnd.nextInt(2)
                val dir = if (rnd.nextBoolean()) 0 else 3
                blit(c, sheet, ch * 3 + frame, dir, x, y, px)
                rect(c, px, x + 11, y + 7, x + 13, y + 11, 0xFF1E1E1E.toInt())
                rect(c, px, x + 12, y + 8, x + 12, y + 8, 0xFF96F0FF.toInt())
            }
            if (p.f.people > 1) badges += p
        }

        val confetti = when (tier) { Tier.GOD_BURST -> 70; Tier.BAKU_AGE -> 40; Tier.WAKUWAKU -> 15; else -> 0 }
        val colors = intArrayOf(0xFFFFF176.toInt(), 0xFF80DEEA.toInt(), 0xFFB9F6CA.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF8A80.toInt())
        repeat(confetti) {
            val x = rnd.nextInt(lw); val y = rnd.nextInt(lh)
            rect(c, px, x, y, x + 1, y + 2, colors[rnd.nextInt(colors.size)])
        }

        // Scale up by a whole number so every pixel is a clean square.
        val k = max(1, min(ceil(widthPx / lw.toFloat()).toInt(), maxEdge / max(lw, lh)))
        val out = Bitmap.createBitmap(lw * k, lh * k, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val up = Paint().apply { isFilterBitmap = false }
        if ((tier == Tier.KYOMU && !mild) || tier == Tier.ERROR) {
            up.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        oc.drawBitmap(low, Rect(0, 0, lw, lh), Rect(0, 0, lw * k, lh * k), up)
        low.recycle()

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(k * 1.5f, k * 0.6f, k * 0.6f, 0xCC000000.toInt())
        }
        text.textSize = 9f * k
        val os = r?.geo?.overseas ?: 0
        oc.drawText("海外 $os", (box.left + 3) * k, (box.top + 10) * k, text)
        text.textSize = 6.5f * k
        for (b in badges) oc.drawText("×${b.f.people}", (b.x + 5f) * k, (b.y - 12f) * k, text)

        if (faded) oc.drawColor(0x88707070.toInt())
        return out
    }

    private fun blit(c: Canvas, sheet: Bitmap, row: Int, col: Int, x: Int, y: Int, p: Paint) {
        val src = Rect(col * TILE, row * TILE, col * TILE + TILE, row * TILE + TILE)
        c.drawBitmap(sheet, src, Rect(x, y, x + TILE, y + TILE), p)
    }

    private fun rect(c: Canvas, p: Paint, l: Int, t: Int, r: Int, b: Int, color: Int) {
        p.color = color
        c.drawRect(l.toFloat(), t.toFloat(), (r + 1).toFloat(), (b + 1).toFloat(), p)
    }

    /** Older relays don't send geo: scatter the crowd over the big cities instead. */
    private fun fallbackSpots(n: Int): List<Spot> {
        if (n <= 0) return emptyList()
        val prefs = intArrayOf(13, 27, 23, 14, 1, 40, 4, 34)
        return prefs.mapIndexed { i, p -> Spot(p, "mobile", n / prefs.size + if (i < n % prefs.size) 1 else 0) }
    }
}
