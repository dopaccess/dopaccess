package jp.dopaccess.widget.widget

import jp.dopaccess.widget.data.Tier
import kotlin.random.Random

/** One figure on the stage, in 0..1 coordinates (x left→right, y top→bottom of the ground band). */
data class Figure(val x: Float, val y: Float, val scale: Float, val pose: Int, val color: Int, val giant: Boolean)

/**
 * Pure placement logic, kept free of Android types so it can be unit-tested on the JVM.
 * The seed comes from fetchedAt, so the same API response always draws the same picture.
 */
object CrowdLayout {
    const val POSES = 8
    const val COLORS = 4
    const val MAX_WALKERS = 40

    fun place(walkers: Int, giants: Int, tier: Tier, seed: Long): List<Figure> {
        val rnd = Random(seed)
        val n = walkers.coerceIn(0, MAX_WALKERS)
        val g = giants.coerceIn(0, 3)
        val people = List(n) {
            Figure(
                x = rnd.nextFloat() * 0.9f + 0.05f,
                y = rnd.nextFloat(),
                scale = 0.8f + rnd.nextFloat() * 0.4f,
                pose = if (tier == Tier.KYOMU) 0 else rnd.nextInt(POSES),
                color = rnd.nextInt(COLORS),
                giant = false,
            )
        }
        val big = List(g) { i ->
            Figure(x = (i + 1f) / (g + 1f), y = 0.5f, scale = 2.6f, pose = rnd.nextInt(3), color = 0, giant = true)
        }
        // Draw back-to-front: smaller y is farther away.
        return (people + big).sortedBy { it.y }
    }
}
