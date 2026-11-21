package dev.hope.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hope.app.MainVm
import dev.hope.app.data.*
import kotlin.math.roundToInt

private fun cleanWord(s: String) = s.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(12)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val st by vm.settings.collectAsStateWithLifecycle()
    val run by vm.gen.state.collectAsStateWithLifecycle()
    val list by vm.genView.collectAsStateWithLifecycle()
    val g = st.gen
    fun upd(f: (GenParams) -> GenParams) = vm.store.update { it.copy(gen = f(it.gen)) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), modifier = Modifier.fillMaxSize()) {
        item { Header() }
        item {
            Section("Algorithm") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Algo.values().forEach { a -> Pick(a.label, g.algo == a) { upd { p -> p.copy(algo = a) } } }
                }
            }
        }
        item {
            Section("Style") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Style.values().forEach { s -> Pick(s.label, g.style == s) { upd { p -> p.copy(style = s) } } }
                }
            }
        }
        item {
            Section("Size") {
                LabeledSlider("Length", "${g.length}", g.length.toFloat(), 5f..32f, 26) { v ->
                    upd { p -> p.copy(length = v.roundToInt()) }
                }
                LabeledSlider("Count", "${g.count}", g.count.toFloat(), 1f..200f, 0) { v ->
                    upd { p -> p.copy(count = v.roundToInt().coerceIn(1, 200)) }
                }
            }
        }
        item {
            Section("Pattern") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(g.prefix, { v -> upd { p -> p.copy(prefix = cleanWord(v)) } }, "Prefix", Modifier.weight(1f))
                    Field(g.suffix, { v -> upd { p -> p.copy(suffix = cleanWord(v)) } }, "Suffix", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Field(g.exclude, { v -> upd { p -> p.copy(exclude = cleanWord(v).toList().distinct().joinToString("")) } }, "Exclude chars")
            }
        }
        item {
            Section("Filters") {
                Tog("No digits", g.noDigits) { v -> upd { p -> p.copy(noDigits = v) } }
                Tog("No underscore", g.noUnderscore) { v -> upd { p -> p.copy(noUnderscore = v) } }
                Tog("No double letters", g.noDouble) { v -> upd { p -> p.copy(noDouble = v) } }
                Tog("Skip checked", g.skipChecked) { v -> upd { p -> p.copy(skipChecked = v) } }
            }
        }
        item {
            Section("Check") {
                Tog("t.me", g.checkTme) { v -> upd { p -> p.copy(checkTme = v) } }
                Tog("Fragment", g.checkFragment) { v -> upd { p -> p.copy(checkFragment = v) } }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (run.running) {
                    Btn("Stop", Modifier.weight(1f), primary = true) { vm.stop() }
                } else {
                    Btn("Run", Modifier.weight(1f), primary = true) { vm.startGen(true) }
                    Btn("Generate", Modifier.weight(1f)) { vm.startGen(false) }
                }
            }
            if (run.total > 0) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { run.done.toFloat() / run.total },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = C.accent, trackColor = C.line, drawStopIndicator = {}
                )
                Hint("${run.done}/${run.total} · ${list.count { it.tme == Tme.FREE.name }} free")
            }
            if (run.note.isNotBlank()) Hint(run.note, C.warn)
        }
        if (list.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("RESULTS · ${list.size}", color = C.muted, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Btn("Copy", Modifier.width(76.dp)) { copyText(ctx, Export.txt(list)) }
                    Spacer(Modifier.width(8.dp))
                    Btn("Bot", Modifier.width(76.dp)) { vm.sendToBot(list) }
                }
                Spacer(Modifier.height(6.dp))
            }
            resultItems(list, run.running, vm)
        }
    }
}
