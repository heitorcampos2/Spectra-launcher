package com.hazagou.spectra.ui.home

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hazagou.spectra.HomeItem
import com.hazagou.spectra.HomeRowModel
import com.hazagou.spectra.MainViewModel
import com.hazagou.spectra.MovingApp
import com.hazagou.spectra.Screen
import com.hazagou.spectra.ShortcutKind
import com.hazagou.spectra.data.AnimalIcon
import com.hazagou.spectra.data.AppInfo
import com.hazagou.spectra.data.HomeSection
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.data.SectionType
import com.hazagou.spectra.data.TvInput
import com.hazagou.spectra.ui.apps.AppOptionsDialog
import com.hazagou.spectra.ui.components.AnimalIconView
import com.hazagou.spectra.ui.components.Avatar
import com.hazagou.spectra.ui.components.BatteryIcon
import com.hazagou.spectra.ui.components.FocusCard
import com.hazagou.spectra.ui.components.WifiIcon
import com.hazagou.spectra.ui.sections.AppPickerDialog
import com.hazagou.spectra.ui.sections.InputOptionsDialog
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FocusInfo(val title: String, val subtitle: String?, val description: String?, val source: String)

fun runShortcut(kind: ShortcutKind, vm: MainViewModel) {
    when (kind) {
        ShortcutKind.ALL_APPS -> vm.open(Screen.APPS)
        ShortcutKind.EDIT_SECTIONS -> vm.open(Screen.SECTIONS)
        ShortcutKind.WALLPAPERS -> vm.open(Screen.WALLPAPER)
        ShortcutKind.CUSTOMIZE -> vm.open(Screen.SETTINGS)
        ShortcutKind.NOTIFICATIONS -> { vm.sidePanelOpen.value = false; vm.notificationsOpen.value = true }
        ShortcutKind.ANDROID_SETTINGS -> vm.system.openSystemSettings()
    }
}

// ------------------------------------------------------------------------------------------ topo

@Composable
fun TopBar(vm: MainViewModel, settings: LauncherSettings) {
    Row(
        Modifier.fillMaxWidth().padding(start = settings.startMargin.dp, end = 40.dp, top = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (settings.showTitle) TitleBlock(settings, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        StatusPill(vm, settings)
    }
}

@Composable
fun StatusPill(vm: MainViewModel, settings: LauncherSettings) {
    val ui = LocalUi.current
    val context = LocalContext.current
    val net by vm.network.collectAsState()
    val battery by vm.battery.collectAsState()
    val notifs by vm.notifications.collectAsState()
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (settings.showSearch) PillIcon(Icons.Filled.Search, "Buscar") { openSearch(context) }
        if (settings.showWifi) {
            val n = net
            PillSlot(onClick = { vm.system.openWifiSettings() }) {
                WifiIcon(
                    level = if (n == null || !n.connected) -1 else n.level, ethernet = n?.ethernet == true,
                    noInternet = n != null && n.connected && !n.internet, color = Color.White, modifier = Modifier.size(22.dp)
                )
            }
        }
        if (settings.showNotifications) {
            val count = notifs.size
            PillSlot(onClick = { vm.notificationsOpen.value = true }) {
                Icon(Icons.Filled.Notifications, "Notificações", tint = Color.White, modifier = Modifier.size(22.dp))
                if (count > 0) Text(
                    if (count > 9) "9+" else "$count", color = Color.White, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(ui.accent).padding(horizontal = 4.dp)
                )
            }
        }
        if (settings.showGear) PillIcon(Icons.Filled.Settings, "Configurações", onLong = { vm.open(Screen.SETTINGS) }) {
            vm.system.openSystemSettings()
        }
        PillIcon(Icons.Filled.Menu, "Menu") { vm.sidePanelOpen.value = true }
        val b = battery
        if (settings.showBattery && b != null && b.present) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                BatteryIcon(b.percent, b.charging, Color.White, Modifier.width(22.dp).height(14.dp))
                Text(" ${b.percent}%", color = Color.White, fontSize = 13.sp)
            }
        }
        if (settings.showClock) Clock()
        if (settings.showProfile) {
            FocusCard(Modifier.size(42.dp), corner = 21.dp, focusScale = 1.12f, glow = false, background = Color.Transparent,
                onClick = { vm.profileOpen.value = true }) { _ ->
                Avatar(settings.profileAvatar, settings.profileName, ui.accent, 42.dp)
            }
        }
    }
}

@Composable
private fun PillSlot(onLong: (() -> Unit)? = null, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    FocusCard(Modifier.size(40.dp), corner = 20.dp, focusScale = 1.15f, glow = false, background = Color.Transparent,
        onLongClick = onLong, onClick = onClick) { f ->
        Box(Modifier.fillMaxSize().background(if (f) LocalUi.current.accent else Color.Transparent), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun PillIcon(icon: ImageVector, desc: String, onLong: (() -> Unit)? = null, onClick: () -> Unit) =
    PillSlot(onLong = onLong, onClick = onClick) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp))
    }

@Composable
fun InfoStrip(info: MutableState<FocusInfo?>, start: Int) {
    val ui = LocalUi.current
    Box(Modifier.fillMaxWidth().heightIn(min = 8.dp).padding(start = start.dp, end = 40.dp, top = 6.dp)) {
        info.value?.let { i ->
            Column(Modifier.widthIn(max = 760.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(i.source.uppercase(), color = ui.accent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 10.dp))
                    Text(i.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val sub = listOfNotNull(i.subtitle, i.description).joinToString("  •  ")
                if (sub.isNotBlank()) Text(sub, color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun TitleBlock(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimalIconView(settings.titleAnimal, ui.accent, Modifier.size(54.dp))
        if (settings.titleAnimal != AnimalIcon.NONE) Spacer(Modifier.width(12.dp))
        if (settings.titleEmoji.isNotBlank()) Text(settings.titleEmoji, fontSize = 32.sp, modifier = Modifier.padding(end = 12.dp))
        Text(
            settings.title,
            style = MaterialTheme.typography.displaySmall.copy(
                brush = if (settings.titleColor != 0L) Brush.linearGradient(listOf(Color(settings.titleColor), Color(settings.titleColor)))
                else Brush.linearGradient(listOf(Color.White, ui.accent, Spectra.Lilac)),
                shadow = Shadow((if (settings.titleColor != 0L) Color(settings.titleColor) else ui.accent).copy(alpha = 0.6f), Offset(0f, 2f), 18f)
            ),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun Clock() {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1000L * (60 - (System.currentTimeMillis() / 1000 % 60)).coerceAtLeast(1L))
        }
    }
    val locale = Locale("pt", "BR")
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(SimpleDateFormat("HH:mm", locale).format(now), color = Color.White, fontSize = 26.sp, style = MaterialTheme.typography.headlineSmall)
        Text(SimpleDateFormat("EEE, d MMM", locale).format(now), color = Spectra.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
fun RowTitle(title: String, emoji: String, settings: LauncherSettings, logo: String? = null) {
    if (!settings.showSectionTitles) return
    Row(Modifier.padding(start = settings.startMargin.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (settings.showSectionIcons) {
            if (logo != null) {
                AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(10.dp))
            } else if (emoji.isNotBlank()) {
                Text(emoji, color = LocalUi.current.accent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
            }
        }
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun AppSectionHeader(row: HomeRowModel.ContentHeader, start: Int) {
    val ui = LocalUi.current
    val c = row.content
    Row(Modifier.padding(start = start.dp, top = 18.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIconSmall(row.app.packageName, 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(row.app.label, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(ui.accent))
        Spacer(Modifier.width(12.dp))
        val parts = buildList {
            if (c.continueItems.isNotEmpty()) add("${c.continueItems.size} continuar")
            if (c.recommended.isNotEmpty()) add("${c.recommended.size} recomendados")
            if (c.channels.isNotEmpty()) add("${c.channels.size} canais")
        }
        Text(parts.joinToString("  ·  "), color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PermissionCard(start: Int, onRequest: () -> Unit) {
    val ui = LocalUi.current
    Box(Modifier.padding(horizontal = start.dp, vertical = 10.dp)) {
        FocusCard(Modifier.fillMaxWidth(), corner = 18.dp, focusScale = 1.02f, onClick = onRequest) { focused ->
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(ui.accent.copy(alpha = if (focused) 0.55f else 0.3f), Color.Transparent)))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📺", fontSize = 30.sp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ativar Canais, Continuar assistindo e Recomendados", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Permita que o Spectra leia o conteúdo publicado pelos apps (SmartTube, Crunchyroll, Sintonia, YouTube…). Pressione OK.",
                        color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ util

fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

fun openSearch(context: Context) {
    val intents = listOf(Intent("android.search.action.GLOBAL_SEARCH"), Intent(Intent.ACTION_ASSIST), Intent(Intent.ACTION_WEB_SEARCH))
    for (i in intents) {
        val ok = runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        if (ok) return
    }
    toast(context, "Busca do sistema indisponível")
}

/** Modo ocioso: só o wallpaper e um relógio grande. Qualquer tecla volta. */
@Composable
fun IdleClock() {
    val now by produceState(Date()) {
        while (true) { value = Date(); delay(15_000) }
    }
    val locale = Locale("pt", "BR")
    Box(Modifier.fillMaxSize().padding(64.dp), contentAlignment = Alignment.BottomStart) {
        Column {
            Text(
                SimpleDateFormat("HH:mm", locale).format(now), color = Color.White, fontSize = 96.sp,
                style = MaterialTheme.typography.displaySmall.copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 4f), 24f))
            )
            Text(
                SimpleDateFormat("EEEE, d 'de' MMMM", locale).format(now).replaceFirstChar { it.uppercase() },
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
