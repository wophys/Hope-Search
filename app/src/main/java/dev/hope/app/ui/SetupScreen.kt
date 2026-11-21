package dev.hope.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hope.app.MainVm
import kotlin.math.roundToInt

private data class Preset(val name: String, val rps: Float, val threads: Int)

private val PRESETS = listOf(Preset("Safe", 1f, 1), Preset("Normal", 2f, 2), Preset("Fast", 5f, 4))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: MainVm) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val bot by vm.bot.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), modifier = Modifier.fillMaxSize()) {
        item { Header() }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TELEGRAM BOT", color = C.muted, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("RECOMMENDED", color = C.good, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
                Spacer(Modifier.height(10.dp))
                Field(st.botToken, { v -> vm.store.update { it.copy(botToken = v.trim()) } }, "Bot token", secret = true)
                Spacer(Modifier.height(12.dp))
                Field(st.chatId, { v -> vm.store.update { it.copy(chatId = v.trim()) } }, "Chat ID")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Test", Modifier.weight(1f)) { vm.botTest() }
                    Btn("Find chat", Modifier.weight(1f)) { vm.botFindChat() }
                    Btn("Ping", Modifier.weight(1f)) { vm.botPing() }
                }
                if (bot.text.isNotBlank()) Hint(bot.text, if (bot.ok) C.good else C.bad)
                Spacer(Modifier.height(8.dp))
                Tog("Auto-send hits", st.autoSend) { v -> vm.store.update { it.copy(autoSend = v) } }
            }
        }
        item {
            Section("Speed") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { p ->
                        Pick(p.name, st.rps == p.rps && st.threads == p.threads) {
                            vm.store.update { it.copy(rps = p.rps, threads = p.threads) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LabeledSlider("Requests per second", String.format("%.1f", st.rps), st.rps, 0.5f..10f, 18) { v ->
                    vm.store.update { it.copy(rps = ((v * 2).roundToInt() / 2f).coerceIn(0.5f, 10f)) }
                }
                LabeledSlider("Threads", "${st.threads}", st.threads.toFloat(), 1f..5f, 3) { v ->
                    vm.store.update { it.copy(threads = v.roundToInt().coerceIn(1, 5)) }
                }
                LabeledSlider("Timeout", "${st.timeoutSec}s", st.timeoutSec.toFloat(), 5f..30f, 4) { v ->
                    vm.store.update { it.copy(timeoutSec = (v / 5).roundToInt().times(5).coerceIn(5, 30)) }
                }
            }
        }
        item {
            Section("About") {
                Text("HOPE 1.0.0", color = C.text, fontFamily = Mono, fontSize = 14.sp)
                Hint("All requests go from this device.")
            }
        }
    }
}
