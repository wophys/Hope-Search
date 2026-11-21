package dev.hope.app

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.hope.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HopeTheme { App() } }
    }
}

private val TABS = listOf(
    "Gen" to R.drawable.ic_gen,
    "Check" to R.drawable.ic_check,
    "Log" to R.drawable.ic_log,
    "Stats" to R.drawable.ic_stats,
    "Setup" to R.drawable.ic_setup
)

@Composable
fun App(vm: MainVm = viewModel()) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.toast.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }
    // экран не гаснет, пока идёт проверка
    DisposableEffect(busy) {
        val w = (ctx as? Activity)?.window
        if (busy) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Scaffold(
        containerColor = C.bg,
        bottomBar = {
            Column {
                HorizontalDivider(color = C.line)
                NavigationBar(containerColor = C.surface, tonalElevation = 0.dp) {
                    TABS.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(painterResource(icon), contentDescription = null) },
                            label = { Text(label, fontFamily = Mono, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = C.accent, selectedTextColor = C.accent,
                                unselectedIconColor = C.muted, unselectedTextColor = C.muted,
                                indicatorColor = C.surface3
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> GenerateScreen(vm)
                1 -> CheckScreen(vm)
                2 -> HistoryScreen(vm)
                3 -> StatsScreen(vm)
                else -> SetupScreen(vm)
            }
        }
    }
}
