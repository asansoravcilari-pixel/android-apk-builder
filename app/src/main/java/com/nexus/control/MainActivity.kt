package com.nexus.control

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val ComponentActivity.store by preferencesDataStore("nexus_preferences")

private val THEME = stringPreferencesKey("theme")
private val ALERTS = booleanPreferencesKey("alerts")
private val SECURITY_ALERTS = booleanPreferencesKey("security_alerts")
private val SERVER_ALERTS = booleanPreferencesKey("server_alerts")
private val BUILD_ALERTS = booleanPreferencesKey("build_alerts")
private val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")

private data class UserPrefs(
    val theme: String = "NEXUS Gece",
    val alerts: Boolean = true,
    val securityAlerts: Boolean = true,
    val serverAlerts: Boolean = true,
    val buildAlerts: Boolean = true,
    val widgetOpacity: Float = 78f
)

private data class NexusPalette(
    val name: String,
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val accent: Color,
    val accent2: Color,
    val text: Color,
    val muted: Color,
    val light: Boolean = false
)

private val palettes = listOf(
    NexusPalette("NEXUS Gece", Color(0xFF06101F), Color(0xFF0C1A2D), Color(0xFF11243B), Color(0xFF47D7FF), Color(0xFF7C5CFF), Color(0xFFF4F8FF), Color(0xFF9BB0CB)),
    NexusPalette("Saf Siyah", Color(0xFF000000), Color(0xFF0D0D0F), Color(0xFF17171B), Color(0xFF45E3FF), Color(0xFF8F6BFF), Color(0xFFFFFFFF), Color(0xFFA5A5B0)),
    NexusPalette("Zümrüt", Color(0xFF061713), Color(0xFF0B261F), Color(0xFF10352B), Color(0xFF4DE2A3), Color(0xFF39B8FF), Color(0xFFF2FFF9), Color(0xFFA0C7B8)),
    NexusPalette("Mor Nebula", Color(0xFF120B1E), Color(0xFF201331), Color(0xFF2E1A46), Color(0xFFB68CFF), Color(0xFF4ED8FF), Color(0xFFFFF8FF), Color(0xFFC3ADD8)),
    NexusPalette("Grafit", Color(0xFF111317), Color(0xFF1B1E24), Color(0xFF272B33), Color(0xFF73CFFF), Color(0xFFD6D9E0), Color(0xFFF7F7F8), Color(0xFFA7ADB8)),
    NexusPalette("Açık", Color(0xFFF4F7FB), Color(0xFFFFFFFF), Color(0xFFEAF0F8), Color(0xFF246BFE), Color(0xFF6E4BFF), Color(0xFF172033), Color(0xFF64748B), light = true)
)

private enum class Page(val title: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Outlined.Dashboard),
    SERVERS("Sunucular", Icons.Outlined.Dns),
    CORE("NEXUS Core", Icons.Outlined.Security),
    TOOLS("Araçlar", Icons.Outlined.Build),
    SETTINGS("Ayarlar", Icons.Outlined.Settings)
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
                    widgetOpacity = p[WIDGET_OPACITY] ?: 78f
                )
            }.collectAsState(initial = UserPrefs())

            val palette = palettes.firstOrNull { it.name == prefs.theme } ?: palettes.first()
            val colorScheme = if (palette.light) {
                lightColorScheme(
                    primary = palette.accent,
                    secondary = palette.accent2,
                    background = palette.background,
                    surface = palette.surface,
                    surfaceVariant = palette.surface2,
                    onBackground = palette.text,
                    onSurface = palette.text
                )
            } else {
                darkColorScheme(
                    primary = palette.accent,
                    secondary = palette.accent2,
                    background = palette.background,
                    surface = palette.surface,
                    surfaceVariant = palette.surface2,
                    onBackground = palette.text,
                    onSurface = palette.text
                )
            }

            var page by remember { mutableStateOf(Page.HOME) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            MaterialTheme(colorScheme = colorScheme) {
                Scaffold(
                    containerColor = palette.background,
                    bottomBar = {
                        NavigationBar(containerColor = palette.surface) {
                            Page.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = page == item,
                                    onClick = { page = item },
                                    icon = { Icon(item.icon, contentDescription = item.title) },
                                    label = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Header(palette, page)
                        when (page) {
                            Page.HOME -> HomePage(palette)
                            Page.SERVERS -> ServersPage(palette)
                            Page.CORE -> CorePage(palette)
                            Page.TOOLS -> ToolsPage(palette)
                            Page.SETTINGS -> SettingsPage(
                                palette = palette,
                                prefs = prefs,
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
                                onRequestPermission = {
                                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onTestNotification = { activity.sendTestNotification() }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
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
        val notification = Notification.Builder(this, "nexus_server")
            .setSmallIcon(R.drawable.nexus_icon)
            .setContentTitle("NEXUS Control")
            .setContentText("Bildirim sistemi hazır. Canlı bağlantı kurulduğunda gerçek olaylar burada görünecek.")
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(2001, notification)
    }
}

@Composable
private fun Header(p: NexusPalette, page: Page) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("NEXUS", color = p.accent, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, fontSize = 13.sp)
            Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .size(42.dp)
                .background(p.surface2, CircleShape)
                .border(1.dp, p.accent.copy(alpha = .35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = "Bildirimler", tint = p.accent)
        }
    }
}

@Composable
private fun HomePage(p: NexusPalette) {
    HeroCard(p)
    SectionTitle("Sistem Özeti", "Canlı bağlantı gelene kadar sahte değer gösterilmez", p)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("CPU", "--", "Canlı veri yok", Icons.Outlined.Memory, p, Modifier.weight(1f))
        MetricCard("RAM", "--", "Canlı veri yok", Icons.Outlined.Storage, p, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Disk", "--", "Canlı veri yok", Icons.Outlined.Storage, p, Modifier.weight(1f))
        MetricCard("WSS", "--", "Doğrulanmadı", Icons.Outlined.Wifi, p, Modifier.weight(1f))
    }

    SectionTitle("Hızlı Erişim", "Sık kullanılan NEXUS alanları", p)
    LazyLikeChips(
        listOf("Core Sağlığı", "Sunucular", "Terminal", "Dosyalar", "API", "Bildirimler"),
        p
    )

    EventCard(p)
}

@Composable
private fun HeroCard(p: NexusPalette) {
    val brush = Brush.linearGradient(listOf(p.accent.copy(alpha = .28f), p.accent2.copy(alpha = .18f), p.surface))
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.background(brush).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).background(p.accent.copy(alpha = .16f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Security, null, tint = p.accent, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("NEXUS Control", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text("Tüm sunucular için tek kontrol merkezi", color = p.muted, fontSize = 13.sp)
                }
            }
            StatusLine("NEXUS Core", "Bağlantı bekleniyor", false, p)
            StatusLine("Build Server", "Bağlantı bekleniyor", false, p)
            HorizontalDivider(color = p.text.copy(alpha = .10f))
            Text("Gerçek API/ajan verisi bağlandığında sağlık, olay, WSS ve servis durumları burada otomatik akacak.", color = p.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ServersPage(p: NexusPalette) {
    SectionTitle("Sunucu Envanteri", "Yetkili bağlantılar eklendiğinde canlılaşır", p)
    ServerCard("NEXUS Core", "Ana üretim sunucusu", listOf("SSH", "API", "WSS", "PostgreSQL", "Redis"), p)
    ServerCard("NEXUS Build Server", "Build / replay / regression", listOf("SSH", "Build", "Runner", "Replay"), p)
    InfoPanel("Sunucu Ekle", "Yeni sunucu ekleme akışı için API/ajan kimliği ve yetkilendirme katmanı bağlanacak.", Icons.Outlined.Add, p)
}

@Composable
private fun ServerCard(name: String, role: String, services: List<String>, p: NexusPalette) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = p.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(p.surface2, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Dns, null, tint = p.accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(role, color = p.muted, fontSize = 12.sp)
                }
                StatusBadge("BEKLEMEDE", false, p)
            }
            Text("IP / uptime / ping / kaynak kullanımı: doğrulanmış bağlantı yok", color = p.muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                services.forEach { ServiceChip(it, p) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Durum") }
                Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Bağlan") }
            }
        }
    }
}

@Composable
private fun CorePage(p: NexusPalette) {
    SectionTitle("Core İzleme", "Evidence-first: doğrulanmayan alanlar bilinmiyor olarak kalır", p)
    StatusGrid(p)
    InfoPanel("Protocol Monitor", "Event-kind ve semantic-field kapsamı ayrı izlenecek. Unknown event fingerprint'leri korunacak.", Icons.Outlined.Security, p)
    InfoPanel("Canlı Olaylar", "Henüz yetkili runtime/replay veri akışı bağlı değil.", Icons.Outlined.Notifications, p)
    InfoPanel("Güvenlik Merkezi", "Bağlantı kurulduğunda servis sağlığı, kritik uyarılar ve oturum olayları burada toplanacak.", Icons.Outlined.Lock, p)
}

@Composable
private fun StatusGrid(p: NexusPalette) {
    val items = listOf("API", "Realtime", "WSS", "PostgreSQL", "Redis", "Runtime", "Transport", "Decoder")
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Core Bileşenleri", fontWeight = FontWeight.Bold)
            items.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { label ->
                        Row(
                            Modifier.weight(1f).background(p.surface2, RoundedCornerShape(14.dp)).padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).background(p.muted, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Doğrulanmadı", color = p.muted, fontSize = 10.sp)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolsPage(p: NexusPalette) {
    SectionTitle("Araç Kutusu", "Modüller için gerçek bağlantı katmanı sonraki adım", p)
    ToolTile("Terminal", "SSH oturumları ve tek tık komutlar", Icons.Outlined.Code, p)
    ToolTile("Dosya Yöneticisi", "Aktarım, görüntüleme ve metin düzenleme", Icons.Outlined.Folder, p)
    ToolTile("API İstemcisi", "Yetkili API istekleri ve yanıt inceleme", Icons.Outlined.Cloud, p)
    ToolTile("Servis Merkezi", "Systemd / Docker servis görünümü", Icons.Outlined.Build, p)
    ToolTile("Ağ Araçları", "Ping, DNS, port ve bağlantı kontrolleri", Icons.Outlined.Wifi, p)
    ToolTile("Log Merkezi", "24 saatlik runtime/socket/debug günlükleri", Icons.Outlined.List, p)
}

@Composable
private fun ToolTile(title: String, detail: String, icon: ImageVector, p: NexusPalette) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(p.accent.copy(alpha = .12f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = p.accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, color = p.muted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ArrowForward, null, tint = p.muted)
        }
    }
}

@Composable
private fun SettingsPage(
    palette: NexusPalette,
    prefs: UserPrefs,
    onTheme: (String) -> Unit,
    onAlerts: (Boolean) -> Unit,
    onSecurityAlerts: (Boolean) -> Unit,
    onServerAlerts: (Boolean) -> Unit,
    onBuildAlerts: (Boolean) -> Unit,
    onWidgetOpacity: (Float) -> Unit,
    onRequestPermission: () -> Unit,
    onTestNotification: () -> Unit
) {
    SectionTitle("Temalar", "6 farklı görünüm", palette)
    palettes.forEach { item ->
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            modifier = Modifier.fillMaxWidth().clickable { onTheme(item.name) }
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(item.background, item.surface2, item.accent, item.accent2).forEach { c ->
                        Box(Modifier.size(16.dp).background(c, CircleShape).border(1.dp, palette.text.copy(alpha = .15f), CircleShape))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                RadioButton(selected = prefs.theme == item.name, onClick = { onTheme(item.name) })
            }
        }
    }

    SectionTitle("Bildirimler", "Kanalları ayrı ayrı yönet", palette)
    ToggleRow("Tüm bildirimler", "Ana bildirim anahtarı", prefs.alerts, onAlerts, palette)
    ToggleRow("Güvenlik", "Oturum, kritik erişim ve alarm olayları", prefs.securityAlerts, onSecurityAlerts, palette)
    ToggleRow("Sunucu", "Servis, bağlantı ve sağlık değişiklikleri", prefs.serverAlerts, onServerAlerts, palette)
    ToggleRow("Build", "Build sonucu ve regression bildirimleri", prefs.buildAlerts, onBuildAlerts, palette)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRequestPermission, modifier = Modifier.weight(1f)) { Text("İzin İste") }
        Button(onClick = onTestNotification, modifier = Modifier.weight(1f)) { Text("Test Gönder") }
    }

    SectionTitle("Widget", "Arka plan şeffaflığı ayarlanabilir", palette)
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = palette.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Widgets, null, tint = palette.accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Widget arka planı", fontWeight = FontWeight.Bold)
                    Text("%${prefs.widgetOpacity.toInt()} opaklık", color = palette.muted, fontSize = 12.sp)
                }
            }
            var slider by remember(prefs.widgetOpacity) { mutableFloatStateOf(prefs.widgetOpacity) }
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { onWidgetOpacity(slider) },
                valueRange = 0f..100f
            )
            Text("0 = tamamen şeffaf · 100 = tamamen dolu", color = palette.muted, fontSize = 11.sp)
        }
    }

    InfoPanel("Sürüm", "NEXUS Control 0.2.0 · geliştirme build'i", Icons.Outlined.Info, palette)
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, p: NexusPalette) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = p.muted, fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String, icon: ImageVector, p: NexusPalette, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = p.accent)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(detail, color = p.muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, p: NexusPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = p.muted, fontSize = 11.sp)
    }
}

@Composable
private fun LazyLikeChips(items: List<String>, p: NexusPalette) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { label ->
            Surface(shape = RoundedCornerShape(14.dp), color = p.surface) {
                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EventCard(p: NexusPalette) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bildirim Merkezi", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                StatusBadge("HAZIR", true, p)
            }
            EventLine("Bildirim kanalları", "Güvenlik · Sunucu · Build", true, p)
            EventLine("Canlı olay akışı", "Bağlantı bekleniyor", false, p)
            EventLine("Widget", "Şeffaflık ayarı kullanılabilir", true, p)
        }
    }
}

@Composable
private fun EventLine(title: String, detail: String, ok: Boolean, p: NexusPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(if (ok) Color(0xFF42E6A4) else p.muted, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(detail, color = p.muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StatusLine(name: String, state: String, online: Boolean, p: NexusPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(if (online) Color(0xFF42E6A4) else p.muted, CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(state, color = p.muted, fontSize = 11.sp)
    }
}

@Composable
private fun StatusBadge(text: String, ok: Boolean, p: NexusPalette) {
    val c = if (ok) Color(0xFF42E6A4) else p.muted
    Surface(shape = RoundedCornerShape(999.dp), color = c.copy(alpha = .12f)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = c, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ServiceChip(text: String, p: NexusPalette) {
    Surface(shape = RoundedCornerShape(999.dp), color = p.surface2) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = p.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoPanel(title: String, detail: String, icon: ImageVector, p: NexusPalette) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = p.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(38.dp).background(p.accent.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = p.accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(detail, color = p.muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}
