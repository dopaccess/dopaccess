package jp.dopaccess.widget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DopaClient(private val settings: Settings) {
    sealed interface Result {
        data class Ok(val body: RealtimeResponse) : Result
        data class Fail(val message: String) : Result
    }

    suspend fun realtime(): Result = withContext(Dispatchers.IO) {
        if (!settings.workerUrl.startsWith("https://")) return@withContext Result.Fail("Worker URL must start with https://")
        val secret = settings.apiSecret ?: return@withContext Result.Fail("API secret is not set")
        val q = if (settings.property.isBlank()) "" else "?property=" + URLEncoder.encode(settings.property, "UTF-8")
        val conn = URL("${settings.workerUrl}/api/realtime$q").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $secret")
            val code = conn.responseCode
            if (code != 200) return@withContext Result.Fail("HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            Result.Ok(ApiJson.decodeFromString(RealtimeResponse.serializer(), text))
        } catch (e: Exception) {
            Result.Fail(e.javaClass.simpleName + ": " + (e.message ?: ""))
        } finally {
            conn.disconnect()
        }
    }
}
