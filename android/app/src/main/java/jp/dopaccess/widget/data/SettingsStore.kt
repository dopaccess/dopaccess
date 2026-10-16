package jp.dopaccess.widget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.store by preferencesDataStore("dopaccess")

/** Everything stays on this phone: which property to show, how often, and the last reading. */
data class Settings(val propertyId: String, val propertyName: String, val intervalMin: Int)

/** refreshing = a tap-triggered fetch is in flight (shown as a spinner). */
data class Snapshot(val reading: Reading?, val error: String?, val refreshing: Boolean = false)

class SettingsStore(private val ctx: Context) {
    private object K {
        val propertyId = stringPreferencesKey("property_id")
        val propertyName = stringPreferencesKey("property_name")
        val interval = intPreferencesKey("interval_min")
        val lastReading = stringPreferencesKey("last_reading")
        val lastError = stringPreferencesKey("last_error")
        val lastManual = longPreferencesKey("last_manual")
        val refreshingAt = longPreferencesKey("refreshing_at")
    }

    suspend fun settings(): Settings {
        val p = ctx.store.data.first()
        return Settings(p[K.propertyId].orEmpty(), p[K.propertyName].orEmpty(), p[K.interval] ?: 30)
    }

    suspend fun save(property: Property?, intervalMin: Int) {
        ctx.store.edit {
            if (property != null) {
                if (it[K.propertyId] != property.id) { it.remove(K.lastReading); it.remove(K.lastError) }
                it[K.propertyId] = property.id
                it[K.propertyName] = property.name
            }
            it[K.interval] = intervalMin
        }
    }

    suspend fun snapshot(): Snapshot {
        val p = ctx.store.data.first()
        val r = p[K.lastReading]?.let { runCatching { AppJson.decodeFromString(Reading.serializer(), it) }.getOrNull() }
        // Give up on the spinner after a minute in case the fetch never ran (e.g. offline).
        val refreshing = System.currentTimeMillis() - (p[K.refreshingAt] ?: 0L) < 60_000
        return Snapshot(r, p[K.lastError], refreshing)
    }

    suspend fun saveResult(r: Reading?, error: String?) {
        ctx.store.edit {
            it.remove(K.refreshingAt)
            if (r != null) {
                it[K.lastReading] = AppJson.encodeToString(Reading.serializer(), r)
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
