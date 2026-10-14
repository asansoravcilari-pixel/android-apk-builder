package com.nexus.control

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class ApkReport(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val signerSha256: String,
    val dexCount: Int,
    val nativeLibCount: Int,
    val entryCount: Int,
    val sizeBytes: Long,
    val activities: Int,
    val services: Int,
    val receivers: Int,
    val providers: Int,
    val exportedComponents: List<String>,
    val abiSet: List<String>,
    val hasNetworkSecurityConfig: Boolean,
    val archiveHighlights: List<String>,
    val endpointCandidates: List<String>
)

object ApkInspector {
    suspend fun inspect(context: Context, uri: Uri): ApkReport = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "nexus_inspect_${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Dosya açılamadı" }
            file.outputStream().use { output -> input.copyTo(output) }
        }

        try {
            @Suppress("DEPRECATION")
            val signingFlag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                signingFlag

            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                ?: error("Geçerli APK bilgisi okunamadı")

            val app = info.applicationInfo
            val minSdk = if (Build.VERSION.SDK_INT >= 24) app?.minSdkVersion ?: 0 else 0
            val targetSdk = app?.targetSdkVersion ?: 0
            val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }

            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                val signing = info.signingInfo
                if (signing != null) {
                    if (signing.hasMultipleSigners()) signing.apkContentsSigners.toList()
                    else signing.signingCertificateHistory.toList()
                } else emptyList()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.toList().orEmpty()
            }

            val signerSha = signatures.firstOrNull()?.toByteArray()?.let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString(":") { "%02X".format(it) }
            }.orEmpty()

            val exported = buildList {
                info.activities.orEmpty().filter { it.exported }.forEach { add("Activity · ${it.name}") }
                info.services.orEmpty().filter { it.exported }.forEach { add("Service · ${it.name}") }
                info.receivers.orEmpty().filter { it.exported }.forEach { add("Receiver · ${it.name}") }
                info.providers.orEmpty().filter { it.exported }.forEach { add("Provider · ${it.name}") }
            }.sorted()

            var dex = 0
            var so = 0
            var entries = 0
            var networkSecurity = false
            val abis = linkedSetOf<String>()
            val highlights = linkedSetOf<String>()
            val endpointCandidates = linkedSetOf<String>()
            var inspectedPayloadBytes = 0L
            val maxPayloadBytes = 8L * 1024 * 1024
            val maxEntryBytes = 2L * 1024 * 1024
            val endpointRegex = Regex("(?:https?://|wss?://)[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]{4,240}")
            val domainRegex = Regex("(?<![A-Za-z0-9.-])(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+(?:com|net|org|io|dev|app|xyz|tr|cloud|tech)(?::\\d{2,5})?(?![A-Za-z0-9.-])", RegexOption.IGNORE_CASE)

            ZipFile(file).use { zip ->
                val e = zip.entries()
                while (e.hasMoreElements()) {
                    val item = e.nextElement()
                    entries++
                    val name = item.name
                    if (name.matches(Regex("classes\\d*\\.dex"))) dex++
                    if (name.endsWith(".so")) {
                        so++
                        val parts = name.split('/')
                        if (parts.size >= 3 && parts[0] == "lib") abis.add(parts[1])
                    }
                    if (name.endsWith("network_security_config.xml")) networkSecurity = true
                    when {
                        name.startsWith("assets/") && highlights.size < 20 -> highlights.add("asset · $name")
                        name.startsWith("res/xml/") && highlights.size < 20 -> highlights.add("xml · $name")
                        name.startsWith("META-INF/") && name.endsWith(".SF") && highlights.size < 20 -> highlights.add("signature · $name")
                    }

                    val scanCandidate = !item.isDirectory && (
                        name.matches(Regex("classes\\d*\\.dex")) ||
                            name.startsWith("assets/") ||
                            name.startsWith("res/raw/") ||
                            name.endsWith(".json") ||
                            name.endsWith(".txt") ||
                            name.endsWith(".js")
                        )
                    if (scanCandidate && endpointCandidates.size < 40 && inspectedPayloadBytes < maxPayloadBytes) {
                        val declaredSize = item.size
                        if (declaredSize != 0L) {
                            val entryBudget = if (declaredSize > 0L) declaredSize.coerceAtMost(maxEntryBytes) else maxEntryBytes
                            val remaining = (maxPayloadBytes - inspectedPayloadBytes).coerceAtMost(entryBudget).toInt()
                            val bytes = zip.getInputStream(item).use { input ->
                                val buffer = ByteArray(remaining)
                                var offset = 0
                                while (offset < buffer.size) {
                                    val read = input.read(buffer, offset, buffer.size - offset)
                                    if (read <= 0) break
                                    offset += read
                                }
                                buffer.copyOf(offset)
                            }
                            inspectedPayloadBytes += bytes.size
                            val text = String(bytes, Charsets.ISO_8859_1)
                            endpointRegex.findAll(text).take(30).forEach { match ->
                                endpointCandidates.add(match.value.trimEnd('.', ',', ';', '\"', '\'', ')', ']', '}'))
                            }
                            if (endpointCandidates.size < 40) {
                                domainRegex.findAll(text).take(30).forEach { match -> endpointCandidates.add(match.value) }
                            }
                        }
                    }
                }
            }

            ApkReport(
                packageName = info.packageName ?: "Bilinmiyor",
                versionName = info.versionName ?: "Bilinmiyor",
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                permissions = info.requestedPermissions?.sorted().orEmpty(),
                signerSha256 = signerSha,
                dexCount = dex,
                nativeLibCount = so,
                entryCount = entries,
                sizeBytes = file.length(),
                activities = info.activities?.size ?: 0,
                services = info.services?.size ?: 0,
                receivers = info.receivers?.size ?: 0,
                providers = info.providers?.size ?: 0,
                exportedComponents = exported,
                abiSet = abis.toList().sorted(),
                hasNetworkSecurityConfig = networkSecurity,
                archiveHighlights = highlights.toList(),
                endpointCandidates = endpointCandidates.take(40)
            )
        } finally {
            file.delete()
        }
    }
}
