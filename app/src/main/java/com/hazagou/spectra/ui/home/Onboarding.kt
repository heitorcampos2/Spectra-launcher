package com.hazagou.spectra.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazagou.spectra.MainViewModel
import com.hazagou.spectra.ui.components.PillButton
import com.hazagou.spectra.ui.components.SpectraDialog
import com.hazagou.spectra.ui.components.rememberAutoFocus
import com.hazagou.spectra.ui.settings.LauncherActions
import com.hazagou.spectra.ui.theme.LocalUi
import com.hazagou.spectra.ui.theme.Spectra

/**
 * Assistente da primeira abertura: mostra o que falta liberar e leva direto à tela certa do Android.
 * O status de cada passo se atualiza sozinho quando a pessoa volta das configurações.
 */
@Composable
fun OnboardingDialog(vm: MainViewModel, actions: LauncherActions, onFinish: () -> Unit) {
    val tick by vm.resumeTick.collectAsState() // recalcula ao voltar das configurações do Android
    val tv = vm.tvRepo
    @Suppress("UNUSED_VARIABLE") val t = tick

    val homeOk = actions.isDefaultLauncher() || actions.isHomeOverrideEnabled()
    val channelsOk = !tv.hasProvider || (tv.hasPermission && !tv.needsListings)
    val notifOk = vm.system.hasNotificationAccess()
    val remoteOk = vm.homeOverrideEnabled()
    val states = listOf(channelsOk, homeOk, notifOk, remoteOk)
    val done = states.count { it }
    val firstPending = states.indexOfFirst { !it }

    SpectraDialog(onFinish, width = 900.dp) {
        val first = rememberAutoFocus()
        fun focusFor(i: Int) = if (i == firstPending) Modifier.focusRequester(first) else Modifier
        Text("Bem-vindo ao Spectra 👋", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text("Vamos deixar tudo pronto. Toque em cada passo: o Spectra abre a tela certa do Android e, ao voltar, o passo fica marcado. ($done de 4 prontos)",
            color = Spectra.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Step(
                n = 1, ok = channelsOk, title = "Canais, Ver a seguir e Sugestões",
                text = if (!tv.hasProvider) "Este aparelho não tem canais do Android TV (celular/tablet) — pode pular."
                else "Libera os canais e o \"continuar assistindo\" do Sintonia, YouTube, Crunchyroll… São 2 permissões: " +
                    "\"Canais e programação\" e \"Listagens de TV\".",
                modifier = focusFor(0),
                button = "Liberar agora", onClick = { actions.requestEpgPermission() },
                secondary = if (tv.hasProvider && !channelsOk) "Abrir permissões do app" else null,
                onSecondary = { actions.openAppSettings() },
                hint = if (tv.hasProvider && !channelsOk) "Se a TV não perguntar: Configurações › Apps › Spectra Launcher › Permissões › ative tudo sobre TV/canais." else null,
            )
            Step(
                n = 2, ok = homeOk, title = "Abrir com o botão Home",
                text = if (actions.isGoogleTv()) "No Google TV, ative \"Spectra — Botão Home e controle\" em Acessibilidade."
                else "Define o Spectra como tela inicial da TV.",
                modifier = focusFor(1),
                button = if (actions.isGoogleTv()) "Abrir Acessibilidade" else "Definir como padrão",
                onClick = { if (actions.isGoogleTv()) actions.openAccessibilitySettings() else actions.requestDefaultLauncher() },
            )
            Step(
                n = 3, ok = notifOk, title = "Contador de notificações",
                text = "Mostra o sino com o número de notificações no topo. Ative \"Spectra Launcher\" na lista.",
                modifier = focusFor(2),
                button = "Abrir acesso a notificações", onClick = { vm.system.openNotificationAccess() },
            )
            Step(
                n = 4, ok = remoteOk, title = "Controle pelo celular em outros apps (opcional)",
                text = "Ative \"Spectra — Botão Home e controle\" em Acessibilidade para o celular funcionar também fora do Spectra.",
                modifier = focusFor(3),
                button = "Abrir Acessibilidade", onClick = { actions.openAccessibilitySettings() },
            )
            // Desempenho: começa em Equilibrado; TVs/TV box mais simples devem usar Máximo desempenho
            val st by vm.settings.collectAsState()
            val perf = st?.performance ?: com.hazagou.spectra.data.PerformanceMode.BALANCED
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x33F59E0B)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚡ Desempenho: ${perf.label}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("O Spectra vem em Equilibrado. Se a TV ou TV box ficar lenta (travando ao navegar), escolha Máximo desempenho. " +
                    "TVs potentes podem usar Máxima qualidade (4K). Dá para mudar depois em MENU ☰ › Desempenho.",
                    color = Color(0xFFFDE68A), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.hazagou.spectra.data.PerformanceMode.entries.forEach { m ->
                        PillButton(m.label, selected = m == perf, leading = if (m == perf) "✔" else null) { vm.launch { setPerformance(m) } }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            PillButton(if (done == 4) "Concluir" else "Continuar depois", leading = "✔", selected = done == 4,
                modifier = if (firstPending < 0) Modifier.focusRequester(first) else Modifier) { onFinish() }
            Text("Você pode abrir este assistente de novo em Personalizar › Launcher e permissões.",
                color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

@Composable
private fun Step(
    n: Int,
    ok: Boolean,
    title: String,
    text: String,
    button: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    hint: String? = null,
) {
    val accent = LocalUi.current.accent
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.05f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (ok) Color(0xFF22C55E) else accent.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Text(if (ok) "✔" else "$n", color = Color.White, fontSize = 20.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(text, color = Spectra.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (hint != null) Text(hint, color = Color(0xFFFDE68A), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (ok) Text("Pronto", color = Color(0xFF86EFAC), style = MaterialTheme.typography.titleSmall)
            else {
                PillButton(button, leading = "›", selected = true, modifier = modifier, onClick = onClick)
                if (secondary != null) PillButton(secondary, onClick = onSecondary)
            }
        }
    }
}
