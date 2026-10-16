package jp.dopaccess.widget.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Crowd(val unit: Int, val walkers: Int, val giants: Int)

/** pref: 1..47 = prefecture (JIS code), 0 = Japan but unknown place, -1 = overseas. */
@Serializable
data class Spot(val pref: Int, val device: String, val users: Int)

@Serializable
data class Geo(val mobile: Int = 0, val desktop: Int = 0, val overseas: Int = 0, val spots: List<Spot> = emptyList())

@Serializable
data class RealtimeResponse(
    val property: String,
    val tier: String,
    val current: Int,
    val baseline: Double? = null,
    val divergence: Double = 0.0,
    val crowd: Crowd,
    val timezone: String,
    val fetchedAt: String,
    val cached: Boolean = false,
    val stale: Boolean = false,
    val geo: Geo? = null,
)

val ApiJson = Json { ignoreUnknownKeys = true }

/** The tiers the widget knows how to draw. Unknown values fall back to NORMAL. */
enum class Tier { KYOMU, KANKOTORI, NORMAL, WAKUWAKU, BAKU_AGE, GOD_BURST, ERROR;
    companion object {
        fun parse(s: String?): Tier = entries.firstOrNull { it.name == s } ?: NORMAL
    }
}
