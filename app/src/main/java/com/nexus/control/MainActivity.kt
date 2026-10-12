package com.nexus.control

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val ComponentActivity.store by preferencesDataStore("nexus_preferences")
private val THEME = stringPreferencesKey("theme")
private val ALERTS = booleanPreferencesKey("alerts")
private val palettes = listOf(
 "Gece Siyahı" to listOf(0xFF090D16,0xFF141B2A,0xFF58D7FF),
 "NEXUS Mavi" to listOf(0xFF07192F,0xFF112C49,0xFF52B8FF),
 "Zümrüt" to listOf(0xFF071D19,0xFF10382D,0xFF4DE2A3),
 "Mor" to listOf(0xFF171023,0xFF2B1B42,0xFFBB93FF),
 "Grafit" to listOf(0xFF17191D,0xFF292D33,0xFFD5D8E0),
 "Açık" to listOf(0xFFF5F7FC,0xFFFFFFFF,0xFF2563EB)
)
class MainActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState)
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel("nexus_security","Güvenlik alarmları",NotificationManager.IMPORTANCE_HIGH))
  nm.createNotificationChannel(NotificationChannel("nexus_status","Sunucu durumları",NotificationManager.IMPORTANCE_DEFAULT))
  setContent {
   val scope=rememberCoroutineScope()
   val prefs by store.data.map { (it[THEME] ?: "Gece Siyahı") to (it[ALERTS] ?: true) }.collectAsState(initial="Gece Siyahı" to true)
   val selected=palettes.firstOrNull { it.first==prefs.first } ?: palettes.first()
   val light=selected.first=="Açık"
   val bg=Color(selected.second[0]); val surface=Color(selected.second[1]); val accent=Color(selected.second[2])
   val foreground=if(light) Color(0xFF172033) else Color(0xFFE8F1FF)
   val scheme=if(light) lightColorScheme(primary=accent,background=bg,surface=surface,onBackground=foreground,onSurface=foreground) else darkColorScheme(primary=accent,background=bg,surface=surface,onBackground=foreground,onSurface=foreground)
   var page by remember { mutableStateOf("Ana Sayfa") }
   val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
   MaterialTheme(colorScheme=scheme) {
    Scaffold(containerColor=bg,bottomBar={ NavigationBar(containerColor=surface) {
     listOf("Ana Sayfa","Sunucular","Core","Araçlar","Ayarlar").forEachIndexed { i,name ->
      val icon=listOf(Icons.Outlined.Dashboard,Icons.Outlined.Dns,Icons.Outlined.Security,Icons.Outlined.Build,Icons.Outlined.Settings)[i]
      NavigationBarItem(selected=page==name,onClick={page=name},icon={Icon(icon,null)},label={Text(name)})
     }
    } }) { pad -> Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
      Text("◆ NEXUS CONTROL",color=accent,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
      Text(page,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
      when(page) {
       "Ana Sayfa" -> { InfoCard("Komuta merkezi","Sunucular, güvenlik, bağlantılar ve görevler tek noktada.",surface,accent)
        InfoCard("Bağlantı bekleniyor","Henüz doğrulanmış sunucu bağlantısı yok. Sahte canlı veri gösterilmez.",surface,accent)
        InfoCard("Hızlı erişim","Sunucular · NEXUS Core · Araçlar · Ayarlar",surface,accent) }
       "Sunucular" -> { InfoCard("Sunucu envanteri","API/ajan ve SSH bağlantı modülleri sonraki aşamada eklenecek.",surface,accent)
        InfoCard("Bağlantı durumu","Doğrulanmadı",surface,accent) }
       "Core" -> { listOf("Core aktifliği","WSS bağlantıları","Protokol olayları","Güvenlik denetimi","GOZO oturum alarmı").forEach { InfoCard(it,"Canlı veri bağlantısı kurulmadı · Doğrulanmadı",surface,accent) } }
       "Araçlar" -> { listOf("Yerel terminal","Dosya aktarımı","Kod editörü","API istemcisi","Tarayıcı","Docker / Veritabanları").forEach { InfoCard(it,"Planlanan modül · Henüz etkin değil",surface,accent) } }
       "Ayarlar" -> {
        Text("Görünüm",fontWeight=FontWeight.SemiBold)
        palettes.forEach { (name,colors) -> Card(colors=CardDefaults.cardColors(containerColor=surface)) { Row(Modifier.fillMaxWidth().clickable { scope.launch { store.edit { it[THEME]=name } } }.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
         Box(Modifier.size(20.dp).background(Color(colors[2]),RoundedCornerShape(6.dp)))
         Spacer(Modifier.width(12.dp)); Text(name,Modifier.weight(1f)); RadioButton(selected=name==selected.first,onClick={scope.launch { store.edit { it[THEME]=name } }})
        } } }
        HorizontalDivider()
        Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Bildirimler"); Text("Güvenlik ve sunucu uyarıları",style=MaterialTheme.typography.bodySmall) }
         Switch(checked=prefs.second,onCheckedChange={enabled -> scope.launch { store.edit { it[ALERTS]=enabled } }; if(enabled && Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)})
        }
        InfoCard("Ana ekran widget'ı","Android ana ekranında boş alana uzun bas → Widget'lar → NEXUS Control",surface,accent)
        InfoCard("Güvenlik","Kimlik bilgileri bu sürümde toplanmaz. Henüz API/SSH bağlantısı yok.",surface,accent)
       }
      }
    } }
   }
  }
 }
}
@Composable private fun InfoCard(title:String,detail:String,surface:Color,accent:Color) {
 Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=surface),modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
   Text(title,fontWeight=FontWeight.Bold,color=accent)
   Text(detail,style=MaterialTheme.typography.bodyMedium)
  }
 }
}
