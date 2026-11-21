package dev.hope.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hope.app.MainVm
import dev.hope.app.data.*

private val FILTERS = listOf("All", "Free", "Listed", "Fav")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val rows by vm.rows.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var pendingCsv by remember { mutableStateOf("") }

    val list = remember(rows, filter, query) {
        val q = query.trim().lowercase().removePrefix("@")
        rows.filter {
            when (filter) {
                "Free" -> it.tme == Tme.FREE.name
                "Listed" -> it.fragment == Frag.LISTED.name
                "Fav" -> it.favorite
                else -> true
            } && (q.isEmpty() || q in it.name)
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.writeTo(uri, pendingCsv)
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), modifier = Modifier.fillMaxSize()) {
        item { Header() }
        item {
            Section("Search") {
                Field(query, { query = it }, "Username")
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FILTERS.forEach { f -> Pick(f, filter == f) { filter = f } }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Share", Modifier.weight(1f), enabled = list.isNotEmpty()) { shareText(ctx, Export.txt(list)) }
                Btn("CSV", Modifier.weight(1f), enabled = list.isNotEmpty()) {
                    pendingCsv = Export.csv(list)
                    saver.launch("hope-usernames.csv")
                }
                Btn("Bot", Modifier.weight(1f), enabled = list.isNotEmpty()) { vm.sendToBot(list) }
                Btn("Clear", Modifier.weight(1f), enabled = rows.isNotEmpty() && !busy) { confirm = true }
            }
            Spacer(Modifier.height(20.dp))
            Text("${list.size} SAVED", color = C.muted, fontFamily = Mono, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
        }
        if (list.isEmpty()) {
            item { Hint("Nothing here yet") }
        } else {
            resultItems(list, running = false, vm = vm, deletable = true)
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = C.surface,
            title = { Text("Clear history", color = C.text, fontFamily = Mono, fontSize = 16.sp) },
            text = { Text("Favorites stay.", color = C.muted) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirm = false }) { Text("Clear", color = C.bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = C.muted) }
            }
        )
    }
}
