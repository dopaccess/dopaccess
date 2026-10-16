package jp.dopaccess.widget.widget

import jp.dopaccess.widget.data.Spot
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** One sprite to draw. pref -1 = overseas box, 0 is resolved to central Honshu. */
data class Figure(val pref: Int, val desktop: Boolean, val people: Int)

object MapLayout {
    const val MAX_FIGURES = 40
    private const val UNKNOWN_PREF = 20 // Nagano: roughly the middle of Honshu

    /**
     * Turns spots into sprites. When there are more people than [maxFigures],
     * one sprite stands for several people so busy prefectures don't become a blob.
     */
    fun figures(spots: List<Spot>, maxFigures: Int = MAX_FIGURES): List<Figure> {
        val live = spots.filter { it.users > 0 }
        val total = live.sumOf { it.users }
        if (total == 0) return emptyList()
        val per = max(1, ceil(total / maxFigures.toDouble()).toInt())
        val out = ArrayList<Figure>()
        for (s in live) {
            val pref = if (s.pref == 0 || s.pref > 47 || s.pref < -1) UNKNOWN_PREF else s.pref
            // Every spot gets at least one sprite; big ones get proportionally more (max 6 per spot).
            val n = min(6, max(1, s.users / per))
            var left = s.users
            for (i in 0 until n) {
                val share = if (i == n - 1) left else s.users / n
                left -= share
                out += Figure(pref, s.device == "desktop", share)
            }
        }
        return out
    }
}
