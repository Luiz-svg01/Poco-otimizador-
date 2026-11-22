package br.com.pocooptimizer.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

private val Context.store by preferencesDataStore("poco_preferences")
data class Preferences(val duplicates: Boolean = true, val largeMb: Int = 100, val oldDays: Int = 90)
data class History(val time: Long, val kind: String, val summary: String, val details: String)
class LocalStore(context: Context) {
    private val store = context.store
    private val duplicateKey = booleanPreferencesKey("duplicates")
    private val largeKey = intPreferencesKey("large_mb")
    private val oldKey = intPreferencesKey("old_days")
    private val historyKey = stringPreferencesKey("history")
    private val lastKey = longPreferencesKey("last_scan")
    private val data = store.data.catch { e -> if(e is java.io.IOException) emit(emptyPreferences()) else throw e }
    val preferences = data.map { Preferences(it[duplicateKey] ?: true, it[largeKey] ?: 100, it[oldKey] ?: 90) }
    val history = data.map { decode(it[historyKey] ?: "[]") }
    val lastScan = data.map { it[lastKey] ?: 0L }
    suspend fun preferences(p: Preferences) { store.edit { it[duplicateKey] = p.duplicates; it[largeKey] = p.largeMb; it[oldKey] = p.oldDays } }
    suspend fun add(h: History, scan: Boolean = false) { store.edit { prefs ->
        val items = (listOf(h) + decode(prefs[historyKey] ?: "[]")).take(200)
        prefs[historyKey] = JSONArray().apply { items.forEach { h -> put(JSONObject().put("time",h.time).put("kind",h.kind).put("summary",h.summary).put("details",h.details.take(30000))) } }.toString()
        if(scan) prefs[lastKey] = h.time
    } }
    suspend fun clear() { store.edit { it.clear() } }
    private fun decode(text: String): List<History> = try {
        val a = JSONArray(text)
        (0 until a.length()).map { i -> a.getJSONObject(i).let { History(it.getLong("time"),it.getString("kind"),it.getString("summary"),it.getString("details")) } }
    } catch (_: Exception) { emptyList() }
}
