package jp.dopaccess.widget.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.store by preferencesDataStore("dopaccess")

data class Settings(
    val workerUrl: String,
    val apiSecret: String?,
    val property: String,
    val intervalMin: Int,
    /** absolute = crowd size follows raw user count instead of the usual-for-this-hour baseline. */
    val absoluteScale: Boolean = false,
    /** mild = gentler visuals (KYOMU naps instead of collapsing). */
    val mild: Boolean = false,
)

/** refreshing = a tap-triggered fetch is in flight (shown as a spinner). */
data class Snapshot(val response: RealtimeResponse?, val error: String?, val updatedAtMs: Long, val refreshing: Boolean = false)

class SettingsStore(private val ctx: Context) {
    private object K {
        val url = stringPreferencesKey("worker_url")
        val secret = stringPreferencesKey("api_secret_enc")
        val property = stringPreferencesKey("property")
        val interval = intPreferencesKey("interval_min")
        val absolute = booleanPreferencesKey("absolute_scale")
        val mild = booleanPreferencesKey("mild")
        val lastJson = stringPreferencesKey("last_json")
        val lastError = stringPreferencesKey("last_error")
        val lastAt = longPreferencesKey("last_at")
        val lastManual = longPreferencesKey("last_manual")
        val refreshingAt = longPreferencesKey("refreshing_at")
    }

    suspend fun settings(): Settings {
        val p = ctx.store.data.first()
        return Settings(
            workerUrl = p[K.url].orEmpty(),
            apiSecret = p[K.secret]?.let(SecretCrypto::decrypt),
            property = p[K.property].orEmpty(),
            intervalMin = p[K.interval] ?: 30,
            absoluteScale = p[K.absolute] ?: false,
            mild = p[K.mild] ?: false,
        )
    }

    suspend fun save(url: String, secret: String?, property: String, intervalMin: Int, absoluteScale: Boolean, mild: Boolean) {
        ctx.store.edit {
            it[K.url] = url.trim().trimEnd('/')
            if (!secret.isNullOrBlank()) it[K.secret] = SecretCrypto.encrypt(secret.trim())
            it[K.property] = property.trim()
            it[K.interval] = intervalMin
            it[K.absolute] = absoluteScale
            it[K.mild] = mild
        }
    }

    suspend fun snapshot(): Snapshot {
        val p = ctx.store.data.first()
        val r = p[K.lastJson]?.let { runCatching { ApiJson.decodeFromString<RealtimeResponse>(it) }.getOrNull() }
        // Give up on the spinner after a minute in case the fetch never ran (e.g. offline).
        val refreshing = System.currentTimeMillis() - (p[K.refreshingAt] ?: 0L) < 60_000
        return Snapshot(r, p[K.lastError], p[K.lastAt] ?: 0L, refreshing)
    }

    suspend fun saveResult(r: RealtimeResponse?, error: String?) {
        ctx.store.edit {
            it.remove(K.refreshingAt)
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
                it[K.refreshingAt] = now
                ok = true
            }
        }
        return ok
    }
}
