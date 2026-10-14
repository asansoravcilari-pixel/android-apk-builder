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



data class CoreProfile(
    val id: String,
    val name: String,
    val healthUrl: String,
    val wssUrl: String,
    val updatedAt: Long
)



data class ServerSlot(
    val id: String,
    val name: String,
    val role: String,
    val host: String = "",
    val port: Int = 443,
    val healthUrl: String = ""
)

data class ServerCheckSnapshot(
    val serverId: String,
    val checkedAt: Long,
    val ok: Boolean,
    val summary: String
)

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

    fun loadBrowserBookmarks(): List<String> = runCatching {
        val array = JSONArray(prefs.getString("browser_bookmarks", "[]") ?: "[]")
        List(array.length()) { index -> array.optString(index) }
            .filter { it.startsWith("https://") }
    }.getOrDefault(emptyList())

    fun toggleBrowserBookmark(url: String): List<String> {
        if (!url.startsWith("https://")) return loadBrowserBookmarks()
        val list = loadBrowserBookmarks().toMutableList()
        if (!list.remove(url)) list.add(0, url.take(1000))
        val array = JSONArray()
        list.take(24).forEach(array::put)
        prefs.edit().putString("browser_bookmarks", array.toString()).apply()
        return loadBrowserBookmarks()
    }

    fun loadServerSlots(): List<ServerSlot> = runCatching {
        val raw = prefs.getString("server_slots", "")
        if (raw.isNullOrBlank()) return@runCatching defaultServerSlots()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    ServerSlot(
                        id = o.optString("id").ifBlank { "server_$i" },
                        name = o.optString("name").ifBlank { "Sunucu ${i + 1}" },
                        role = o.optString("role").ifBlank { "Sunucu" },
                        host = o.optString("host"),
                        port = o.optInt("port", 443).coerceIn(1, 65535),
                        healthUrl = o.optString("healthUrl").takeIf { it.isBlank() || it.startsWith("https://") }.orEmpty()
                    )
                )
            }
        }.let { loaded ->
            val byId = loaded.associateBy { it.id }
            defaultServerSlots().map { default -> byId[default.id] ?: default }
        }
    }.getOrDefault(defaultServerSlots())

    fun saveServerSlot(slot: ServerSlot): List<ServerSlot> {
        require(slot.port in 1..65535) { "Port 1..65535 olmalı" }
        require(slot.healthUrl.isBlank() || slot.healthUrl.startsWith("https://")) { "Health URL https:// olmalı" }
        val next = loadServerSlots().map { existing ->
            if (existing.id == slot.id) slot.copy(
                name = slot.name.trim().take(60).ifBlank { existing.name },
                role = slot.role.trim().take(80).ifBlank { existing.role },
                host = slot.host.trim().take(255),
                healthUrl = slot.healthUrl.trim().take(1000)
            ) else existing
        }
        val array = JSONArray()
        next.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("role", item.role)
                    .put("host", item.host)
                    .put("port", item.port)
                    .put("healthUrl", item.healthUrl)
            )
        }
        prefs.edit().putString("server_slots", array.toString()).apply()
        return loadServerSlots()
    }

    fun saveServerCheck(serverId: String, results: List<DiagnosticResult>) {
        if (serverId.isBlank() || results.isEmpty()) return
        val all = loadServerChecks().toMutableMap()
        val ok = results.all { it.ok }
        val summary = results.joinToString(" · ") { "${it.title}:${if (it.ok) "PASS" else "FAIL"}" }.take(1200)
        all[serverId] = ServerCheckSnapshot(serverId, System.currentTimeMillis(), ok, summary)
        val array = JSONArray()
        all.values.forEach { item ->
            array.put(
                JSONObject()
                    .put("serverId", item.serverId)
                    .put("checkedAt", item.checkedAt)
                    .put("ok", item.ok)
                    .put("summary", item.summary)
            )
        }
        prefs.edit().putString("server_checks", array.toString()).apply()
    }

    fun loadServerChecks(): Map<String, ServerCheckSnapshot> = runCatching {
        val array = JSONArray(prefs.getString("server_checks", "[]") ?: "[]")
        buildMap {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val id = o.optString("serverId")
                if (id.isNotBlank()) {
                    put(id, ServerCheckSnapshot(id, o.optLong("checkedAt"), o.optBoolean("ok"), o.optString("summary")))
                }
            }
        }
    }.getOrDefault(emptyMap())

    private fun defaultServerSlots(): List<ServerSlot> = listOf(
        ServerSlot("core", "NEXUS Core", "Production Core"),
        ServerSlot("cloud", "NEXUS Cloud Runtime", "Cloud Runtime"),
        ServerSlot("build", "NEXUS Build Server", "Build · replay · regression"),
        ServerSlot("webmail", "NEXUS Web & Mail", "Web · mail · public services")
    )

    fun loadFavoriteModules(): Set<String> =
        prefs.getStringSet("favorite_modules", emptySet())?.toSet().orEmpty()

    fun toggleFavoriteModule(name: String): Set<String> {
        val next = loadFavoriteModules().toMutableSet().apply {
            if (!add(name)) remove(name)
        }
        prefs.edit().putStringSet("favorite_modules", next.toSet()).apply()
        return next
    }

    fun loadRecentModules(): List<String> = runCatching {
        val array = JSONArray(prefs.getString("recent_modules", "[]") ?: "[]")
        List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    fun pushRecentModule(name: String) {
        if (name.isBlank()) return
        val list = loadRecentModules().toMutableList()
        list.remove(name)
        list.add(0, name)
        val array = JSONArray()
        list.take(8).forEach(array::put)
        prefs.edit().putString("recent_modules", array.toString()).apply()
    }

    fun loadCoreProfiles(): List<CoreProfile> = runCatching {
        val raw = prefs.getString("core_profiles", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val health = o.optString("healthUrl")
                val wss = o.optString("wssUrl")
                if ((health.isBlank() || health.startsWith("https://")) && (wss.isBlank() || wss.startsWith("wss://"))) {
                    add(
                        CoreProfile(
                            id = o.optString("id").ifBlank { "profile_$i" },
                            name = o.optString("name").ifBlank { "Core Profili" },
                            healthUrl = health,
                            wssUrl = wss,
                            updatedAt = o.optLong("updatedAt")
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    fun saveCoreProfile(name: String, healthUrl: String, wssUrl: String): List<CoreProfile> {
        require(healthUrl.isBlank() || healthUrl.startsWith("https://")) { "Health URL https:// olmalı" }
        require(wssUrl.isBlank() || wssUrl.startsWith("wss://")) { "WSS URL wss:// olmalı" }
        val cleanName = name.trim().take(60).ifBlank { "Core Profili" }
        val id = cleanName.lowercase()
            .replace(Regex("[^a-z0-9çğıöşü]+"), "-")
            .trim('-')
            .ifBlank { "core" }
        val now = System.currentTimeMillis()
        val list = loadCoreProfiles().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val profile = CoreProfile(id, cleanName, healthUrl.trim(), wssUrl.trim(), now)
        if (index >= 0) list[index] = profile else list.add(0, profile)
        persistCoreProfiles(list.take(8))
        return loadCoreProfiles()
    }

    fun deleteCoreProfile(id: String): List<CoreProfile> {
        persistCoreProfiles(loadCoreProfiles().filterNot { it.id == id })
        return loadCoreProfiles()
    }

    private fun persistCoreProfiles(items: List<CoreProfile>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("healthUrl", item.healthUrl)
                    .put("wssUrl", item.wssUrl)
                    .put("updatedAt", item.updatedAt)
            )
        }
        prefs.edit().putString("core_profiles", array.toString()).apply()
    }

}
