package jp.dopaccess.widget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.store by preferencesDataStore("dopaccess")

data class Settings(val workerUrl: String, val apiSecret: String?, val property: String, val intervalMin: Int)

data class Snapshot(val response: RealtimeResponse?, val error: String?, val updatedAtMs: Long)

class SettingsStore(private val ctx: Context) {
    private object K {
        val url = stringPreferencesKey("worker_url")
        val secret = stringPreferencesKey("api_secret_enc")
        val property = stringPreferencesKey("property")
        val interval = intPreferencesKey("interval_min")
        val lastJson = stringPreferencesKey("last_json")
        val lastError = stringPreferencesKey("last_error")
        val lastAt = longPreferencesKey("last_at")
        val lastManual = longPreferencesKey("last_manual")
    }

    suspend fun settings(): Settings {
        val p = ctx.store.data.first()
        return Settings(
            workerUrl = p[K.url].orEmpty(),
            apiSecret = p[K.secret]?.let(SecretCrypto::decrypt),
            property = p[K.property].orEmpty(),
            intervalMin = p[K.interval] ?: 30,
        )
    }

    suspend fun save(url: String, secret: String?, property: String, intervalMin: Int) {
        ctx.store.edit {
            it[K.url] = url.trim().trimEnd('/')
            if (!secret.isNullOrBlank()) it[K.secret] = SecretCrypto.encrypt(secret.trim())
            it[K.property] = property.trim()
            it[K.interval] = intervalMin
        }
    }

    suspend fun snapshot(): Snapshot {
        val p = ctx.store.data.first()
        val r = p[K.lastJson]?.let { runCatching { ApiJson.decodeFromString<RealtimeResponse>(it) }.getOrNull() }
        return Snapshot(r, p[K.lastError], p[K.lastAt] ?: 0L)
    }

    suspend fun saveResult(r: RealtimeResponse?, error: String?) {
        ctx.store.edit {
            if (r != null) {
                it[K.lastJson] = ApiJson.encodeToString(RealtimeResponse.serializer(), r)
                it[K.lastAt] = System.currentTimeMillis()
                it.remove(K.lastError)
            } else if (error != null) {
                it[K.lastError] = error
            }
        }
    }

    /** Returns true and records the press if the manual-refresh cooldown has passed. */
    suspend fun tryManual(cooldownMs: Long = 10_000): Boolean {
        val now = System.currentTimeMillis()
        var ok = false
        ctx.store.edit {
            if (now - (it[K.lastManual] ?: 0L) >= cooldownMs) {
                it[K.lastManual] = now
                ok = true
            }
        }
        return ok
    }
}
