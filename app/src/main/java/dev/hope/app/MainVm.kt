package dev.hope.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hope.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException

data class RunState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val note: String = ""
)

class Session {
    val names = MutableStateFlow<List<String>>(emptyList())
    val state = MutableStateFlow(RunState())
}

data class BotStatus(val text: String = "", val ok: Boolean = true)

class MainVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val store = SettingsStore(app)
    val settings: StateFlow<AppSettings> = store.state

    val rows: StateFlow<List<UsernameEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val gen = Session()
    val chk = Session()
    val genView = view(gen)
    val chkView = view(chk)
    val stats: StateFlow<Stats> =
        rows.map { computeStats(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, Stats())

    val busy = MutableStateFlow(false)
    val toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val bot = MutableStateFlow(BotStatus())

    private var job: Job? = null
    private val botApi by lazy { Bot(Net.client(15)) }

    private fun view(s: Session): StateFlow<List<UsernameEntity>> =
        combine(s.names, rows) { names, all ->
            val m = all.associateBy { it.name }
            names.map { m[it] ?: UsernameEntity(name = it) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- запуск ----------

    private fun launchRun(s: Session, block: suspend CoroutineScope.() -> Unit) {
        if (busy.value) return
        busy.value = true
        s.state.value = RunState(running = true)
        job = viewModelScope.launch(Dispatchers.Default) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                s.state.update { it.copy(note = "Error: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                s.state.update { it.copy(running = false) }
                busy.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    /** check=false: только сгенерировать и сохранить без сети. */
    fun startGen(check: Boolean) {
        val st = settings.value
        launchRun(gen) {
            val known = if (st.gen.skipChecked) dao.allNames().toSet() else emptySet()
            val g = Generator.generate(st.gen, known)
            if (g.items.isEmpty()) {
                gen.names.value = emptyList()
                gen.state.update { it.copy(note = g.error ?: "Nothing generated") }
                return@launchRun
            }
            gen.names.value = g.items.map { it.name }
            val net = check && (st.gen.checkTme || st.gen.checkFragment)
            if (check && !net) gen.state.update { it.copy(note = "Enable t.me or Fragment check") }
            process(gen, g.items, net, st)
        }
    }

    fun startCheck(raw: String) {
        val st = settings.value
        if (!st.gen.checkTme && !st.gen.checkFragment) {
            toast.tryEmit("Enable t.me or Fragment"); return
        }
        val names = raw.split(Regex("[\\s,;]+")).map { Generator.normalize(it) }
            .filter { it.isNotEmpty() }.distinct().take(500)
        if (names.isEmpty()) {
            toast.tryEmit("Nothing to check"); return
        }
        chk.names.value = names
        launchRun(chk) { process(chk, names.map { Item(it, "manual") }, true, st) }
    }

    private suspend fun process(s: Session, items: List<Item>, net: Boolean, st: AppSettings) {
        s.state.update { it.copy(total = items.size, done = 0) }
        if (!net) {
            items.forEach { dao.insertIgnore(UsernameEntity(name = it.name, algo = it.algo)) }
            s.state.update { it.copy(done = items.size) }
            return
        }
        val interval = (1000f / st.rps).toLong().coerceAtLeast(50L)
        val checker = Checker(Net.client(st.timeoutSec))
        val tmeGate = Gate(interval)
        val fragGate = Gate(interval)
        val sem = Semaphore(st.threads)
        coroutineScope {
            for (item in items) {
                launch {
                    sem.withPermit {
                        save(checkOne(item, st.gen, checker, tmeGate, fragGate))
                        s.state.update { it.copy(done = it.done + 1) }
                    }
                }
            }
        }
        if (st.autoSend && st.botToken.isNotBlank() && st.chatId.isNotBlank()) {
            val hits = items.mapNotNull { dao.get(it.name) }.filter { it.isHit }
            if (hits.isNotEmpty()) {
                val r = botApi.sendAll(st.botToken.trim(), st.chatId.trim(), Export.botText(hits))
                toast.tryEmit(if (r.isSuccess) "Sent ${hits.size} to bot" else "Bot: ${r.exceptionOrNull()?.message}")
            }
        }
    }

    private suspend fun save(e: UsernameEntity) {
        if (dao.insertIgnore(e) == -1L) dao.updateCheck(e.name, e.tme, e.fragment, e.priceTon, e.checkedAt)
    }

    private suspend fun checkOne(item: Item, g: GenParams, c: Checker, tg: Gate, fg: Gate): UsernameEntity {
        var tme = Tme.NONE
        var frag = Frag.NONE
        var price: Double? = null
        if (!Generator.isValid(item.name)) {
            tme = Tme.INVALID
        } else {
            if (g.checkTme) tme = retry(tg, Tme.ERROR) { c.tme(item.name) }
            if (g.checkFragment) {
                val f = retry(fg, FragResult(Frag.ERROR, null)) { c.fragment(item.name) }
                frag = f.status; price = f.price
            }
        }
        return UsernameEntity(item.name, tme.name, frag.name, price, false, item.algo, System.currentTimeMillis())
    }

    private suspend fun <T> retry(gate: Gate, fail: T, block: suspend () -> T): T {
        for (attempt in 1..3) {
            gate.acquire()
            try {
                return block()
            } catch (e: RateLimited) {
                gate.penalize(maxOf(e.waitMs, 4000L * attempt))
            } catch (e: IOException) {
                delay(700L * attempt)
            }
        }
        return fail
    }

    // ---------- история ----------

    fun toggleFav(e: UsernameEntity) {
        viewModelScope.launch { dao.setFavorite(e.name, !e.favorite) }
    }

    fun delete(e: UsernameEntity) {
        viewModelScope.launch { dao.delete(e.name) }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clearNonFavorites() }
    }

    fun writeTo(uri: Uri, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?.use { it.write(text.toByteArray()) } ?: error("no stream")
            }.isSuccess
            toast.tryEmit(if (ok) "Saved" else "Save failed")
        }
    }

    // ---------- бот ----------

    fun sendToBot(list: List<UsernameEntity>) {
        val st = settings.value
        if (st.botToken.isBlank() || st.chatId.isBlank()) {
            toast.tryEmit("Set up bot first"); return
        }
        val hits = list.filter { it.isHit }
        if (hits.isEmpty()) {
            toast.tryEmit("No hits to send"); return
        }
        viewModelScope.launch {
            val r = botApi.sendAll(st.botToken.trim(), st.chatId.trim(), Export.botText(hits))
            toast.emit(if (r.isSuccess) "Sent ${hits.size}" else r.exceptionOrNull()?.message ?: "Send failed")
        }
    }

    fun botTest() {
        val t = settings.value.botToken.trim()
        if (t.isEmpty()) {
            bot.value = BotStatus("Enter token", false); return
        }
        bot.value = BotStatus("Checking…")
        viewModelScope.launch {
            botApi.me(t)
                .onSuccess { bot.value = BotStatus("OK · $it") }
                .onFailure { bot.value = BotStatus(it.message ?: "Failed", false) }
        }
    }

    fun botFindChat() {
        val t = settings.value.botToken.trim()
        if (t.isEmpty()) {
            bot.value = BotStatus("Enter token", false); return
        }
        bot.value = BotStatus("Searching…")
        viewModelScope.launch {
            botApi.findChat(t)
                .onSuccess { id ->
                    store.update { it.copy(chatId = id) }
                    bot.value = BotStatus("Chat $id")
                }
                .onFailure { bot.value = BotStatus(it.message ?: "Failed", false) }
        }
    }

    fun botPing() {
        val st = settings.value
        if (st.botToken.isBlank() || st.chatId.isBlank()) {
            bot.value = BotStatus("Enter token and chat", false); return
        }
        bot.value = BotStatus("Sending…")
        viewModelScope.launch {
            botApi.send(st.botToken.trim(), st.chatId.trim(), "HOPE · connected")
                .onSuccess { bot.value = BotStatus("Sent") }
                .onFailure { bot.value = BotStatus(it.message ?: "Failed", false) }
        }
    }
}
