package jp.dopaccess.widget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** Talks to the GA4 Data and Admin APIs straight from the phone. Nothing goes through a server of ours. */
class Ga4(private val accessToken: String) {
    class HttpError(val code: Int, body: String) : Exception("HTTP $code ${body.take(200)}")

    suspend fun realtime(propertyId: String): Reading = withContext(Dispatchers.IO) {
        val body = """{"dimensions":[{"name":"deviceCategory"}],"metrics":[{"name":"activeUsers"}]}"""
        val json = call("https://analyticsdata.googleapis.com/v1beta/properties/$propertyId:runRealtimeReport", body)
        parseRealtime(json, System.currentTimeMillis())
    }

    suspend fun properties(): List<Property> = withContext(Dispatchers.IO) {
        val out = ArrayList<Property>()
        var page: String? = null
        do {
            val q = "pageSize=200" + (page?.let { "&pageToken=$it" } ?: "")
            val json = call("https://analyticsadmin.googleapis.com/v1beta/accountSummaries?$q", null)
            out += parseProperties(json)
            page = json["nextPageToken"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
        } while (page != null)
        out
    }

    private fun call(url: String, body: String?): JsonObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code != 200) throw HttpError(code, conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
            return AppJson.parseToJsonElement(conn.inputStream.bufferedReader().use { it.readText() }).jsonObject
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun parseRealtime(json: JsonObject, now: Long): Reading {
            var desktop = 0; var mobile = 0
            json["rows"]?.jsonArray?.forEach { row ->
                val o = row.jsonObject
                val device = o["dimensionValues"]!!.jsonArray[0].jsonObject["value"]!!.jsonPrimitive.content
                val n = o["metricValues"]!!.jsonArray[0].jsonObject["value"]!!.jsonPrimitive.content.toIntOrNull() ?: 0
                if (device == "desktop") desktop += n else mobile += n
            }
            return Reading(desktop + mobile, desktop, mobile, now)
        }

        fun parseProperties(json: JsonObject): List<Property> =
            json["accountSummaries"]?.jsonArray?.flatMap { a ->
                val ao = a.jsonObject
                val account = ao["displayName"]?.jsonPrimitive?.content.orEmpty()
                ao["propertySummaries"]?.jsonArray?.map { p ->
                    val po = p.jsonObject
                    Property(po["property"]!!.jsonPrimitive.content.removePrefix("properties/"),
                        po["displayName"]?.jsonPrimitive?.content.orEmpty(), account)
                } ?: emptyList()
            } ?: emptyList()
    }
}
