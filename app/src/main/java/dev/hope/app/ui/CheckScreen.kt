package dev.hope.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hope.app.MainVm
import dev.hope.app.data.*

@Composable
fun CheckScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val st by vm.settings.collectAsStateWithLifecycle()
    val run by vm.chk.state.collectAsStateWithLifecycle()
    val list by vm.chkView.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val g = st.gen

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), modifier = Modifier.fillMaxSize()) {
        item { Header() }
        item {
            Section("Usernames") {
                Field(
                    input, { input = it }, "One per line", singleLine = false, minLines = 5,
                    keyboard = KeyboardType.Ascii
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Btn("Paste", Modifier.weight(1f)) { input = pasteText(ctx) }
                    Btn("Clear", Modifier.weight(1f)) { input = "" }
                }
            }
        }
        item {
            Section("Check") {
                Tog("t.me", g.checkTme) { v -> vm.store.update { it.copy(gen = it.gen.copy(checkTme = v)) } }
                Tog("Fragment", g.checkFragment) { v -> vm.store.update { it.copy(gen = it.gen.copy(checkFragment = v)) } }
            }
        }
        item {
            Spacer(Modifier.height(20.dp))
            if (run.running) Btn("Stop", Modifier.fillMaxWidth(), primary = true) { vm.stop() }
            else Btn("Check", Modifier.fillMaxWidth(), primary = true) { vm.startCheck(input) }
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
