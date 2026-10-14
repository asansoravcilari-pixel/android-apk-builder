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
import android.webkit.WebSettings
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
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
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
    SERVERS("Sunucular", "4 sunucu hızlı görünüm ve sağlık takibi", Icons.Outlined.Dns),
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
            val localState = remember(activity) { LocalStateStore(activity) }
            var module by remember { mutableStateOf<Module?>(null) }
            var showSettings by remember { mutableStateOf(false) }
            var showCommandPalette by remember { mutableStateOf(false) }
            var favoriteNames by remember { mutableStateOf(localState.loadFavoriteModules()) }
            var recentNames by remember { mutableStateOf(localState.loadRecentModules()) }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

            BackHandler(enabled = showCommandPalette || module != null || showSettings) {
                when {
                    showCommandPalette -> showCommandPalette = false
                    else -> {
                        module = null
                        showSettings = false
                    }
                }
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
                localState.pushRecentModule(target.name)
                recentNames = localState.loadRecentModules()
                showCommandPalette = false
                closeDrawer()
            }
            fun toggleFavorite(target: Module) {
                favoriteNames = localState.toggleFavoriteModule(target.name)
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
                            favoriteNames = favoriteNames,
                            recentNames = recentNames,
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
                                onSearch = { showCommandPalette = true },
                                favorite = module?.let { it.name in favoriteNames } ?: false,
                                onToggleFavorite = module?.let { selected -> { toggleFavorite(selected) } },
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
                                else -> CommandCenter(palette, prefs, favoriteNames, recentNames, ::openModule)
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }

                if (showCommandPalette) {
                    CommandPaletteDialog(
                        p = palette,
                        favoriteNames = favoriteNames,
                        recentNames = recentNames,
                        onDismiss = { showCommandPalette = false },
                        onHome = {
                            openHome()
                            showCommandPalette = false
                        },
                        onSettings = {
                            openSettings()
                            showCommandPalette = false
                        },
                        onModule = ::openModule
                    )
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
    favoriteNames: Set<String>,
    recentNames: List<String>,
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

            val favoriteModules = Module.entries.filter { it.name in favoriteNames }
            if (favoriteModules.isNotEmpty()) {
                DrawerGroup("FAVORİLER", p)
                favoriteModules.take(5).forEach { m ->
                    DrawerModuleItem(m, currentModule == m, p) { onModule(m) }
                }
            }

            val recentModules = recentNames.mapNotNull { name -> Module.entries.firstOrNull { it.name == name } }
                .filterNot { it.name in favoriteNames }
                .take(3)
            if (recentModules.isNotEmpty()) {
                DrawerGroup("SON KULLANILAN", p)
                recentModules.forEach { m ->
                    DrawerModuleItem(m, currentModule == m, p) { onModule(m) }
                }
            }

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
                    Text("v0.5.0", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Komut paleti · favoriler · canlı cihaz durumu", color = p.muted, fontSize = 10.sp)
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
private fun NexusHeader(
    p: NexusPalette,
    title: String,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    favorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onNotifications: () -> Unit
) {
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
            onClick = onSearch,
            modifier = Modifier.background(p.surface, RoundedCornerShape(14.dp))
        ) { Icon(Icons.Outlined.Search, "Komut paleti", tint = p.text) }
        if (onToggleFavorite != null) {
            Spacer(Modifier.width(5.dp))
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.background(p.surface, RoundedCornerShape(14.dp))
            ) {
                Icon(
                    if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    if (favorite) "Favoriden çıkar" else "Favoriye ekle",
                    tint = if (favorite) p.accent else p.text
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        IconButton(
            onClick = onNotifications,
            modifier = Modifier.background(p.surface, RoundedCornerShape(14.dp))
        ) { Icon(Icons.Outlined.Notifications, "Bildirimler", tint = p.text) }
    }
}


@Composable
private fun CommandPaletteDialog(
    p: NexusPalette,
    favoriteNames: Set<String>,
    recentNames: List<String>,
    onDismiss: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onModule: (Module) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalized = query.trim().lowercase(Locale.getDefault())
    val allModules = remember { Module.entries.toList() }
    val matches = if (normalized.isBlank()) {
        val favorite = allModules.filter { it.name in favoriteNames }
        val recent = recentNames.mapNotNull { name -> allModules.firstOrNull { it.name == name } }
        (favorite + recent + allModules).distinct()
    } else {
        allModules.filter {
            it.title.lowercase(Locale.getDefault()).contains(normalized) ||
                it.subtitle.lowercase(Locale.getDefault()).contains(normalized) ||
                it.name.lowercase(Locale.getDefault()).contains(normalized)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = p.surface,
            tonalElevation = 10.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.82f)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = p.accent)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Komut Paleti", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text("Modül veya işlem ara", color = p.muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Kapat") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Ara: Core, test, terminal, APK...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onHome, label = { Text("Komuta") }, leadingIcon = { Icon(Icons.Outlined.Dashboard, null) })
                    AssistChip(onClick = onSettings, label = { Text("Ayarlar") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) })
                }
                HorizontalDivider(color = p.text.copy(alpha = .08f))
                if (matches.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Eşleşen modül bulunamadı", color = p.muted, fontSize = 12.sp)
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        matches.forEach { m ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onModule(m) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (m.name in favoriteNames) p.accent.copy(alpha = .08f) else p.surface2.copy(alpha = .72f)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(38.dp).background(p.surface, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                        Icon(m.icon, null, tint = p.accent, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(m.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(m.subtitle, color = p.muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (m.name in favoriteNames) Icon(Icons.Outlined.Star, "Favori", tint = p.accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCenter(p: NexusPalette, prefs: UserPrefs, favoriteNames: Set<String>, recentNames: List<String>, open: (Module) -> Unit) {
    val context = LocalContext.current
    val localState = remember(context) { LocalStateStore(context) }
    val scope = rememberCoroutineScope()
    val maintenance = localState.loadMaintenance()
    var history by remember { mutableStateOf(localState.loadDiagnosticHistory()) }
    val recovery = localState.loadRecoverySummary()
    val coreHost = remember(prefs.healthUrl) { runCatching { Uri.parse(prefs.healthUrl).host.orEmpty() }.getOrDefault("") }
    val wssHost = remember(prefs.wssUrl) { runCatching { Uri.parse(prefs.wssUrl).host.orEmpty() }.getOrDefault("") }
    val activeProfile = remember(prefs.healthUrl, prefs.wssUrl) {
        localState.loadCoreProfiles().firstOrNull { it.healthUrl == prefs.healthUrl && it.wssUrl == prefs.wssUrl }
    }
    var scanBusy by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<DiagnosticResult>>(emptyList()) }
    var serverChecks by remember { mutableStateOf(localState.loadServerChecks()) }

    DashboardHero(p, maintenance.active, history.firstOrNull())
    ServerCommandDeck(p, prefs, serverChecks, { serverChecks = it }, open)
    OperationalAdvisor(p, prefs, maintenance, recovery, history, serverChecks, localState.loadServerSlots(), open)
    DevicePulse(p)
    DashboardHealthSweep(
        p = p,
        configured = prefs.healthUrl.startsWith("https://") && coreHost.isNotBlank(),
        busy = scanBusy,
        results = scanResults,
        onRun = {
            if (!scanBusy && prefs.healthUrl.startsWith("https://") && coreHost.isNotBlank()) {
                scope.launch {
                    scanBusy = true
                    try {
                        val fresh = listOf(
                            Diagnostics.dns(coreHost),
                            Diagnostics.tcp(coreHost, 443),
                            Diagnostics.https(prefs.healthUrl),
                            Diagnostics.tls(prefs.healthUrl)
                        )
                        scanResults = fresh
                        localState.appendDiagnostics(fresh)
                        history = localState.loadDiagnosticHistory()
                    } finally {
                        scanBusy = false
                    }
                }
            }
        },
        onOpenTests = { open(Module.TESTS) },
        onConfigure = { open(Module.CORE) }
    )

    val favoriteModules = Module.entries.filter { it.name in favoriteNames }
    val recentModules = recentNames.mapNotNull { name -> Module.entries.firstOrNull { it.name == name } }
        .filterNot { it.name in favoriteNames }
        .take(6)
    if (favoriteModules.isNotEmpty()) {
        DashboardSection("Favori Kontroller", "Sabitlenen modüller", p)
        CompactModuleGrid(favoriteModules.take(6), p, open)
    }
    if (recentModules.isNotEmpty()) {
        DashboardSection("Son Kullanılan", "En son açılan operasyon alanları", p)
        RecentModuleRail(recentModules, p, open)
    }

    DashboardSection("Sistem Matrisi", "Yalnız gerçek yapılandırma ve yerel doğrulamalardan üretilir.", p)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        DashboardStateCard(
            title = "Core",
            state = activeProfile?.name?.uppercase(Locale.getDefault()) ?: if (prefs.healthUrl.isNotBlank()) "PROFİL HAZIR" else "YAPILANDIR",
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
    OperationsFeed(history, maintenance, recovery, serverChecks.values.sortedByDescending { it.checkedAt }, p)

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
                HeroDatum("SÜRÜM", "0.5.0", p)
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
private fun ServerCommandDeck(p: NexusPalette, prefs: UserPrefs, checks: Map<String, ServerCheckSnapshot>, onChecksChanged: (Map<String, ServerCheckSnapshot>) -> Unit, open: (Module) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { LocalStateStore(context) }
    val scope = rememberCoroutineScope()
    val slots = remember { store.loadServerSlots() }
    var selected by remember { mutableIntStateOf(0) }
    var busyId by remember { mutableStateOf<String?>(null) }

    val effective = remember(slots, prefs.healthUrl) {
        slots.map { slot ->
            if (slot.id == "core" && slot.healthUrl.isBlank() && prefs.healthUrl.startsWith("https://")) {
                val host = runCatching { Uri.parse(prefs.healthUrl).host.orEmpty() }.getOrDefault("")
                slot.copy(healthUrl = prefs.healthUrl, host = slot.host.ifBlank { host })
            } else slot
        }
    }
    if (effective.isEmpty()) return
    val selectedSafe = selected.coerceIn(0, effective.lastIndex)
    val active = effective[selectedSafe]
    val last = checks[active.id]
    val activeHost = active.host.ifBlank { runCatching { Uri.parse(active.healthUrl).host.orEmpty() }.getOrDefault("") }
    val configured = activeHost.isNotBlank() || active.healthUrl.startsWith("https://")

    DashboardSection("Sunucu Kontrolü", "4 sunucu arasında tek dokunuşla geçiş ve hızlı sağlık kontrolü", p)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            ScrollableTabRow(
                selectedTabIndex = selectedSafe,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                effective.forEachIndexed { index, slot ->
                    val snapshot = checks[slot.id]
                    Tab(
                        selected = selectedSafe == index,
                        onClick = { selected = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(6.dp).background(snapshot?.let { if (it.ok) p.success else p.danger } ?: p.muted, CircleShape))
                                Text(slot.name.removePrefix("NEXUS "), maxLines = 1, fontSize = 9.sp, fontWeight = if (selectedSafe == index) FontWeight.ExtraBold else FontWeight.Medium)
                            }
                        }
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(active.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(active.role, color = p.muted, fontSize = 9.sp)
                    Text(activeHost.ifBlank { "Host yapılandırılmadı" }, color = p.accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = (when {
                    last == null -> p.muted
                    last.ok -> p.success
                    else -> p.danger
                }).copy(alpha = .10f)) {
                    Text(
                        when {
                            busyId == active.id -> "KONTROL"
                            last == null -> "BEKLEMEDE"
                            last.ok -> "SAĞLIKLI"
                            else -> "UYARI"
                        },
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        color = when { last?.ok == true -> p.success; last != null -> p.danger; else -> p.muted },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ServerResourceStub("CPU", "—", p, Modifier.weight(1f))
                ServerResourceStub("RAM", "—", p, Modifier.weight(1f))
                ServerResourceStub("DISK", "—", p, Modifier.weight(1f))
                ServerResourceStub("PORT", active.port.toString(), p, Modifier.weight(1f))
            }
            Text(
                last?.let { "Son kontrol ${formatTime(it.checkedAt)} · ${it.summary}" } ?: "Henüz bu sunucu için sağlık kontrolü yok.",
                color = p.muted,
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = configured && busyId == null,
                    onClick = {
                        scope.launch {
                            busyId = active.id
                            try {
                                val host = activeHost
                                val results = buildList {
                                    if (host.isNotBlank()) {
                                        add(Diagnostics.dns(host))
                                        add(Diagnostics.tcp(host, active.port))
                                    }
                                    if (active.healthUrl.startsWith("https://")) {
                                        add(Diagnostics.https(active.healthUrl))
                                        add(Diagnostics.tls(active.healthUrl))
                                    }
                                }
                                if (results.isNotEmpty()) {
                                    store.saveServerCheck(active.id, results)
                                    store.appendDiagnostics(results)
                                    onChecksChanged(store.loadServerChecks())
                                }
                            } finally { busyId = null }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (busyId == active.id) "Kontrol..." else if (configured) "Hızlı Kontrol" else "Yapılandır") }
                OutlinedButton(onClick = { open(Module.SERVERS) }, modifier = Modifier.weight(1f)) { Text("Sunucular") }
            }
        }
    }
}

@Composable
private fun ServerResourceStub(label: String, value: String, p: NexusPalette, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = p.surface2.copy(alpha = .72f)) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ServerEditorCard(slot: ServerSlot, lastCheck: ServerCheckSnapshot?, p: NexusPalette, onSave: (ServerSlot) -> Unit) {
    var name by remember(slot.id, slot.name) { mutableStateOf(slot.name) }
    var role by remember(slot.id, slot.role) { mutableStateOf(slot.role) }
    var host by remember(slot.id, slot.host) { mutableStateOf(slot.host) }
    var port by remember(slot.id, slot.port) { mutableStateOf(slot.port.toString()) }
    var health by remember(slot.id, slot.healthUrl) { mutableStateOf(slot.healthUrl) }
    var expanded by remember(slot.id) { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = p.surface), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(p.surface2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Dns, null, tint = p.accent) }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(slot.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(lastCheck?.let { "${if (it.ok) "PASS" else "FAIL"} · ${formatTime(it.checkedAt)}" } ?: "Kontrol bekleniyor", color = if (lastCheck?.ok == true) p.success else p.muted, fontSize = 9.sp)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = p.muted)
            }
            if (expanded) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Görünen ad") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(role, { role = it.take(80) }, label = { Text("Rol") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(host, { host = it.trim().take(255) }, label = { Text("Host / IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, modifier = Modifier.weight(.35f), singleLine = true)
                    OutlinedTextField(health, { health = it.trim().take(1000) }, label = { Text("HTTPS Health URL") }, modifier = Modifier.weight(.65f), singleLine = true)
                }
                Button(
                    enabled = (port.toIntOrNull()?.let { it in 1..65535 } == true) && (health.isBlank() || health.startsWith("https://")),
                    onClick = { onSave(slot.copy(name = name, role = role, host = host, port = port.toIntOrNull() ?: 443, healthUrl = health)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sunucu Profilini Kaydet") }
            }
        }
    }
}

@Composable
private fun OperationalAdvisor(
    p: NexusPalette,
    prefs: UserPrefs,
    maintenance: MaintenanceSnapshot,
    recovery: Pair<Long, String>?,
    history: List<DiagnosticHistoryItem>,
    serverChecks: Map<String, ServerCheckSnapshot>,
    slots: List<ServerSlot>,
    open: (Module) -> Unit
) {
    val configuredCount = slots.count { it.host.isNotBlank() || it.healthUrl.isNotBlank() } +
        if (slots.none { it.id == "core" && (it.host.isNotBlank() || it.healthUrl.isNotBlank()) } && prefs.healthUrl.isNotBlank()) 1 else 0
    val failedServers = serverChecks.values.count { !it.ok }
    val recentFailures = history.take(8).count { !it.ok }
    val recommendations = buildList {
        if (maintenance.active) add("Bakım oturumu aktif; çıkıştan önce sağlık taraması çalıştır.")
        if (configuredCount < 4) add("${4 - configuredCount} sunucu profili bağlantı bilgisi bekliyor.")
        if (failedServers > 0) add("$failedServers sunucu son kontrolde hata verdi; Test Merkezi'nden ayrıntıyı doğrula.")
        if (recentFailures > 0) add("Son test geçmişinde $recentFailures başarısız doğrulama var.")
        if (recovery == null) add("Henüz recovery preflight kaydı yok; kritik değişiklikten önce bir preflight çalıştır.")
        if (isEmpty()) add("Yerel kontrollerde açık uyarı görünmüyor. Canlı Core telemetrisi bağlanınca kapsam genişleyecek.")
    }.take(3)
    val healthy = !maintenance.active && failedServers == 0 && recentFailures == 0 && configuredCount >= 1

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background((if (healthy) p.success else p.accent2).copy(alpha = .10f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Bolt, null, tint = if (healthy) p.success else p.accent2, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Akıllı Operasyon Özeti", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Text("Yalnız doğrulanmış yerel durum ve test geçmişinden türetilir", color = p.muted, fontSize = 9.sp)
                }
                Pill(if (healthy) "NORMAL" else "DİKKAT", healthy, p)
            }
            recommendations.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("${index + 1}", color = p.accent, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.width(18.dp))
                    Text(item, color = p.text, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open(Module.SERVERS) }, modifier = Modifier.weight(1f)) { Text("Sunucular") }
                Button(onClick = { open(Module.TESTS) }, modifier = Modifier.weight(1f)) { Text("Test Merkezi") }
            }
        }
    }
}

@Composable
private fun DevicePulse(p: NexusPalette) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(DeviceStatus.read(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            snapshot = DeviceStatus.read(context)
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = p.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cihaz Nabzı", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Sunucu metriği değil; bu Android cihazından gerçek veri", color = p.muted, fontSize = 9.sp)
                }
                Box(
                    Modifier.size(34.dp).background(p.accent.copy(alpha = .10f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.PhoneAndroid, null, tint = p.accent, modifier = Modifier.size(19.dp)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulseMetric(
                    label = "AĞ",
                    value = snapshot.networkLabel,
                    detail = if (snapshot.networkValidated) "Doğrulandı" else "Doğrulanmadı",
                    good = snapshot.networkValidated,
                    icon = Icons.Outlined.Wifi,
                    p = p,
                    modifier = Modifier.weight(1f)
                )
                PulseMetric(
                    label = "BATARYA",
                    value = snapshot.batteryPercent?.let { "$it%" } ?: "?",
                    detail = if (snapshot.charging) "Şarj oluyor" else "Batarya",
                    good = snapshot.batteryPercent?.let { it >= 25 } ?: false,
                    icon = Icons.Outlined.BatteryStd,
                    p = p,
                    modifier = Modifier.weight(1f)
                )
                PulseMetric(
                    label = "DEPOLAMA",
                    value = snapshot.storageUsedPercent?.let { "$it%" } ?: "?",
                    detail = snapshot.storageFreeBytes?.let { "${formatBytes(it)} boş" } ?: "Bilinmiyor",
                    good = snapshot.storageUsedPercent?.let { it < 90 } ?: false,
                    icon = Icons.Outlined.Storage,
                    p = p,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PulseMetric(
    label: String,
    value: String,
    detail: String,
    good: Boolean,
    icon: ImageVector,
    p: NexusPalette,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = p.surface2.copy(alpha = .76f)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = p.accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(6.dp).background(if (good) p.success else p.muted, CircleShape))
            }
            Text(label, color = p.muted, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = p.muted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentModuleRail(items: List<Module>, p: NexusPalette, open: (Module) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { module ->
            Surface(
                modifier = Modifier.width(150.dp).clickable { open(module) },
                shape = RoundedCornerShape(17.dp),
                color = p.surface
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(p.surface2, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                        Icon(module.icon, null, tint = p.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(module.title, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Son kullanılan", color = p.muted, fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return String.format(Locale.US, if (value >= 10) "%.0f %s" else "%.1f %s", value, units[index])
}

@Composable
private fun DashboardHealthSweep(
    p: NexusPalette,
    configured: Boolean,
    busy: Boolean,
    results: List<DiagnosticResult>,
    onRun: () -> Unit,
    onOpenTests: () -> Unit,
    onConfigure: () -> Unit
) {
    val pass = results.count { it.ok }
    val fail = results.size - pass
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = p.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).background(p.accent.copy(alpha = .10f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Bolt, null, tint = p.accent, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hızlı Sağlık Taraması", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Text("DNS · TCP 443 · HTTPS · TLS", color = p.muted, fontSize = 9.sp)
                }
                if (results.isNotEmpty()) {
                    Pill("$pass PASS", pass > 0 && fail == 0, p)
                    if (fail > 0) { Spacer(Modifier.width(5.dp)); Pill("$fail FAIL", false, p) }
                }
            }
            if (results.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    results.forEach { item ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = (if (item.ok) p.success else p.danger).copy(alpha = .08f)
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(6.dp).background(if (item.ok) p.success else p.danger, CircleShape))
                                Spacer(Modifier.height(4.dp))
                                Text(item.title.take(7), fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = configured && !busy,
                    onClick = onRun,
                    modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Taranıyor..." else if (configured) "Şimdi Tara" else "Core Yapılandır") }
                OutlinedButton(
                    onClick = if (configured) onOpenTests else onConfigure,
                    modifier = Modifier.weight(1f)
                ) { Text(if (configured) "Test Merkezi" else "Ayarla") }
            }
        }
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
    serverChecks: List<ServerCheckSnapshot>,
    p: NexusPalette
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (maintenance.active) FeedRow("Bakım modu", maintenance.note.ifBlank { "Aktif bakım oturumu" }, p.danger, p)
            recovery?.let { FeedRow("Recovery preflight", formatTime(it.first), p.accent2, p) }
            serverChecks.take(2).forEach { item ->
                FeedRow("Sunucu · ${item.serverId}", "${if (item.ok) "PASS" else "FAIL"} · ${formatTime(item.checkedAt)}", if (item.ok) p.success else p.danger, p)
            }
            history.take(4).forEach { item ->
                FeedRow(item.title, "${if (item.ok) "PASS" else "FAIL"} · ${formatTime(item.timestamp)}", if (item.ok) p.success else p.danger, p)
            }
            if (!maintenance.active && recovery == null && history.isEmpty() && serverChecks.isEmpty()) {
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
    val context = LocalContext.current
    val localState = remember(context) { LocalStateStore(context) }
    val activeProfile = remember(prefs.healthUrl, prefs.wssUrl) { localState.loadCoreProfiles().firstOrNull { it.healthUrl == prefs.healthUrl && it.wssUrl == prefs.wssUrl } }
    val scope=rememberCoroutineScope();var r by remember{mutableStateOf<DiagnosticResult?>(null)}
    SectionHeader("NEXUS Core Modu","Endpoint tahmin edilmez; tam URL Ayarlar'dan girilir.",p)
    if(activeProfile!=null) InfoCard("Aktif Core Profili",activeProfile.name,Icons.Outlined.Dns,p)
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

@Composable
private fun ServersMode(p: NexusPalette) {
    val context = LocalContext.current
    val store = remember(context) { LocalStateStore(context) }
    var slots by remember { mutableStateOf(store.loadServerSlots()) }
    val checks = remember(slots) { store.loadServerChecks() }

    SectionHeader("4 Sunucu Merkezi", "Ana ekrandaki dört sekmenin bağlantı bilgilerini burada yönet.", p)
    slots.forEach { slot ->
        ServerEditorCard(
            slot = slot,
            lastCheck = checks[slot.id],
            p = p,
            onSave = { updated -> slots = store.saveServerSlot(updated) }
        )
    }
    InfoCard(
        "Canlı CPU / RAM / Disk",
        "Kaynak kullanımı yalnız doğrulanmış agent/API metriği bağlandığında gösterilecek. Şimdilik değer uydurulmaz.",
        Icons.Outlined.Storage,
        p
    )
}

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
        if (r.endpointCandidates.isNotEmpty()) Report("Endpoint / domain adayları (${r.endpointCandidates.size})", r.endpointCandidates.joinToString("\n"), p, true)
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
private fun BrowserMode(p: NexusPalette) {
    val context = LocalContext.current
    val stateStore = remember(context) { LocalStateStore(context) }
    val mobileUa = remember(context) { WebSettings.getDefaultUserAgent(context) }
    val desktopUa = remember { "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36 NEXUS-Control/0.5" }
    var input by remember { mutableStateOf("https://") }
    var target by remember { mutableStateOf<String?>(null) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Yeni sekme") }
    var status by remember { mutableStateOf("Hazır") }
    var progress by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(stateStore.loadBrowserHistory()) }
    var bookmarks by remember { mutableStateOf(stateStore.loadBrowserBookmarks()) }
    var desktopMode by remember { mutableStateOf(false) }

    fun navigate(raw: String) {
        val normalized = when {
            raw.startsWith("https://") -> raw
            raw.contains("://") -> ""
            raw.isNotBlank() -> "https://$raw"
            else -> ""
        }
        if (normalized.isNotBlank()) {
            input = normalized
            target = normalized
            status = "Yükleniyor"
            stateStore.saveBrowserHistory(normalized)
            history = stateStore.loadBrowserHistory()
        } else status = "Yalnız HTTPS adresleri kabul edilir"
    }

    SectionHeader("Teknik Tarayıcı", "HTTPS, favori, masaüstü görünüm, geçmiş ve oturum kontrolü.", p)
    InputCard(p) {
        OutlinedTextField(input, { input = it }, label = { Text("Adres") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { web?.goBack() }, enabled = web?.canGoBack() == true) { Icon(Icons.Outlined.ArrowBack, "Geri") }
            IconButton(onClick = { web?.goForward() }, enabled = web?.canGoForward() == true) { Icon(Icons.Outlined.ArrowForward, "İleri") }
            IconButton(onClick = { web?.reload() }, enabled = web != null) { Icon(Icons.Outlined.Refresh, "Yenile") }
            IconButton(
                onClick = { bookmarks = stateStore.toggleBrowserBookmark(input) },
                enabled = input.startsWith("https://")
            ) { Icon(if (input in bookmarks) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Favori") }
            Button(onClick = { navigate(input) }, modifier = Modifier.weight(1f)) { Text("Git") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pageTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, color = p.muted, fontSize = 10.sp, maxLines = 2)
            }
            Text("$progress%", color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = desktopMode,
                onClick = { desktopMode = !desktopMode; web?.settings?.userAgentString = if (desktopMode) desktopUa else mobileUa; web?.reload() },
                label = { Text("Masaüstü") },
                leadingIcon = { Icon(Icons.Outlined.DesktopWindows, null, modifier = Modifier.size(16.dp)) }
            )
            OutlinedButton(
                onClick = { input.takeIf { it.startsWith("https://") }?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } } },
                modifier = Modifier.weight(1f)
            ) { Text("Harici Aç") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    WebStorage.getInstance().deleteAllData()
                    web?.clearCache(true)
                    status = "Cookie, WebStorage ve cache temizlendi"
                },
                modifier = Modifier.weight(1f)
            ) { Text("Oturumu Temizle") }
            OutlinedButton(onClick = { stateStore.clearBrowserHistory(); history = emptyList() }, modifier = Modifier.weight(1f)) { Text("Geçmişi Sil") }
        }
    }

    if (bookmarks.isNotEmpty()) {
        Text("Favoriler", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            bookmarks.take(10).forEach { url ->
                AssistChip(onClick = { navigate(url) }, label = { Text(Uri.parse(url).host ?: url, maxLines = 1) }, leadingIcon = { Icon(Icons.Outlined.Star, null, modifier = Modifier.size(14.dp)) })
            }
        }
    }
    if (history.isNotEmpty()) {
        Text("Geçmiş", fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            history.take(8).forEach { url -> AssistChip(onClick = { navigate(url) }, label = { Text(Uri.parse(url).host ?: url, maxLines = 1) }) }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(520.dp)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = true
                    settings.userAgentString = if (desktopMode) desktopUa else mobileUa
                    CookieManager.getInstance().setAcceptCookie(true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        override fun onReceivedTitle(view: WebView?, title: String?) { if (!title.isNullOrBlank()) pageTitle = title }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            return if (url.startsWith("https://")) false else { status = "HTTP/özel şema engellendi: $url"; true }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            status = "Yüklendi"
                            url?.takeIf { it.startsWith("https://") }?.let { loaded ->
                                input = loaded
                                stateStore.saveBrowserHistory(loaded)
                                history = stateStore.loadBrowserHistory()
                            }
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) status = "Hata ${error?.errorCode}: ${error?.description}"
                        }
                    }
                    web = this
                }
            },
            update = { view ->
                view.settings.userAgentString = if (desktopMode) desktopUa else mobileUa
                target?.let { url -> if (view.url != url) view.loadUrl(url) }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun TerminalMode(p: NexusPalette) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { LocalStateStore(context) }
    val serverSlots = remember { store.loadServerSlots() }
    var mode by remember { mutableIntStateOf(0) }
    var selectedServer by remember { mutableIntStateOf(0) }
    var command by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("NEXUS Local Shell hazır.\nKomutlar Android uygulama sandbox'ında çalışır; root değildir.") }
    val history = remember { mutableStateListOf<ShellResult>() }

    SectionHeader("NEXUS Terminal", "Cihaz shell'i gerçek çalışır; sunucu terminali doğrulanmış SSH/Core Agent backend ile açılacak.", p)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Cihaz") }, modifier = Modifier.weight(1f))
        FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Sunucu") }, modifier = Modifier.weight(1f))
    }

    if (mode == 0) {
        InputCard(p) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("pwd", "id", "uname -a", "getprop ro.build.version.release").forEach { preset ->
                    AssistChip(onClick = { command = preset }, label = { Text(preset, maxLines = 1) })
                }
            }
            OutlinedTextField(
                value = command,
                onValueChange = { command = it.take(4000) },
                label = { Text("/system/bin/sh komutu") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
            )
            Button(
                enabled = command.isNotBlank() && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        val result = LocalShell.run(command)
                        history.add(0, result)
                        while (history.size > 12) history.removeAt(history.lastIndex)
                        output = buildString {
                            append("$ ").appendLine(result.command)
                            append(result.output)
                            if (result.exitCode != null) append("\n[exit ${result.exitCode}]")
                            if (result.timedOut) append("\n[timeout]")
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Çalışıyor..." else "Komutu Çalıştır") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF020407)), modifier = Modifier.fillMaxWidth()) {
            Text(output, color = Color(0xFF66E5B1), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp), fontSize = 11.sp)
        }
        if (history.isNotEmpty()) {
            Text("Son komutlar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                history.take(8).forEach { item ->
                    AssistChip(onClick = { command = item.command }, label = { Text(item.command.take(24), maxLines = 1) })
                }
            }
        }
    } else {
        ScrollableTabRow(selectedTabIndex = selectedServer.coerceIn(0, serverSlots.lastIndex.coerceAtLeast(0)), edgePadding = 0.dp, divider = {}) {
            serverSlots.forEachIndexed { index, slot ->
                Tab(selected = selectedServer == index, onClick = { selectedServer = index }, text = { Text(slot.name, maxLines = 1, fontSize = 10.sp) })
            }
        }
        val slot = serverSlots.getOrNull(selectedServer)
        if (slot != null) {
            InfoCard(slot.name, "${slot.role}\n${slot.host.ifBlank { "Host yapılandırılmadı" }}:${slot.port}", Icons.Outlined.Dns, p)
        }
        Capability("SSH / Core Agent oturumu", "Backend ve host-key doğrulaması tamamlanınca aktif", false, p)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF020407)), modifier = Modifier.fillMaxWidth()) {
            Text("NEXUS Server Terminal\n\nSahte SSH çıktısı üretilmiyor.\nSunucu komutları için doğrulanmış bağlantı capability'si bekleniyor.", color = Color(0xFF66E5B1), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp), fontSize = 11.sp)
        }
    }
}


@Composable private fun FileMode(p:NexusPalette){val context=LocalContext.current;val scope=rememberCoroutineScope();var name by remember{mutableStateOf("Dosya seçilmedi")};var preview by remember{mutableStateOf("")};val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{val x=FilePreview.read(context,uri);name=x.first;preview=x.second}};SectionHeader("Dosya Merkezi","Yerel dosya seçme ve ilk 100 KB metin önizleme.",p);Button(onClick={picker.launch(arrayOf("text/*","application/json","application/xml","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Text("Dosya Seç")};Report(name,preview.ifBlank{"Önizleme yok"},p,true)}

@Composable private fun MailMode(p:NexusPalette){val c=LocalContext.current;var to by remember{mutableStateOf("")};var subject by remember{mutableStateOf("")};SectionHeader("Mail Center","Şimdilik Android e-posta istemcisine güvenli devir.",p);InputCard(p){OutlinedTextField(to,{to=it},label={Text("Alıcı")},modifier=Modifier.fillMaxWidth());OutlinedTextField(subject,{subject=it},label={Text("Konu")},modifier=Modifier.fillMaxWidth());Button(onClick={val i=Intent(Intent.ACTION_SENDTO).apply{data=Uri.parse("mailto:");putExtra(Intent.EXTRA_EMAIL,arrayOf(to));putExtra(Intent.EXTRA_SUBJECT,subject)};runCatching{c.startActivity(i)}},modifier=Modifier.fillMaxWidth()){Text("E-posta Oluştur")}};Capability("IMAP / SMTP","Hesap/OAuth sözleşmesi bağlanınca aktif",false,p)}
@Composable private fun NotificationsMode(p:NexusPalette,notify:()->Unit){SectionHeader("Bildirim Merkezi","Android kanalları hazır.",p);Button(onClick=notify,modifier=Modifier.fillMaxWidth()){Text("Test Bildirimi Gönder")};Capability("Güvenlik kanalı","Hazır",true,p);Capability("Sunucu kanalı","Hazır",true,p);Capability("Build kanalı","Hazır",true,p);Capability("Canlı alarm üretimi","Core olay akışı gerekli",false,p)}

@Composable
private fun SettingsPage(p:NexusPalette,prefs:UserPrefs,onTheme:(String)->Unit,onAlerts:(Boolean)->Unit,onSecurityAlerts:(Boolean)->Unit,onServerAlerts:(Boolean)->Unit,onBuildAlerts:(Boolean)->Unit,onWidgetOpacity:(Float)->Unit,saveHealthUrl:(String)->Unit,saveWssUrl:(String)->Unit,requestPermission:()->Unit,testNotification:()->Unit){
    val context = LocalContext.current
    val localState = remember(context) { LocalStateStore(context) }
    var coreProfiles by remember { mutableStateOf(localState.loadCoreProfiles()) }
    var profileName by remember { mutableStateOf("") }
    SectionHeader("Görünüm","6 tema ve widget şeffaflığı",p)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){palettes.forEach{item->Card(modifier=Modifier.width(145.dp).clickable{onTheme(item.name)},colors=CardDefaults.cardColors(containerColor=if(prefs.theme==item.name)p.surface2 else p.surface)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(item.background,item.surface2,item.accent,item.accent2).forEach{c->Box(Modifier.size(18.dp).background(c,CircleShape))}};Text(item.name,fontWeight=FontWeight.Bold,fontSize=12.sp);if(prefs.theme==item.name)Text("AKTİF",color=p.accent,fontSize=9.sp,fontWeight=FontWeight.Bold)}}}}
    InputCard(p){Text("Widget arka planı · %${prefs.widgetOpacity.toInt()}",fontWeight=FontWeight.Bold);var slider by remember(prefs.widgetOpacity){mutableFloatStateOf(prefs.widgetOpacity)};Slider(value=slider,onValueChange={slider=it},onValueChangeFinished={onWidgetOpacity(slider)},valueRange=0f..100f);Text("0 = şeffaf · 100 = dolu",color=p.muted,fontSize=11.sp)}
    SectionHeader("Core Bağlantısı","Endpoint tahmin edilmez; gerçek URL'yi gir.",p)
    var health by remember(prefs.healthUrl){mutableStateOf(prefs.healthUrl)};var wss by remember(prefs.wssUrl){mutableStateOf(prefs.wssUrl)}
    InputCard(p){
        OutlinedTextField(health,{health=it.trim()},label={Text("Tam HTTPS Health URL")},modifier=Modifier.fillMaxWidth())
        Button(enabled=health.isBlank()||health.startsWith("https://"),onClick={saveHealthUrl(health)},modifier=Modifier.fillMaxWidth()){Text("Health URL Kaydet")}
        OutlinedTextField(wss,{wss=it.trim()},label={Text("Tam WSS URL")},modifier=Modifier.fillMaxWidth())
        Button(enabled=wss.isBlank()||wss.startsWith("wss://"),onClick={saveWssUrl(wss)},modifier=Modifier.fillMaxWidth()){Text("WSS URL Kaydet")}
        HorizontalDivider(color=p.text.copy(alpha=.08f))
        Text("Core Profili",fontWeight=FontWeight.Bold,fontSize=12.sp)
        OutlinedTextField(profileName,{profileName=it.take(60)},label={Text("Profil adı · Production / Build / Test")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Button(
            enabled = profileName.isNotBlank() && (health.isBlank() || health.startsWith("https://")) && (wss.isBlank() || wss.startsWith("wss://")),
            onClick = { coreProfiles = runCatching { localState.saveCoreProfile(profileName, health, wss) }.getOrElse { coreProfiles } },
            modifier = Modifier.fillMaxWidth()
        ){Text("Bu Bağlantıyı Profil Olarak Kaydet")}
    }
    if(coreProfiles.isNotEmpty()){
        SectionHeader("Kayıtlı Core Profilleri","Bir profile dokunarak endpointleri aktif bağlantıya uygula.",p)
        coreProfiles.forEach{profile->
            Card(colors=CardDefaults.cardColors(containerColor=p.surface),modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(38.dp).background(p.surface2,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Dns,null,tint=p.accent)}
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)){
                        Text(profile.name,fontWeight=FontWeight.Bold,fontSize=12.sp)
                        Text(profile.healthUrl.ifBlank{"Health yok"},color=p.muted,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(profile.wssUrl.ifBlank{"WSS yok"},color=p.muted,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    IconButton(onClick={
                        health=profile.healthUrl
                        wss=profile.wssUrl
                        saveHealthUrl(profile.healthUrl)
                        saveWssUrl(profile.wssUrl)
                    }){Icon(Icons.Outlined.CheckCircle,"Profili etkinleştir",tint=p.success)}
                    IconButton(onClick={coreProfiles=localState.deleteCoreProfile(profile.id)}){Icon(Icons.Outlined.DeleteOutline,"Profili sil",tint=p.danger)}
                }
            }
        }
    }
    SectionHeader("Bildirimler","Kanalları ayrı yönet",p);Toggle("Tüm bildirimler",prefs.alerts,onAlerts,p);Toggle("Güvenlik",prefs.securityAlerts,onSecurityAlerts,p);Toggle("Sunucu",prefs.serverAlerts,onServerAlerts,p);Toggle("Build",prefs.buildAlerts,onBuildAlerts,p);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=requestPermission,modifier=Modifier.weight(1f)){Text("İzin İste")};Button(onClick=testNotification,modifier=Modifier.weight(1f)){Text("Test")}}
    InfoCard("Sürüm","NEXUS Control 0.5.0 · Komut paleti, favoriler, cihaz nabzı ve Core profilleri",Icons.Outlined.Info,p)
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
