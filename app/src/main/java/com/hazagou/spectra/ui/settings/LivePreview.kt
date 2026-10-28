package com.hazagou.spectra.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hazagou.spectra.data.AppInfo
import com.hazagou.spectra.data.CardStyle
import com.hazagou.spectra.data.LauncherSettings
import com.hazagou.spectra.ui.components.Avatar
import com.hazagou.spectra.ui.components.DefaultArtBackground
import com.hazagou.spectra.ui.components.WifiIcon
import com.hazagou.spectra.ui.home.TitleBlock
import com.hazagou.spectra.ui.home.appCardHeight
import com.hazagou.spectra.ui.home.appCardWidth
import com.hazagou.spectra.ui.home.rememberBanner
import com.hazagou.spectra.ui.home.rememberIcon
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra
import java.io.File

/**
 * Prévia AO VIVO da tela inicial, inclinada em 3D. É desenhada em tamanho real (960×540 dp)
 * e reduzida, então reflete exatamente título, cores, cartões, margens, espaçamentos e opacidades.
 */
@Composable
fun LivePreview(settings: LauncherSettings, apps: List<AppInfo>, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                rotationY = 16f
                cameraDistance = 14f * density
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, ui.accent, RoundedCornerShape(16.dp))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val f = maxWidth / 960.dp
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(960.dp, 540.dp)
                    .graphicsLayer { scaleX = f; scaleY = f; transformOrigin = TransformOrigin(0f, 0f) }
            ) { PreviewContent(settings, apps) }
        }
    }
}

@Composable
private fun PreviewContent(s: LauncherSettings, apps: List<AppInfo>) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val file = s.wallpaperPath?.takeIf { s.bgMode == com.hazagou.spectra.data.BgMode.WALLPAPER }?.let { File(it) }?.takeIf { it.exists() }
    Box(Modifier.fillMaxSize().background(Spectra.Night)) {
        if (file != null) AsyncImage(
            ImageRequest.Builder(ctx).data(file).size(640, 360).build(), null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        ) else when (s.bgMode) {
            com.hazagou.spectra.data.BgMode.SOLID -> Box(Modifier.fillMaxSize().background(Color(s.bgColor1)))
            com.hazagou.spectra.data.BgMode.GRADIENT -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(s.bgColor1), Color(s.bgColor2)))))
            else -> DefaultArtBackground(Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = s.dim * 0.5f), Color.Black.copy(alpha = (s.dim * 1.4f).coerceAtMost(0.9f))))))

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = s.startMargin.dp, end = 40.dp, top = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s.showTitle) TitleBlock(s, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.32f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (s.showWifi) WifiIcon(3, false, false, Color.White, Modifier.size(22.dp))
                    if (s.showNotifications) Text("🔔", fontSize = 18.sp)
                    if (s.showGear) Text("⚙", color = Color.White, fontSize = 20.sp)
                    if (s.showClock) Text("12:00", color = Color.White, fontSize = 24.sp)
                    if (s.showProfile) Avatar(s.profileAvatar, s.profileName, ui.accent, 38.dp)
                }
            }
            Spacer(Modifier.height(30.dp))
            val tvApps = apps.filter { it.isTvApp }.ifEmpty { apps }.take(7)
            val other = apps.filterNot { it in tvApps }.take(9)
            PreviewRow(s, "TV", "📺", tvApps, focused = true)
            PreviewRow(s, "Aplicativos móveis", "📱", other.ifEmpty { tvApps }, focused = false, style = CardStyle.ICON)
        }
    }
}

@Composable
private fun PreviewRow(s: LauncherSettings, title: String, icon: String, apps: List<AppInfo>, focused: Boolean, style: CardStyle = s.cardStyle) {
    val ui = LocalUi.current
    Column(Modifier.padding(top = s.rowSpacing.dp).graphicsLayer { alpha = if (focused) 1f else s.unselectedRowAlpha }) {
        if (s.showSectionTitles) Row(Modifier.padding(start = s.startMargin.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.showSectionIcons) Text(icon, color = ui.accent, fontSize = 17.sp, modifier = Modifier.padding(end = 10.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Row(
            Modifier.padding(start = s.startMargin.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(s.cardSpacing.dp)
        ) {
            apps.forEachIndexed { i, a ->
                val isFocus = focused && i == 0
                val w = appCardWidth(style, s.cardSize)
                val h = appCardHeight(style, s.cardSize)
                val shape = RoundedCornerShape(if (style == CardStyle.ICON) w / 2 else s.corner.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.width(w).height(h)
                            .graphicsLayer { val sc = if (isFocus) ui.focusScale else 1f; scaleX = sc; scaleY = sc }
                            .clip(shape)
                            .background(ui.surfaceHigh)
                            .then(if (isFocus) Modifier.border(3.dp, ui.accent, shape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        val banner = if (style == CardStyle.BANNER && a.isTvApp) rememberBanner(a) else null
                        if (banner != null) Image(banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else rememberIcon(a.packageName)?.let { Image(it, null, Modifier.size(minOf(w, h) * 0.6f)) }
                    }
                    if (isFocus && s.showLabels) Text(a.label, color = Color.White, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).width(w))
                }
            }
        }
    }
}
