package dev.hope.app.data

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

object Net {
    fun client(timeoutSec: Int): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .callTimeout(timeoutSec * 2L, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

class RateLimited(val waitMs: Long) : Exception("Rate limited")

data class FragResult(val status: Frag, val price: Double?)

/** Минимальный интервал между стартами запросов; общий для всех потоков. */
class Gate(private val intervalMs: Long) {
    private val mutex = Mutex()
    private var next = 0L

    suspend fun acquire() {
        val wait = mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val start = maxOf(now, next)
            next = start + intervalMs
            start - now
        }
        if (wait > 0) delay(wait)
    }

    suspend fun penalize(ms: Long) = mutex.withLock {
        next = maxOf(next, SystemClock.elapsedRealtime() + ms)
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { c ->
    c.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (c.isActive) c.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            c.resume(response)
        }
    })
}

private data class Page(val path: String, val body: String)

class Checker(private val client: OkHttpClient) {

    private suspend fun get(url: String): Page {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(req).await().use { r ->
            if (r.code == 429) {
                val wait = (r.header("Retry-After")?.toLongOrNull() ?: 0L) * 1000L
                throw RateLimited(wait)
            }
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = withContext(Dispatchers.IO) { r.body?.string().orEmpty() }
            return Page(r.request.url.encodedPath, body)
        }
    }

    /**
     * Страница t.me/<name> у свободного имени имеет og:title "Telegram: Contact @name".
     * У занятого там имя аккаунта/канала. Свободное в t.me не гарантирует,
     * что имя можно занять (бывают зарезервированные), для этого есть проверка Fragment.
     */
    suspend fun tme(name: String): Tme {
        val html = get("https://t.me/$name").body
        val title = Regex("<meta property=\"og:title\" content=\"([^\"]*)\"")
            .find(html)?.groupValues?.get(1) ?: return Tme.ERROR
        return if (title.trim().equals("Telegram: Contact @$name", ignoreCase = true)) Tme.FREE else Tme.TAKEN
    }

    suspend fun fragment(name: String): FragResult {
        val p = get("https://fragment.com/username/$name")
        // для неизвестных имён Fragment редиректит на поиск
        if (!p.path.startsWith("/username/")) return FragResult(Frag.NOT_LISTED, null)
        val status = Regex("tm-section-header-status[^\"]*\"[^>]*>\\s*([^<]+?)\\s*<")
            .find(p.body)?.groupValues?.get(1)?.lowercase()
        val price = Regex("icon-ton[^>]*>\\s*([0-9][0-9,.]*)\\s*<")
            .find(p.body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val st = when {
            status == null -> Frag.NOT_LISTED
            "sold" in status -> Frag.SOLD
            "unavailable" in status || "taken" in status -> Frag.TAKEN
            "available" in status || "auction" in status || "sale" in status || "bid" in status -> Frag.LISTED
            else -> Frag.NOT_LISTED
        }
        return FragResult(st, if (st == Frag.LISTED || st == Frag.SOLD) price else null)
    }
}

class Bot(private val client: OkHttpClient) {

    private suspend fun call(token: String, method: String, form: Map<String, String> = emptyMap()): JSONObject {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url("https://api.telegram.org/bot$token/$method").post(body).build()
        val j = withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { r ->
                val s = r.body?.string().orEmpty()
                if (s.isBlank()) JSONObject().put("ok", false).put("description", "HTTP ${r.code}") else JSONObject(s)
            }
        }
        if (!j.optBoolean("ok")) throw IOException(j.optString("description", "Request failed"))
        return j
    }

    suspend fun me(token: String): Result<String> = runCatching {
        "@" + call(token, "getMe").getJSONObject("result").optString("username")
    }

    suspend fun send(token: String, chat: String, text: String): Result<Unit> = runCatching {
        call(token, "sendMessage", mapOf("chat_id" to chat, "text" to text, "disable_web_page_preview" to "true"))
        Unit
    }

    /** Длинный текст режется по строкам на сообщения до ~3800 символов. */
    suspend fun sendAll(token: String, chat: String, text: String): Result<Unit> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        for (line in text.lines()) {
            if (cur.length + line.length + 1 > 3800 && cur.isNotEmpty()) {
                parts += cur.toString(); cur.clear()
            }
            if (cur.isNotEmpty()) cur.append('\n')
            cur.append(line)
        }
        if (cur.isNotEmpty()) parts += cur.toString()
        for (p in parts) {
            val r = send(token, chat, p)
            if (r.isFailure) return r
        }
        return Result.success(Unit)
    }

    /** Берёт chat id из последних апдейтов: сначала нужно написать боту /start. */
    suspend fun findChat(token: String): Result<String> = runCatching {
        val arr = call(token, "getUpdates").getJSONArray("result")
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            val m = o.optJSONObject("message") ?: o.optJSONObject("channel_post")
                ?: o.optJSONObject("my_chat_member") ?: continue
            val chat = m.optJSONObject("chat") ?: continue
            if (chat.has("id")) return@runCatching chat.getLong("id").toString()
        }
        throw IOException("Send /start to the bot first")
    }
}
