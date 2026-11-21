package dev.hope.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hope.app.MainVm
import dev.hope.app.R
import dev.hope.app.data.*

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("HOPE", text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}

fun pasteText(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
}

fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(ctx, "Can't open link", Toast.LENGTH_SHORT).show() }
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { ctx.startActivity(Intent.createChooser(i, null)) }
}

private fun tmeTag(e: UsernameEntity, pending: Boolean): Pair<String, Color>? = when (e.tme) {
    Tme.FREE.name -> "FREE" to C.good
    Tme.TAKEN.name -> "TAKEN" to C.muted
    Tme.INVALID.name -> "INVALID" to C.bad
    Tme.ERROR.name -> "T.ME ERROR" to C.warn
    else -> if (pending) "..." to C.muted else null
}

private fun fragTag(e: UsernameEntity): Pair<String, Color>? {
    val price = tonText(e.priceTon)?.let { " $it" }.orEmpty()
    return when (e.fragment) {
        Frag.LISTED.name -> "F · LISTED$price" to C.accent
        Frag.SOLD.name -> "F · SOLD$price" to C.warn
        Frag.TAKEN.name -> "F · TAKEN" to C.muted
        Frag.NOT_LISTED.name -> "F · NOT LISTED" to C.muted
        Frag.ERROR.name -> "F · ERROR" to C.warn
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultRow(
    e: UsernameEntity,
    pending: Boolean,
    onFav: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onFragment: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text("@${e.name}", color = C.text, fontFamily = Mono, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tmeTag(e, pending)?.let { Tag(it.first, it.second) }
                    fragTag(e)?.let { Tag(it.first, it.second, onFragment) }
                }
            }
            IconBtn(R.drawable.ic_copy, onCopy)
            IconBtn(
                if (e.favorite) R.drawable.ic_star_fill else R.drawable.ic_star, onFav,
                tint = if (e.favorite) C.warn else C.muted
            )
            if (onDelete != null) IconBtn(R.drawable.ic_trash, onDelete)
        }
        HorizontalDivider(color = C.line)
    }
}

fun LazyListScope.resultItems(
    list: List<UsernameEntity>,
    running: Boolean,
    vm: MainVm,
    deletable: Boolean = false
) {
    items(list, key = { it.name }) { e ->
        val ctx = LocalContext.current
        ResultRow(
            e = e,
            pending = running && e.checkedAt == 0L,
            onFav = { vm.toggleFav(e) },
            onCopy = { copyText(ctx, "@${e.name}") },
            onOpen = { openUrl(ctx, "https://t.me/${e.name}") },
            onFragment = { openUrl(ctx, "https://fragment.com/username/${e.name}") },
            onDelete = if (deletable) ({ vm.delete(e) }) else null
        )
    }
}
