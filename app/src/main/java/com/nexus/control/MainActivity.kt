package com.nexus.control

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ComponentActivity.store by preferencesDataStore("nexus_preferences")
private val THEME = stringPreferencesKey("theme")
private val ALERTS = booleanPreferencesKey("alerts")
private val SECURITY_ALERTS = booleanPreferencesKey("security_alerts")
private val SERVER_ALERTS = booleanPreferencesKey("server_alerts")
private val BUILD_ALERTS = booleanPreferencesKey("build_alerts")
private val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")
private val HEALTH_URL = stringPreferencesKey("health_url")
private val WSS_URL = stringPreferencesKey("wss_url")

private data class UserPrefs(
    val theme: String = "NEXUS Gece",
    val alerts: Boolean = true,
    val securityAlerts: Boolean = true,
    val serverAlerts: Boolean = true,
    val buildAlerts: Boolean = true,
    val widgetOpacity: Float = 78f,
    val healthUrl: String = "",
    val wssUrl: String = ""
)

data class NexusPalette(
    val name: String,
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val accent: Color,
    val accent2: Color,
    val text: Color,
    val muted: Color,
    val success: Color = Color(0xFF43E5A6),
    val danger: Color = Color(0xFFFF667A),
    val light: Boolean = false
)

private val palettes = listOf(
    NexusPalette("NEXUS Gece", Color(0xFF050B14), Color(0xFF0A1422), Color(0xFF102238), Color(0xFF43D7FF), Color(0xFF8A68FF), Color(0xFFF5F9FF), Color(0xFF8EA5C2)),
    NexusPalette("Saf Siyah", Color.Black, Color(0xFF0B0B0E), Color(0xFF18181D), Color(0xFF4EE6FF), Color(0xFF9E7AFF), Color.White, Color(0xFFA5A7B2)),
    NexusPalette("Zümrüt", Color(0xFF04130F), Color(0xFF09231C), Color(0xFF10372B), Color(0xFF47E6A3), Color(0xFF48C8FF), Color(0xFFF2FFF8), Color(0xFF9CC4B3)),
    NexusPalette("Mor Nebula", Color(0xFF100817), Color(0xFF1D102A), Color(0xFF311947), Color(0xFFC08FFF), Color(0xFF51DFFF), Color(0xFFFFF7FF), Color(0xFFC3ADD6)),
    NexusPalette("Grafit", Color(0xFF0E1014), Color(0xFF191C22), Color(0xFF272C35), Color(0xFF78D4FF), Color(0xFFDDE2EA), Color(0xFFF6F7F9), Color(0xFFA5ADB9)),
    NexusPalette("Açık", Color(0xFFF3F6FB), Color.White, Color(0xFFE8EEF7), Color(0xFF1F6BFF), Color(0xFF7453FF), Color(0xFF172033), Color(0xFF64748B), light = true)
)

private enum class Module(val title: String, val subtitle: String, val icon: ImageVector) {
    CORE("NEXUS Core Modu", "Core sağlık ve bağlantı merkezi", Icons.Outlined.Security),
    TESTS("Test Merkezi", "DNS · TCP · HTTPS gerçek testleri", Icons.Outlined.CheckCircle),
    MAINTENANCE("Bakım Modu", "Bakım oturumu ve kontrol listesi", Icons.Outlined.Build),
    RECOVERY("Kurtarma Modu", "Preflight ve recovery zinciri", Icons.Outlined.Security),
    SERVERS("Sunucular", "Core / Build envanteri", Icons.Outlined.Dns),
    BUILD("Build & Release", "GitHub Actions ve artifact alanı", Icons.Outlined.Cloud),
    LOGS("Log Merkezi", "Runtime · socket · security", Icons.Outlined.List),
    SECURITY("Güvenlik Merkezi", "TLS ve erişim kontrolleri", Icons.Outlined.Lock),
    BACKUP("Yedek & Restore", "Doğrulama ve geri dönüş planı", Icons.Outlined.Storage),
    APK("APK İnceleme", "Paket · izin · imza · DEX", Icons.Outlined.Build),
    SOCKET("Socket Lab", "WSS bağlantısı ve frame testi", Icons.Outlined.Wifi),
    CRYPTO("Kripto Araçları", "JWT · Base64 · SHA-256", Icons.Outlined.Lock),
    NETWORK("Ağ Araçları", "DNS · port · HTTPS", Icons.Outlined.Wifi),
    BROWSER("Tarayıcı", "HTTPS teknik tarayıcı", Icons.Outlined.Cloud),
    TERMINAL("Terminal", "SSH/Core Agent alanı", Icons.Outlined.Code),
    FILES("Dosyalar", "Yerel dosya önizleme", Icons.Outlined.Folder),
    MAIL("Mail Center", "E-posta oluşturma alanı", Icons.Outlined.Notifications),
    NOTIFICATIONS("Bildirim Merkezi", "Kanallar ve test", Icons.Outlined.Notifications)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannels()
        setContent {
            val scope = rememberCoroutineScope()
            val activity = this@MainActivity
            val prefs by store.data.map { p ->
                UserPrefs(
                    theme = p[THEME] ?: "NEXUS Gece",
                    alerts = p[ALERTS] ?: true,
                    securityAlerts = p[SECURITY_ALERTS] ?: true,
                    serverAlerts = p[SERVER_ALERTS] ?: true,
                    buildAlerts = p[BUILD_ALERTS] ?: true,
                    widgetOpacity = p[WIDGET_OPACITY] ?: 78f,
                    healthUrl = p[HEALTH_URL] ?: "",
                    wssUrl = p[WSS_URL] ?: ""
                )
            }.collectAsState(initial = UserPrefs())

            val palette = palettes.firstOrNull { it.name == prefs.theme } ?: palettes.first()
            val scheme = if (palette.light) lightColorScheme(
                primary = palette.accent, secondary = palette.accent2, background = palette.background,
                surface = palette.surface, surfaceVariant = palette.surface2, onBackground = palette.text, onSurface = palette.text
            ) else darkColorScheme(
                primary = palette.accent, secondary = palette.accent2, background = palette.background,
                surface = palette.surface, surfaceVariant = palette.surface2, onBackground = palette.text, onSurface = palette.text
            )

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            var module by remember { mutableStateOf<Module?>(null) }
            var showSettings by remember { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

            BackHandler(enabled = module != null || showSettings) {
                module = null
                showSettings = false
            }

            fun closeDrawer() { scope.launch { drawerState.close() } }
            fun openHome() {
                module = null
                showSettings = false
                closeDrawer()
            }
            fun openModule(target: Module) {
                module = target
                showSettings = false
                closeDrawer()
            }
            fun openSettings() {
                module = null
                showSettings = true
                closeDrawer()
            }

            MaterialTheme(colorScheme = scheme) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        NexusDrawer(
                            p = palette,
                            currentModule = module,
                            settingsSelected = showSettings,
                            dashboardSelected = module == null && !showSettings,
                            onHome = ::openHome,
                            onModule = ::openModule,
                            onSettings = ::openSettings
                        )
                    }
                ) {
                    Scaffold(containerColor = palette.background) { padding ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 15.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(13.dp)
                        ) {
                            NexusHeader(
                                p = palette,
                                title = when {
                                    showSettings -> "Ayarlar"
                                    module != null -> module!!.title
                                    else -> "Komuta Merkezi"
                                },
                                onMenu = { scope.launch { drawerState.open() } },
                                onNotifications = { openModule(Module.NOTIFICATIONS) }
                            )

                            when {
                                showSettings -> SettingsPage(
                                    palette, prefs,
                                    onTheme = { name -> scope.launch { store.edit { it[THEME] = name } } },
                                    onAlerts = { enabled ->
                                        scope.launch { store.edit { it[ALERTS] = enabled } }
                                        if (enabled && Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    onSecurityAlerts = { enabled -> scope.launch { store.edit { it[SECURITY_ALERTS] = enabled } } },
                                    onServerAlerts = { enabled -> scope.launch { store.edit { it[SERVER_ALERTS] = enabled } } },
                                    onBuildAlerts = { enabled -> scope.launch { store.edit { it[BUILD_ALERTS] = enabled } } },
                                    onWidgetOpacity = { value ->
                                        scope.launch { store.edit { it[WIDGET_OPACITY] = value } }
                                        activity.getSharedPreferences("nexus_widget", Context.MODE_PRIVATE)
                                            .edit().putInt("opacity", value.toInt()).apply()
                                        StatusWidget.updateAll(activity)
                                    },
                                    saveHealthUrl = { value -> scope.launch { store.edit { it[HEALTH_URL] = value } } },
                                    saveWssUrl = { value -> scope.launch { store.edit { it[WSS_URL] = value } } },
                                    requestPermission = {
                                        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    testNotification = { activity.sendTestNotification() }
                                )
                                module != null -> ModuleScreen(module!!, palette, prefs) { activity.sendTestNotification() }
                                else -> CommandCenter(palette, prefs, ::openModule)
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }
            }
        }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("nexus_security", "Güvenlik uyarıları", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel("nexus_server", "Sunucu durumları", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("nexus_build", "Build bildirimleri", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun sendTestNotification() {
        val n = Notification.Builder(this, "nexus_server")
            .setSmallIcon(R.drawable.nexus_icon)
            .setContentTitle("NEXUS Control")
            .setContentText("Bildirim kanalı çalışıyor. Canlı alarm için Core olay akışı bağlanacak.")
            .setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(4001, n)
    }
}

@Composable
private fun NexusDrawer(
    p: NexusPalette,
    currentModule: Module?,
    settingsSelected: Boolean,
    dashboardSelected: Boolean,
    onHome: () -> Unit,
    onModule: (Module) -> Unit,
    onSettings: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = p.surface,
        modifier = Modifier.width(318.dp)
    ) {
        Column(
            Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).background(
                        Brush.linearGradient(listOf(p.accent.copy(alpha = .30f), p.accent2.copy(alpha = .26f))),
                        RoundedCornerShape(15.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) { Text("N", color = p.accent, fontWeight = FontWeight.Black, fontSize = 23.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("NEXUS CONTROL", color = p.accent, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 11.sp)
                    Text("Mobil Operasyon Merkezi", color = p.muted, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = p.text.copy(alpha = .08f), modifier = Modifier.padding(vertical = 8.dp))
            DrawerItem("Komuta Merkezi", "Tek ekran operasyon görünümü", Icons.Outlined.Dashboard, dashboardSelected, p, onHome)

            DrawerGroup("CORE & OPERASYON", p)
            listOf(Module.CORE, Module.SERVERS, Module.TESTS, Module.MAINTENANCE, Module.RECOVERY, Module.BACKUP).forEach { m ->
                DrawerModuleItem(m, currentModule == m, p) { onModule(m) }
            }

            DrawerGroup("GÖZLEM & GÜVENLİK", p)
            listOf(Module.LOGS, Module.SECURITY, Module.BUILD, Module.NOTIFICATIONS).forEach { m ->
                DrawerModuleItem(m, currentModule == m, p) { onModule(m) }
            }

            DrawerGroup("LAB & ARAÇLAR", p)
            listOf(Module.TERMINAL, Module.FILES, Module.APK, Module.SOCKET, Module.NETWORK, Module.CRYPTO, Module.BROWSER, Module.MAIL).forEach { m ->
                DrawerModuleItem(m, currentModule == m, p) { onModule(m) }
            }

            DrawerGroup("UYGULAMA", p)
            DrawerItem("Ayarlar", "Tema · bağlantı · widget · bildirim", Icons.Outlined.Settings, settingsSelected, p, onSettings)
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = p.surface2.copy(alpha = .72f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("v0.4.0", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Drawer tabanlı tek ana dashboard", color = p.muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun DrawerGroup(title: String, p: NexusPalette) {
    Text(
        title,
        color = p.muted,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(start = 14.dp, top = 13.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerModuleItem(module: Module, selected: Boolean, p: NexusPalette, onClick: () -> Unit) {
    DrawerItem(module.title, module.subtitle, module.icon, selected, p, onClick)
}

@Composable
private fun DrawerItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    p: NexusPalette,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = if (selected) p.text.copy(alpha = .72f) else p.muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        icon = { Icon(icon, title, tint = if (selected) p.accent else p.muted) },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = p.accent.copy(alpha = .11f),
            unselectedContainerColor = Color.Transparent,
            selectedTextColor = p.text,
            unselectedTextColor = p.text
        )
    )
}

@Composable
private fun NexusHeader(p: NexusPalette, title: String, onMenu: () -> Unit, onNotifications: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onMenu,
            modifier = Modifier.background(p.surface, RoundedCornerShape(14.dp))
        ) { Icon(Icons.Outlined.Menu, "Menü", tint = p.accent) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("NEXUS CONTROL", color = p.accent, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = 10.sp)
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(
            onClick = onNotifications,
            modifier = Modifier.background(p.surface, RoundedCornerShape(14.dp))
        ) { Icon(Icons.Outlined.Notifications, "Bildirimler", tint = p.text) }
    }
}

@Composable
private fun CommandCenter(p: NexusPalette, prefs: UserPrefs, open: (Module) -> Unit) {
    val context = LocalContext.current
    val localState = remember(context) { LocalStateStore(context) }
    val maintenance = localState.loadMaintenance()
    val history = localState.loadDiagnosticHistory()
    val recovery = localState.loadRecoverySummary()
    val coreHost = remember(prefs.healthUrl) { runCatching { Uri.parse(prefs.healthUrl).host.orEmpty() }.getOrDefault("") }
    val wssHost = remember(prefs.wssUrl) { runCatching { Uri.parse(prefs.wssUrl).host.orEmpty() }.getOrDefault("") }

    DashboardHero(p, maintenance.active, history.firstOrNull())

    DashboardSection("Sistem Matrisi", "Yalnız gerçek yapılandırma ve yerel doğrulamalardan üretilir.", p)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        DashboardStateCard(
            title = "Core",
            state = if (prefs.healthUrl.isNotBlank()) "PROFİL HAZIR" else "YAPILANDIR",
            detail = coreHost.ifBlank { "Health URL gerekli" },
            icon = Icons.Outlined.Security,
            ok = prefs.healthUrl.isNotBlank(),
            p = p,
            modifier = Modifier.weight(1f),
            onClick = { open(Module.CORE) }
        )
        DashboardStateCard(
            title = "WSS",
            state = if (prefs.wssUrl.isNotBlank()) "PROFİL HAZIR" else "YAPILANDIR",
            detail = wssHost.ifBlank { "WSS URL gerekli" },
            icon = Icons.Outlined.Wifi,
            ok = prefs.wssUrl.isNotBlank(),
            p = p,
            modifier = Modifier.weight(1f),
            onClick = { open(Module.SOCKET) }
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        DashboardStateCard(
            title = "Bakım",
            state = if (maintenance.active) "AKTİF" else "KAPALI",
            detail = if (maintenance.active) maintenance.note.ifBlank { "Bakım oturumu açık" } else "Aktif oturum yok",
            icon = Icons.Outlined.Build,
            ok = maintenance.active,
            p = p,
            modifier = Modifier.weight(1f),
            onClick = { open(Module.MAINTENANCE) }
        )
        DashboardStateCard(
            title = "Kurtarma",
            state = if (recovery != null) "GEÇMİŞ VAR" else "HAZIR",
            detail = recovery?.let { formatTime(it.first) } ?: "Preflight çalıştırılabilir",
            icon = Icons.Outlined.Security,
            ok = recovery != null,
            p = p,
            modifier = Modifier.weight(1f),
            onClick = { open(Module.RECOVERY) }
        )
    }

    DiagnosticOverview(history, p) { open(Module.TESTS) }

    DashboardSection("Hızlı İşlemler", "Sık kullanılan merkezlere tek dokunuş", p)
    QuickActionRail(p, open)

    DashboardSection("Operasyon Akışı", "Son yerel doğrulamalar ve aktif durum", p)
    OperationsFeed(history, maintenance, recovery, p)

    DashboardSection("Araçlar", "Analiz ve erişim modülleri", p)
    CompactModuleGrid(
        listOf(Module.TERMINAL, Module.APK, Module.SOCKET, Module.BROWSER, Module.FILES, Module.SECURITY),
        p,
        open
    )
}

@Composable
private fun DashboardHero(p: NexusPalette, maintenanceActive: Boolean, lastTest: DiagnosticHistoryItem?) {
    val brush = Brush.linearGradient(listOf(p.accent.copy(alpha = .28f), p.accent2.copy(alpha = .20f), p.surface))
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(brush).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(8.dp).background(if (maintenanceActive) p.danger else p.success, CircleShape))
                        Text(if (maintenanceActive) "BAKIM MODU" else "COMMAND READY", color = if (maintenanceActive) p.danger else p.success, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text("Tüm operasyon tek ekranda", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                    Text("Core · test · kurtarma · güvenlik · laboratuvar", color = p.muted, fontSize = 11.sp)
                }
                Box(Modifier.size(50.dp).background(p.accent.copy(alpha = .12f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Dashboard, null, tint = p.accent, modifier = Modifier.size(27.dp))
                }
            }
            HorizontalDivider(color = p.text.copy(alpha = .08f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroDatum("SÜRÜM", "0.4.0", p)
                HeroDatum("SON TEST", lastTest?.let { if (it.ok) "PASS" else "FAIL" } ?: "YOK", p)
                HeroDatum("MOD", if (maintenanceActive) "BAKIM" else "NORMAL", p)
            }
        }
    }
}

@Composable
private fun HeroDatum(label: String, value: String, p: NexusPalette) {
    Column {
        Text(label, color = p.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        Text(value, color = p.text, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
private fun DashboardSection(title: String, subtitle: String, p: NexusPalette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(subtitle, color = p.muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DashboardStateCard(
    title: String,
    state: String,
    detail: String,
    icon: ImageVector,
    ok: Boolean,
    p: NexusPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = p.surface)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(p.surface2, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = p.accent, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(8.dp).background(if (ok) p.success else p.muted, CircleShape))
            }
            Text(state, fontWeight = FontWeight.Black, fontSize = 12.sp, color = if (ok) p.success else p.text)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(detail, color = p.muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DiagnosticOverview(history: List<DiagnosticHistoryItem>, p: NexusPalette, openTests: () -> Unit) {
    val passCount = history.count { it.ok }
    val failCount = history.count { !it.ok }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = openTests),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = p.surface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Test Sağlığı", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(if (history.isEmpty()) "Henüz gerçek test sonucu yok" else "Son ${history.size} yerel doğrulama", color = p.muted, fontSize = 10.sp)
                }
                if (history.isNotEmpty()) {
                    Text("$passCount PASS · $failCount FAIL", color = if (failCount == 0) p.success else p.danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (history.isEmpty()) {
                Surface(shape = RoundedCornerShape(14.dp), color = p.surface2) {
                    Text("Test Merkezi'ni açarak DNS, TCP, HTTPS ve TLS doğrulaması çalıştır.", modifier = Modifier.padding(12.dp), color = p.muted, fontSize = 10.sp)
                }
            } else {
                DiagnosticTrend(history.take(18).reversed(), p)
            }
        }
    }
}

@Composable
private fun DiagnosticTrend(history: List<DiagnosticHistoryItem>, p: NexusPalette) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(58.dp)) {
        if (history.isEmpty()) return@Canvas
        val step = if (history.size <= 1) size.width else size.width / (history.size - 1).toFloat()
        val points = history.mapIndexed { index, item ->
            androidx.compose.ui.geometry.Offset(index * step, if (item.ok) size.height * .25f else size.height * .75f)
        }
        for (i in 0 until points.lastIndex) {
            drawLine(p.accent.copy(alpha = .75f), points[i], points[i + 1], strokeWidth = 3f)
        }
        points.forEachIndexed { index, point ->
            drawCircle(if (history[index].ok) p.success else p.danger, radius = 5f, center = point)
        }
    }
}

@Composable
private fun QuickActionRail(p: NexusPalette, open: (Module) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Module.TESTS, Module.MAINTENANCE, Module.RECOVERY, Module.CORE, Module.BUILD, Module.NOTIFICATIONS).forEach { m ->
            Surface(
                modifier = Modifier.width(132.dp).clickable { open(m) },
                shape = RoundedCornerShape(17.dp),
                color = p.surface
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(m.icon, null, tint = p.accent, modifier = Modifier.size(21.dp))
                    Text(m.title, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.subtitle, color = p.muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun OperationsFeed(
    history: List<DiagnosticHistoryItem>,
    maintenance: MaintenanceSnapshot,
    recovery: Pair<Long, String>?,
    p: NexusPalette
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (maintenance.active) FeedRow("Bakım modu", maintenance.note.ifBlank { "Aktif bakım oturumu" }, p.danger, p)
            recovery?.let { FeedRow("Recovery preflight", formatTime(it.first), p.accent2, p) }
            history.take(4).forEach { item ->
                FeedRow(item.title, "${if (item.ok) "PASS" else "FAIL"} · ${formatTime(item.timestamp)}", if (item.ok) p.success else p.danger, p)
            }
            if (!maintenance.active && recovery == null && history.isEmpty()) {
                Text("Henüz yerel operasyon kaydı yok.", color = p.muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun FeedRow(title: String, detail: String, color: Color, p: NexusPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Text(detail, color = p.muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactModuleGrid(items: List<Module>, p: NexusPalette, open: (Module) -> Unit) {
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { module ->
                Surface(
                    modifier = Modifier.weight(1f).clickable { open(module) },
                    shape = RoundedCornerShape(17.dp),
                    color = p.surface
                ) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(module.icon, null, tint = p.accent, modifier = Modifier.size(22.dp))
                        Text(module.title, fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ModuleScreen(module: Module, p: NexusPalette, prefs: UserPrefs, notify:()->Unit) {
    when(module) {
        Module.TESTS, Module.NETWORK -> TestCenter(p, prefs)
        Module.CORE -> CoreMode(p, prefs)
        Module.MAINTENANCE -> MaintenanceMode(p)
        Module.RECOVERY -> RecoveryMode(p, prefs)
        Module.SERVERS -> ServersMode(p)
        Module.BUILD -> BuildMode(p)
        Module.LOGS -> LogsMode(p)
        Module.SECURITY -> SecurityMode(p, prefs)
        Module.BACKUP -> BackupMode(p)
        Module.APK -> ApkLab(p)
        Module.SOCKET -> SocketLab(p, prefs.wssUrl)
        Module.CRYPTO -> CryptoLab(p)
        Module.BROWSER -> BrowserMode(p)
        Module.TERMINAL -> TerminalMode(p)
        Module.FILES -> FileMode(p)
        Module.MAIL -> MailMode(p)
        Module.NOTIFICATIONS -> NotificationsMode(p, notify)
    }
}

@Composable
private fun TestCenter(p: NexusPalette, prefs: UserPrefs) {
    val context = LocalContext.current
    val stateStore = remember(context) { LocalStateStore(context) }
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(runCatching { Uri.parse(prefs.healthUrl).host.orEmpty() }.getOrDefault("")) }
    var port by remember { mutableStateOf("443") }
    var url by remember { mutableStateOf(prefs.healthUrl) }
    var results by remember { mutableStateOf<List<DiagnosticResult>>(emptyList()) }
    var history by remember { mutableStateOf(stateStore.loadDiagnosticHistory()) }
    var busy by remember { mutableStateOf(false) }

    suspend fun execute(block: suspend () -> List<DiagnosticResult>) {
        busy = true
        try {
            val newResults = block()
            results = newResults
            stateStore.appendDiagnostics(newResults)
            history = stateStore.loadDiagnosticHistory()
        } finally { busy = false }
    }

    SectionHeader("Gerçek Bağlantı Testleri", "DNS, TCP, HTTPS ve TLS cihazdan gerçek çalışır; geçmiş yerelde tutulur.", p)
    InputCard(p) {
        OutlinedTextField(host,{host=it.trim()},label={Text("Host")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(port,{port=it.filter(Char::isDigit).take(5)},label={Text("Port")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Button(enabled=!busy && host.isNotBlank() && (port.toIntOrNull()?.let { it in 1..65535 } == true),onClick={scope.launch{execute{listOf(Diagnostics.dns(host),Diagnostics.tcp(host,port.toIntOrNull()?:443))}}},modifier=Modifier.fillMaxWidth()){Text(if (busy) "Çalışıyor..." else "DNS + TCP Testi")}
        OutlinedTextField(url,{url=it.trim()},label={Text("HTTPS URL")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Button(enabled=!busy&&url.startsWith("https://"),onClick={scope.launch{execute{listOf(Diagnostics.https(url),Diagnostics.tls(url))}}},modifier=Modifier.fillMaxWidth()){Text(if (busy) "Çalışıyor..." else "HTTPS + TLS Testi")}
        if (prefs.healthUrl.startsWith("https://") && url != prefs.healthUrl) {
            OutlinedButton(onClick={url=prefs.healthUrl;host=runCatching{Uri.parse(prefs.healthUrl).host.orEmpty()}.getOrDefault("");port="443"},modifier=Modifier.fillMaxWidth()){Text("Kayıtlı Core Profilini Doldur")}
        }
    }
    results.forEach { ResultCard(it,p) }
    if (history.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionHeader("Son Testler", "En yeni 24 sonuç", p) }
            TextButton(onClick={stateStore.clearDiagnosticHistory();history=emptyList()}){Text("Temizle")}
        }
        history.take(8).forEach { HistoryCard(it, p) }
    }
}

@Composable
private fun CoreMode(p:NexusPalette,prefs:UserPrefs){
    val scope=rememberCoroutineScope();var r by remember{mutableStateOf<DiagnosticResult?>(null)}
    SectionHeader("NEXUS Core Modu","Endpoint tahmin edilmez; tam URL Ayarlar'dan girilir.",p)
    Capability("Health URL",prefs.healthUrl.ifBlank{"Yapılandırılmadı"},prefs.healthUrl.isNotBlank(),p)
    Capability("WSS URL",prefs.wssUrl.ifBlank{"Yapılandırılmadı"},prefs.wssUrl.isNotBlank(),p)
    Button(enabled=prefs.healthUrl.startsWith("https://"),onClick={scope.launch{r=Diagnostics.https(prefs.healthUrl)}},modifier=Modifier.fillMaxWidth()){Text("Core Health Testi")}
    r?.let{ResultCard(it,p)}
    InfoCard("Core bileşenleri","API · Realtime · PostgreSQL · Redis · Runtime · Transport · Decoder yalnız doğrulanmış veri ile canlıya geçecek.",Icons.Outlined.Security,p)
}

@Composable
private fun MaintenanceMode(p:NexusPalette){
    val context = LocalContext.current
    val stateStore = remember(context) { LocalStateStore(context) }
    val initial = remember { stateStore.loadMaintenance() }
    var active by remember { mutableStateOf(initial.active) }
    var note by remember { mutableStateOf(initial.note) }
    val checks=remember{mutableStateListOf<Boolean>().apply { addAll(initial.checks(4)) }}
    var startedAt by remember { mutableLongStateOf(initial.startedAt) }
    fun persist() {
        var mask = 0
        checks.forEachIndexed { index, checked -> if (checked) mask = mask or (1 shl index) }
        stateStore.saveMaintenance(MaintenanceSnapshot(active, note, mask, startedAt))
        StatusWidget.updateAll(context)
    }
    SectionHeader("Bakım Modu","Bakım oturumu cihazda kalıcıdır; uzak aksiyon capability bağlanmadan çalışmaz.",p)
    Capability("Durum",if(active)"Yerel bakım oturumu açık" else "Kapalı",active,p)
    if (active && startedAt > 0L) Report("Başlangıç", formatTime(startedAt), p)
    InputCard(p){
        OutlinedTextField(note,{note=it.take(500);persist()},label={Text("Bakım notu")},modifier=Modifier.fillMaxWidth())
        listOf("Kullanıcı etkisi kontrol edildi","Kritik servisler kaydedildi","Geri dönüş noktası doğrulandı","Bakım sonrası test planı hazır").forEachIndexed{i,t->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checks[i],{checks[i]=it;persist()});Text(t,fontSize=12.sp)}}
        Button(enabled=active || checks.all{it},onClick={active=!active;startedAt=if(active) System.currentTimeMillis() else 0L;if(!active){for(i in checks.indices)checks[i]=false};persist()},modifier=Modifier.fillMaxWidth()){Text(if(active)"Bakım Oturumunu Bitir" else "Bakım Oturumunu Başlat")}
    }
    Capability("Uzak servis kontrolü","Core capability endpoint + yetki sözleşmesi gerekli",false,p)
}

@Composable
private fun RecoveryMode(p:NexusPalette,prefs:UserPrefs){
    val context = LocalContext.current
    val stateStore = remember(context) { LocalStateStore(context) }
    val scope=rememberCoroutineScope()
    var rs by remember{mutableStateOf<List<DiagnosticResult>>(emptyList())}
    var busy by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(stateStore.loadRecoverySummary()) }
    SectionHeader("Kurtarma Modu","Önce erişim preflight; sonra doğrulanmış recovery capability.",p)
    Button(enabled=!busy && prefs.healthUrl.startsWith("https://"),onClick={scope.launch{busy=true;try{val h=runCatching{Uri.parse(prefs.healthUrl).host.orEmpty()}.getOrDefault("");if(h.isNotBlank()){rs=listOf(Diagnostics.dns(h),Diagnostics.tcp(h,443),Diagnostics.https(prefs.healthUrl),Diagnostics.tls(prefs.healthUrl));val summary=rs.joinToString("\n"){"${if(it.ok) "PASS" else "FAIL"} ${it.title}: ${it.detail.take(250)}"};stateStore.saveRecoverySummary(summary);previous=stateStore.loadRecoverySummary()}}finally{busy=false}}},modifier=Modifier.fillMaxWidth()){Text(if(busy) "Preflight çalışıyor..." else "Preflight Çalıştır")}
    if (prefs.healthUrl.isBlank()) InfoCard("Core profili eksik","Ayarlar > Core Bağlantısı bölümüne gerçek HTTPS health URL gir.",Icons.Outlined.Info,p)
    rs.forEach{ResultCard(it,p)}
    previous?.let { (at, summary) -> Report("Son kayıtlı preflight · ${formatTime(at)}", summary, p, true) }
    Checklist("Kurtarma zinciri",listOf("Erişim kontrolü","Servis sağlık doğrulaması","DB/cache kontrolü","Kontrollü restart","State resync","Son doğrulama"),p)
    Capability("Kontrollü restart","Yetkili recovery capability bağlanmadan kapalı",false,p)
}

@Composable private fun ServersMode(p:NexusPalette){SectionHeader("Sunucu Envanteri","Canlı metrikler doğrulanmış agent/API ile gelir.",p);ServerCard("NEXUS Core","Üretim Core",listOf("API","WSS","PostgreSQL","Redis"),p);ServerCard("NEXUS Build Server","Build / replay / regression",listOf("Build","Replay","Runner"),p)}
@Composable private fun BuildMode(p:NexusPalette){val c=LocalContext.current;SectionHeader("Build & Release","GitHub Actions hızlı erişim",p);InfoCard("Android build zinciri","Debug APK zinciri GitHub Actions'ta çalışıyor.",Icons.Outlined.Cloud,p);Button(onClick={c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/asansoravcilari-pixel/android-apk-builder/actions")))},modifier=Modifier.fillMaxWidth()){Text("GitHub Actions'ı Aç")};Checklist("Release hazırlığı",listOf("UI/işlev testleri","Kalıcı signing key","Release build","Sürüm notu","Artifact SHA-256"),p)}
@Composable private fun LogsMode(p:NexusPalette){SectionHeader("Log Merkezi","Kaynak bağlanmadan sahte log gösterilmez.",p);InfoCard("Kaynaklar","Runtime · Socket · Debug · Security · Build",Icons.Outlined.List,p);Capability("Live tail","Core agent capability gerekli",false,p)}
@Composable private fun SecurityMode(p:NexusPalette,prefs:UserPrefs){val scope=rememberCoroutineScope();var r by remember{mutableStateOf<DiagnosticResult?>(null)};SectionHeader("Güvenlik Merkezi","TLS doğrulaması yerel çalışır.",p);Button(enabled=prefs.healthUrl.startsWith("https://"),onClick={scope.launch{r=Diagnostics.tls(prefs.healthUrl)}},modifier=Modifier.fillMaxWidth()){Text("TLS Sertifikasını Kontrol Et")};r?.let{ResultCard(it,p)};InfoCard("Secrets politikası","Yetkisiz parola/anahtar kırma yok. Sahip olunan anahtarlarla yetkili analiz desteklenecek.",Icons.Outlined.Lock,p)}
@Composable private fun BackupMode(p:NexusPalette){SectionHeader("Yedek & Restore","Restore testi olmadan yedek sağlıklı sayılmaz.",p);Checklist("Restore doğrulama",listOf("Yedek mevcut","Checksum doğrulandı","İzolasyonda restore edildi","Servis ayağa kalktı","Veri bütünlüğü kontrol edildi"),p);Capability("Uzak yedek listesi","Backup API/agent gerekli",false,p)}

@Composable
private fun ApkLab(p:NexusPalette){
    val context=LocalContext.current;val scope=rememberCoroutineScope();var report by remember{mutableStateOf<ApkReport?>(null)};var err by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{runCatching{ApkInspector.inspect(context,uri)}.onSuccess{report=it;err=null}.onFailure{err=it.message}}}
    SectionHeader("APK İnceleme Modu","Seçilen dosya yerel olarak analiz edilir.",p)
    Button(onClick={picker.launch(arrayOf("application/vnd.android.package-archive","application/octet-stream","application/zip"))},modifier=Modifier.fillMaxWidth()){Text("APK Seç ve İncele")}
    err?.let{Text(it,color=p.danger)}
    report?.let { r ->
        Report("Paket", r.packageName, p)
        Report("Sürüm", "${r.versionName} (${r.versionCode})", p)
        Report("SDK", "min ${r.minSdk} · target ${r.targetSdk}", p)
        Report("İmza SHA-256", r.signerSha256.ifBlank { "Bulunamadı" }, p, true)
        Report("Arşiv", "${r.dexCount} DEX · ${r.nativeLibCount} native .so · ${r.entryCount} öğe", p)
        Report("Bileşenler", "${r.activities} activity · ${r.services} service · ${r.receivers} receiver · ${r.providers} provider", p)
        Report("ABI", if (r.abiSet.isEmpty()) "Native ABI yok" else r.abiSet.joinToString(" · "), p)
        Report("Network Security Config", if (r.hasNetworkSecurityConfig) "Arşivde bulundu" else "Arşivde adıyla bulunamadı", p)
        Report("Boyut", "${r.sizeBytes / 1024 / 1024} MB", p)
        if (r.permissions.isNotEmpty()) Report("İzinler (${r.permissions.size})", r.permissions.joinToString("\n"), p)
        if (r.exportedComponents.isNotEmpty()) Report("Exported bileşenler (${r.exportedComponents.size})", r.exportedComponents.joinToString("\n"), p)
        if (r.archiveHighlights.isNotEmpty()) Report("Arşiv vurguları", r.archiveHighlights.joinToString("\n"), p, true)
    }
}

@Composable
private fun SocketLab(p:NexusPalette,initial:String){
    var url by remember(initial){mutableStateOf(initial)}
    var msg by remember{mutableStateOf("")}
    var socketState by remember { mutableStateOf(SocketState.IDLE) }
    val logs=remember{mutableStateListOf<String>()}
    val probe=remember{SocketProbe()}
    DisposableEffect(Unit){onDispose{probe.close()}}
    fun addLog(value: String) { logs.add(0, "${formatTime(System.currentTimeMillis())} · $value"); while(logs.size>50) logs.removeAt(logs.lastIndex) }
    SectionHeader("Socket Lab","Yetkili WSS endpoint'inde bağlantı durumu ve frame gözlemi.",p)
    Capability("Socket durumu", socketState.name, socketState == SocketState.OPEN, p)
    InputCard(p){
        OutlinedTextField(url,{url=it.trim()},label={Text("wss:// URL")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(enabled=url.startsWith("wss://") && socketState !in listOf(SocketState.CONNECTING,SocketState.OPEN),onClick={logs.clear();probe.connect(url,{socketState=it},{addLog(it)})},modifier=Modifier.weight(1f)){Text(if(socketState==SocketState.CONNECTING)"Bağlanıyor" else "Bağlan")}
            OutlinedButton(onClick={probe.close();socketState=SocketState.IDLE;addLog("Bağlantı kapatıldı")},modifier=Modifier.weight(1f)){Text("Kapat")}
        }
        OutlinedTextField(msg,{msg=it.take(4000)},label={Text("Text frame")},modifier=Modifier.fillMaxWidth(),minLines=2)
        Button(enabled=socketState==SocketState.OPEN && msg.isNotBlank(),onClick={val ok=probe.send(msg);addLog(if(ok)">> ${msg.take(1000)}" else "Gönderilemedi")},modifier=Modifier.fillMaxWidth()){Text("Gönder")}
    }
    if(logs.isEmpty()) InfoCard("Frame günlüğü","Bağlantı kurulunca en fazla son 50 olay burada görünür. İçerik cihaz dışına gönderilmez.",Icons.Outlined.List,p)
    logs.forEach{Report("Socket",it,p,true)}
}

@Composable
private fun CryptoLab(p:NexusPalette){var input by remember{mutableStateOf("")};var output by remember{mutableStateOf("")};SectionHeader("Kripto & Encoding","JWT · Base64 · SHA-256 yerel araçları",p);InputCard(p){OutlinedTextField(input,{input=it},label={Text("Girdi")},minLines=4,modifier=Modifier.fillMaxWidth());Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){AssistChip(onClick={output=CryptoTools.sha256(input)},label={Text("SHA-256")});AssistChip(onClick={output=CryptoTools.base64Encode(input)},label={Text("Base64 Encode")});AssistChip(onClick={output=CryptoTools.base64Decode(input)},label={Text("Base64 Decode")});AssistChip(onClick={output=CryptoTools.decodeJwt(input)},label={Text("JWT Decode")})}};Report("Çıktı",output.ifBlank{"Henüz işlem yapılmadı"},p,true)}

@Composable
private fun BrowserMode(p:NexusPalette){
    val context = LocalContext.current
    val stateStore = remember(context) { LocalStateStore(context) }
    var input by remember{mutableStateOf("https://")}
    var target by remember{mutableStateOf<String?>(null)}
    var web by remember{mutableStateOf<WebView?>(null)}
    var pageTitle by remember { mutableStateOf("Yeni sekme") }
    var status by remember { mutableStateOf("Hazır") }
    var progress by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(stateStore.loadBrowserHistory()) }
    fun navigate(raw:String){val normalized=when{raw.startsWith("https://")->raw;raw.contains("://")->"";raw.isNotBlank()->"https://$raw";else->""};if(normalized.isNotBlank()){input=normalized;target=normalized;status="Yükleniyor";stateStore.saveBrowserHistory(normalized);history=stateStore.loadBrowserHistory()}else status="Yalnız HTTPS adresleri kabul edilir"}
    SectionHeader("Teknik Tarayıcı","HTTPS, geçmiş, hata durumu ve oturum temizleme.",p)
    InputCard(p){
        OutlinedTextField(input,{input=it},label={Text("Adres")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={web?.goBack()},enabled=web?.canGoBack()==true){Icon(Icons.Outlined.ArrowBack,"Geri")};IconButton(onClick={web?.goForward()},enabled=web?.canGoForward()==true){Icon(Icons.Outlined.ArrowForward,"İleri")};IconButton(onClick={web?.reload()},enabled=web!=null){Icon(Icons.Outlined.Refresh,"Yenile")};Button(onClick={navigate(input)},modifier=Modifier.weight(1f)){Text("Git")}}
        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(pageTitle,fontWeight=FontWeight.Bold,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text(status,color=p.muted,fontSize=10.sp,maxLines=2)};Text("$progress%",color=p.accent,fontSize=11.sp,fontWeight=FontWeight.Bold)}
        if(progress in 1..99) LinearProgressIndicator(progress={progress/100f},modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();web?.clearCache(true);status="Cookie, WebStorage ve cache temizlendi"},modifier=Modifier.weight(1f)){Text("Oturumu Temizle")};OutlinedButton(onClick={stateStore.clearBrowserHistory();history=emptyList()},modifier=Modifier.weight(1f)){Text("Geçmişi Sil")}}
    }
    if(history.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){history.take(8).forEach{u->AssistChip(onClick={navigate(u)},label={Text(Uri.parse(u).host?:u,maxLines=1)})}}
    Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().height(520.dp)){
        AndroidView(factory={ctx->WebView(ctx).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=true;CookieManager.getInstance().setAcceptCookie(true);webChromeClient=object:WebChromeClient(){override fun onProgressChanged(view:WebView?,newProgress:Int){progress=newProgress};override fun onReceivedTitle(view:WebView?,title:String?){if(!title.isNullOrBlank())pageTitle=title}};webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{val u=request?.url?.toString().orEmpty();return if(u.startsWith("https://")){false}else{status="HTTP/özel şema engellendi: $u";true}};override fun onPageFinished(view:WebView?,url:String?){status="Yüklendi";url?.takeIf{it.startsWith("https://")}?.let{input=it;stateStore.saveBrowserHistory(it);history=stateStore.loadBrowserHistory()}};override fun onReceivedError(view:WebView?,request:WebResourceRequest?,error:WebResourceError?){if(request?.isForMainFrame==true)status="Hata ${error?.errorCode}: ${error?.description}"}};web=this}},update={v->target?.let{t->if(v.url!=t)v.loadUrl(t)}},modifier=Modifier.fillMaxSize())
    }
}

@Composable private fun TerminalMode(p:NexusPalette){SectionHeader("Terminal","SSH/Core Agent backend doğrulanmadan sahte terminal çıktısı üretilmez.",p);Capability("SSH oturumu","Backend capability gerekli",false,p);Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF020407)),modifier=Modifier.fillMaxWidth()){Text("NEXUS Terminal\n\nBağlantı yapılandırılmadı.\nYetkili SSH/Core Agent capability bekleniyor.",color=Color(0xFF66E5B1),fontFamily=FontFamily.Monospace,modifier=Modifier.padding(16.dp),fontSize=12.sp)}}

@Composable private fun FileMode(p:NexusPalette){val context=LocalContext.current;val scope=rememberCoroutineScope();var name by remember{mutableStateOf("Dosya seçilmedi")};var preview by remember{mutableStateOf("")};val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{val x=FilePreview.read(context,uri);name=x.first;preview=x.second}};SectionHeader("Dosya Merkezi","Yerel dosya seçme ve ilk 100 KB metin önizleme.",p);Button(onClick={picker.launch(arrayOf("text/*","application/json","application/xml","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Text("Dosya Seç")};Report(name,preview.ifBlank{"Önizleme yok"},p,true)}

@Composable private fun MailMode(p:NexusPalette){val c=LocalContext.current;var to by remember{mutableStateOf("")};var subject by remember{mutableStateOf("")};SectionHeader("Mail Center","Şimdilik Android e-posta istemcisine güvenli devir.",p);InputCard(p){OutlinedTextField(to,{to=it},label={Text("Alıcı")},modifier=Modifier.fillMaxWidth());OutlinedTextField(subject,{subject=it},label={Text("Konu")},modifier=Modifier.fillMaxWidth());Button(onClick={val i=Intent(Intent.ACTION_SENDTO).apply{data=Uri.parse("mailto:");putExtra(Intent.EXTRA_EMAIL,arrayOf(to));putExtra(Intent.EXTRA_SUBJECT,subject)};runCatching{c.startActivity(i)}},modifier=Modifier.fillMaxWidth()){Text("E-posta Oluştur")}};Capability("IMAP / SMTP","Hesap/OAuth sözleşmesi bağlanınca aktif",false,p)}
@Composable private fun NotificationsMode(p:NexusPalette,notify:()->Unit){SectionHeader("Bildirim Merkezi","Android kanalları hazır.",p);Button(onClick=notify,modifier=Modifier.fillMaxWidth()){Text("Test Bildirimi Gönder")};Capability("Güvenlik kanalı","Hazır",true,p);Capability("Sunucu kanalı","Hazır",true,p);Capability("Build kanalı","Hazır",true,p);Capability("Canlı alarm üretimi","Core olay akışı gerekli",false,p)}

@Composable
private fun SettingsPage(p:NexusPalette,prefs:UserPrefs,onTheme:(String)->Unit,onAlerts:(Boolean)->Unit,onSecurityAlerts:(Boolean)->Unit,onServerAlerts:(Boolean)->Unit,onBuildAlerts:(Boolean)->Unit,onWidgetOpacity:(Float)->Unit,saveHealthUrl:(String)->Unit,saveWssUrl:(String)->Unit,requestPermission:()->Unit,testNotification:()->Unit){
    SectionHeader("Görünüm","6 tema ve widget şeffaflığı",p)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){palettes.forEach{item->Card(modifier=Modifier.width(145.dp).clickable{onTheme(item.name)},colors=CardDefaults.cardColors(containerColor=if(prefs.theme==item.name)p.surface2 else p.surface)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(item.background,item.surface2,item.accent,item.accent2).forEach{c->Box(Modifier.size(18.dp).background(c,CircleShape))}};Text(item.name,fontWeight=FontWeight.Bold,fontSize=12.sp);if(prefs.theme==item.name)Text("AKTİF",color=p.accent,fontSize=9.sp,fontWeight=FontWeight.Bold)}}}}
    InputCard(p){Text("Widget arka planı · %${prefs.widgetOpacity.toInt()}",fontWeight=FontWeight.Bold);var slider by remember(prefs.widgetOpacity){mutableFloatStateOf(prefs.widgetOpacity)};Slider(value=slider,onValueChange={slider=it},onValueChangeFinished={onWidgetOpacity(slider)},valueRange=0f..100f);Text("0 = şeffaf · 100 = dolu",color=p.muted,fontSize=11.sp)}
    SectionHeader("Core Bağlantısı","Endpoint tahmin edilmez; gerçek URL'yi gir.",p)
    var health by remember(prefs.healthUrl){mutableStateOf(prefs.healthUrl)};var wss by remember(prefs.wssUrl){mutableStateOf(prefs.wssUrl)}
    InputCard(p){OutlinedTextField(health,{health=it.trim()},label={Text("Tam HTTPS Health URL")},modifier=Modifier.fillMaxWidth());Button(enabled=health.isBlank()||health.startsWith("https://"),onClick={saveHealthUrl(health)},modifier=Modifier.fillMaxWidth()){Text("Health URL Kaydet")};OutlinedTextField(wss,{wss=it.trim()},label={Text("Tam WSS URL")},modifier=Modifier.fillMaxWidth());Button(enabled=wss.isBlank()||wss.startsWith("wss://"),onClick={saveWssUrl(wss)},modifier=Modifier.fillMaxWidth()){Text("WSS URL Kaydet")}}
    SectionHeader("Bildirimler","Kanalları ayrı yönet",p);Toggle("Tüm bildirimler",prefs.alerts,onAlerts,p);Toggle("Güvenlik",prefs.securityAlerts,onSecurityAlerts,p);Toggle("Sunucu",prefs.serverAlerts,onServerAlerts,p);Toggle("Build",prefs.buildAlerts,onBuildAlerts,p);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=requestPermission,modifier=Modifier.weight(1f)){Text("İzin İste")};Button(onClick=testNotification,modifier=Modifier.weight(1f)){Text("Test")}}
    InfoCard("Sürüm","NEXUS Control 0.4.0 · Command Center yeniden tasarımı",Icons.Outlined.Info,p)
}

@Composable private fun QuickGrid(items:List<Module>,p:NexusPalette,open:(Module)->Unit){items.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){row.forEach{m->Card(modifier=Modifier.weight(1f).clickable{open(m)},colors=CardDefaults.cardColors(containerColor=p.surface)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Icon(m.icon,null,tint=p.accent);Text(m.title,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(m.subtitle,color=p.muted,fontSize=10.sp,lineHeight=13.sp)}}};if(row.size==1)Spacer(Modifier.weight(1f))}}}
@Composable private fun ModuleRow(m:Module,p:NexusPalette,open:(Module)->Unit){Card(modifier=Modifier.fillMaxWidth().clickable{open(m)},colors=CardDefaults.cardColors(containerColor=p.surface)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(p.surface2,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(m.icon,null,tint=p.accent)};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(m.title,fontWeight=FontWeight.Bold);Text(m.subtitle,color=p.muted,fontSize=11.sp)};Icon(Icons.Outlined.ArrowForward,null,tint=p.muted)}}}
@Composable private fun StatTile(title:String,value:String,detail:String,icon:ImageVector,p:NexusPalette,modifier:Modifier){Card(modifier=modifier,colors=CardDefaults.cardColors(containerColor=p.surface)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Icon(icon,null,tint=p.accent);Text(value,fontWeight=FontWeight.Black,fontSize=14.sp);Text(title,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(detail,color=p.muted,fontSize=9.sp)}}}
@Composable private fun SectionHeader(title:String,sub:String,p:NexusPalette){Column{Text(title,fontWeight=FontWeight.ExtraBold,fontSize=17.sp);Text(sub,color=p.muted,fontSize=11.sp,lineHeight=15.sp)}}
@Composable private fun InputCard(p:NexusPalette,content:@Composable ColumnScope.()->Unit){Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=p.surface),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}}
@Composable private fun ResultCard(r:DiagnosticResult,p:NexusPalette){val c=if(r.ok)p.success else p.danger;Card(shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=p.surface),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.Top){Box(Modifier.size(9.dp).background(c,CircleShape));Spacer(Modifier.width(9.dp));Column{Text(r.title,fontWeight=FontWeight.Bold);Text(r.detail,color=p.muted,fontSize=11.sp,fontFamily=FontFamily.Monospace)}}}}
@Composable private fun Report(title:String,detail:String,p:NexusPalette,mono:Boolean=false){Card(shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=p.surface),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,color=p.accent,fontWeight=FontWeight.Bold,fontSize=11.sp);Text(detail,fontSize=11.sp,fontFamily=if(mono)FontFamily.Monospace else FontFamily.Default)}}}
@Composable private fun Capability(title:String,detail:String,ok:Boolean,p:NexusPalette){Card(colors=CardDefaults.cardColors(containerColor=p.surface),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).background(if(ok)p.success else p.muted,CircleShape));Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(detail,color=p.muted,fontSize=10.sp)};Text(if(ok)"HAZIR" else "KAPALI",color=if(ok)p.success else p.muted,fontSize=9.sp,fontWeight=FontWeight.Black)}}}
@Composable private fun InfoCard(title:String,detail:String,icon:ImageVector,p:NexusPalette){Card(colors=CardDefaults.cardColors(containerColor=p.surface),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.Top){Icon(icon,null,tint=p.accent);Spacer(Modifier.width(10.dp));Column{Text(title,fontWeight=FontWeight.Bold);Text(detail,color=p.muted,fontSize=11.sp,lineHeight=16.sp)}}}}
@Composable private fun Checklist(title:String,items:List<String>,p:NexusPalette){Card(colors=CardDefaults.cardColors(containerColor=p.surface),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontWeight=FontWeight.Bold);items.forEachIndexed{i,s->Text("${i+1}. $s",color=p.muted,fontSize=11.sp)}}}}
@Composable private fun ServerCard(name:String,role:String,services:List<String>,p:NexusPalette){Card(colors=CardDefaults.cardColors(containerColor=p.surface),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row{Icon(Icons.Outlined.Dns,null,tint=p.accent);Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(name,fontWeight=FontWeight.Bold);Text(role,color=p.muted,fontSize=11.sp)};Pill("BEKLEMEDE",false,p)};Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){services.forEach{Pill(it,false,p)}};Text("CPU/RAM/disk/uptime yalnız doğrulanmış agent/API verisi ile gösterilecek.",color=p.muted,fontSize=10.sp)}}}
@Composable private fun Pill(text:String,ok:Boolean,p:NexusPalette){val c=if(ok)p.success else p.muted;Surface(color=c.copy(alpha=.1f),shape=RoundedCornerShape(999.dp)){Text(text,color=c,modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp),fontSize=9.sp,fontWeight=FontWeight.Bold)}}
@Composable
private fun HistoryCard(item: DiagnosticHistoryItem, p: NexusPalette) {
    val c = if (item.ok) p.success else p.danger
    Card(shape=RoundedCornerShape(16.dp), colors=CardDefaults.cardColors(containerColor=p.surface), modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment=Alignment.Top) {
            Box(Modifier.size(8.dp).background(c, CircleShape))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(item.title, fontWeight=FontWeight.Bold, fontSize=11.sp, modifier=Modifier.weight(1f))
                    Text(formatTime(item.timestamp), color=p.muted, fontSize=9.sp)
                }
                Text(item.detail, color=p.muted, fontFamily=FontFamily.Monospace, fontSize=9.sp, maxLines=3, overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

private fun formatTime(timestamp: Long): String = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

@Composable private fun Toggle(title:String,checked:Boolean,onChange:(Boolean)->Unit,p:NexusPalette){Card(colors=CardDefaults.cardColors(containerColor=p.surface),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Switch(checked,onChange)}}}
