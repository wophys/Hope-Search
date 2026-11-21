package dev.hope.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hope.app.MainVm
import dev.hope.app.data.tonText

@Composable
private fun Tile(label: String, value: String, modifier: Modifier, color: Color = C.text) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.border(1.dp, C.line, shape).background(C.surface, shape).padding(14.dp)
    ) {
        Text(label.uppercase(), color = C.muted, fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = color, fontFamily = Mono, fontSize = 22.sp)
    }
}

@Composable
private fun TileRow(a: Triple<String, String, Color>, b: Triple<String, String, Color>) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(a.first, a.second, Modifier.weight(1f), a.third)
        Tile(b.first, b.second, Modifier.weight(1f), b.third)
    }
}

@Composable
private fun Bar(label: String, value: String, frac: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.text, fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f).height(8.dp).background(C.surface2, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).height(8.dp).background(C.accent, RoundedCornerShape(2.dp)))
        }
        Text(value, color = C.muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun StatsScreen(vm: MainVm) {
    val s by vm.stats.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), modifier = Modifier.fillMaxSize()) {
        item { Header() }
        if (s.total == 0) {
            item { Hint("No data yet") }
            return@LazyColumn
        }
        item {
            Spacer(Modifier.height(22.dp))
            TileRow(Triple("Total", "${s.total}", C.text), Triple("Checked", "${s.checked}", C.text))
            TileRow(Triple("Free", "${s.free}", C.good), Triple("Hit rate", "${s.hitRate}%", C.good))
            TileRow(Triple("Taken", "${s.taken}", C.muted), Triple("Invalid", "${s.invalid}", C.bad))
            TileRow(Triple("Listed", "${s.listed}", C.accent), Triple("Sold", "${s.sold}", C.warn))
            TileRow(Triple("Favorites", "${s.favorites}", C.warn), Triple("Avg price", tonText(s.avgPrice) ?: "—", C.text))
        }
        if (s.byLength.isNotEmpty()) {
            item {
                Section("By length") {
                    val max = s.byLength.maxOf { it.second }.toFloat()
                    s.byLength.forEach { (len, n) -> Bar("$len", "$n", n / max) }
                }
            }
        }
        if (s.byAlgo.isNotEmpty()) {
            item {
                Section("By algorithm") {
                    val max = s.byAlgo.maxOf { it.third }.toFloat()
                    s.byAlgo.forEach { (name, free, total) ->
                        Bar(name.lowercase(), "$free/$total", total / max)
                    }
                }
            }
        }
    }
}
