package com.nexus.control

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MaintenanceSnapshot(
    val active: Boolean = false,
    val note: String = "",
    val checksMask: Int = 0,
    val startedAt: Long = 0L
) {
    fun checks(count: Int): List<Boolean> = List(count) { index -> checksMask and (1 shl index) != 0 }
}

data class DiagnosticHistoryItem(
    val timestamp: Long,
    val title: String,
    val ok: Boolean,
    val detail: String
)

class LocalStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_local_state_v1", Context.MODE_PRIVATE)

    fun loadMaintenance(): MaintenanceSnapshot = MaintenanceSnapshot(
        active = prefs.getBoolean("maintenance_active", false),
        note = prefs.getString("maintenance_note", "").orEmpty(),
        checksMask = prefs.getInt("maintenance_checks", 0),
        startedAt = prefs.getLong("maintenance_started_at", 0L)
    )

    fun saveMaintenance(snapshot: MaintenanceSnapshot) {
        prefs.edit()
            .putBoolean("maintenance_active", snapshot.active)
            .putString("maintenance_note", snapshot.note.take(500))
            .putInt("maintenance_checks", snapshot.checksMask)
            .putLong("maintenance_started_at", snapshot.startedAt)
            .apply()
    }

    fun saveRecoverySummary(summary: String) {
        prefs.edit()
            .putString("recovery_last_summary", summary.take(3000))
            .putLong("recovery_last_at", System.currentTimeMillis())
            .apply()
    }

    fun loadRecoverySummary(): Pair<Long, String>? {
        val at = prefs.getLong("recovery_last_at", 0L)
        val summary = prefs.getString("recovery_last_summary", "").orEmpty()
        return if (at > 0L && summary.isNotBlank()) at to summary else null
    }

    fun appendDiagnostics(results: List<DiagnosticResult>) {
        if (results.isEmpty()) return
        val current = loadDiagnosticHistory().toMutableList()
        results.forEach { result ->
            current.add(0, DiagnosticHistoryItem(System.currentTimeMillis(), result.title, result.ok, result.detail.take(1200)))
        }
        val trimmed = current.take(24)
        val array = JSONArray()
        trimmed.forEach { item ->
            array.put(
                JSONObject()
                    .put("timestamp", item.timestamp)
                    .put("title", item.title)
                    .put("ok", item.ok)
                    .put("detail", item.detail)
            )
        }
        prefs.edit().putString("diagnostic_history", array.toString()).apply()
    }

    fun loadDiagnosticHistory(): List<DiagnosticHistoryItem> = runCatching {
        val raw = prefs.getString("diagnostic_history", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    DiagnosticHistoryItem(
                        timestamp = o.optLong("timestamp"),
                        title = o.optString("title"),
                        ok = o.optBoolean("ok"),
                        detail = o.optString("detail")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun clearDiagnosticHistory() {
        prefs.edit().remove("diagnostic_history").apply()
    }

    fun saveBrowserHistory(url: String) {
        if (!url.startsWith("https://")) return
        val existing = loadBrowserHistory().toMutableList()
        existing.remove(url)
        existing.add(0, url.take(1000))
        val array = JSONArray()
        existing.take(12).forEach(array::put)
        prefs.edit().putString("browser_history", array.toString()).apply()
    }

    fun loadBrowserHistory(): List<String> = runCatching {
        val array = JSONArray(prefs.getString("browser_history", "[]") ?: "[]")
        List(array.length()) { index -> array.optString(index) }.filter { it.startsWith("https://") }
    }.getOrDefault(emptyList())

    fun clearBrowserHistory() {
        prefs.edit().remove("browser_history").apply()
    }
}
