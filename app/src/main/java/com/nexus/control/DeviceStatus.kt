package com.nexus.control

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs


data class DeviceSnapshot(
    val batteryPercent: Int?,
    val charging: Boolean,
    val networkLabel: String,
    val networkValidated: Boolean,
    val storageUsedPercent: Int?,
    val storageFreeBytes: Long?,
    val storageTotalBytes: Long?
)

object DeviceStatus {
    fun read(context: Context): DeviceSnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivity.activeNetwork
        val capabilities = activeNetwork?.let(connectivity::getNetworkCapabilities)
        val networkLabel = when {
            capabilities == null -> "Çevrimdışı"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobil"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Ağ"
        }
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        val storage = runCatching {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val usedPct = if (total > 0) (((total - free) * 100.0) / total).toInt().coerceIn(0, 100) else null
            Triple(usedPct, free, total)
        }.getOrNull()

        return DeviceSnapshot(
            batteryPercent = batteryPercent,
            charging = charging,
            networkLabel = networkLabel,
            networkValidated = validated,
            storageUsedPercent = storage?.first,
            storageFreeBytes = storage?.second,
            storageTotalBytes = storage?.third
        )
    }
}
