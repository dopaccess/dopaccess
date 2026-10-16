package jp.dopaccess.widget.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One realtime reading: active users in the last 30 minutes, split by device. Tablets count as phones. */
@Serializable
data class Reading(val total: Int, val desktop: Int, val mobile: Int, val fetchedAtMs: Long)

/** A GA4 property the signed-in user can read. */
@Serializable
data class Property(val id: String, val name: String, val account: String)

val AppJson = Json { ignoreUnknownKeys = true }
