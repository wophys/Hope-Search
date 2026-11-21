package dev.hope.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val gen: GenParams = GenParams(),
    val rps: Float = 2f,
    val threads: Int = 2,
    val timeoutSec: Int = 15,
    val botToken: String = "",
    val chatId: String = "",
    val autoSend: Boolean = false
)

class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("hope", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    @Synchronized
    fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_state.value)
        _state.value = n
        save(n)
    }

    private inline fun <reified T : Enum<T>> enumOf(s: String?, d: T): T =
        enumValues<T>().firstOrNull { it.name == s } ?: d

    private fun load(): AppSettings {
        val d = GenParams()
        val g = GenParams(
            algo = enumOf(p.getString("algo", null), d.algo),
            style = enumOf(p.getString("style", null), d.style),
            length = p.getInt("length", d.length).coerceIn(5, 32),
            count = p.getInt("count", d.count).coerceIn(1, 200),
            prefix = p.getString("prefix", "").orEmpty(),
            suffix = p.getString("suffix", "").orEmpty(),
            exclude = p.getString("exclude", "").orEmpty(),
            noDigits = p.getBoolean("noDigits", d.noDigits),
            noUnderscore = p.getBoolean("noUnderscore", d.noUnderscore),
            noDouble = p.getBoolean("noDouble", d.noDouble),
            skipChecked = p.getBoolean("skipChecked", d.skipChecked),
            checkTme = p.getBoolean("checkTme", d.checkTme),
            checkFragment = p.getBoolean("checkFragment", d.checkFragment)
        )
        return AppSettings(
            gen = g,
            rps = p.getFloat("rps", 2f).coerceIn(0.5f, 10f),
            threads = p.getInt("threads", 2).coerceIn(1, 5),
            timeoutSec = p.getInt("timeout", 15).coerceIn(5, 30),
            botToken = p.getString("botToken", "").orEmpty(),
            chatId = p.getString("chatId", "").orEmpty(),
            autoSend = p.getBoolean("autoSend", false)
        )
    }

    private fun save(s: AppSettings) {
        val g = s.gen
        p.edit()
            .putString("algo", g.algo.name).putString("style", g.style.name)
            .putInt("length", g.length).putInt("count", g.count)
            .putString("prefix", g.prefix).putString("suffix", g.suffix).putString("exclude", g.exclude)
            .putBoolean("noDigits", g.noDigits).putBoolean("noUnderscore", g.noUnderscore)
            .putBoolean("noDouble", g.noDouble).putBoolean("skipChecked", g.skipChecked)
            .putBoolean("checkTme", g.checkTme).putBoolean("checkFragment", g.checkFragment)
            .putFloat("rps", s.rps).putInt("threads", s.threads).putInt("timeout", s.timeoutSec)
            .putString("botToken", s.botToken).putString("chatId", s.chatId)
            .putBoolean("autoSend", s.autoSend)
            .apply()
    }
}
