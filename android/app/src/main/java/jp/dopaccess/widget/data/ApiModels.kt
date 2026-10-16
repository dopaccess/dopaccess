package jp.dopaccess.widget.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Crowd(val unit: Int, val walkers: Int, val giants: Int)

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
)

val ApiJson = Json { ignoreUnknownKeys = true }

/** The tiers the widget knows how to draw. Unknown values fall back to NORMAL. */
enum class Tier { KYOMU, KANKOTORI, NORMAL, WAKUWAKU, BAKU_AGE, GOD_BURST, ERROR;
    companion object {
        fun parse(s: String?): Tier = entries.firstOrNull { it.name == s } ?: NORMAL
    }
}
