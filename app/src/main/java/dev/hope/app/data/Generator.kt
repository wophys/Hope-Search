package dev.hope.app.data

import kotlin.random.Random

data class GenParams(
    val algo: Algo = Algo.PRONOUNCEABLE,
    val style: Style = Style.PLAIN,
    val length: Int = 8,
    val count: Int = 20,
    val prefix: String = "",
    val suffix: String = "",
    val exclude: String = "",
    val noDigits: Boolean = false,
    val noUnderscore: Boolean = false,
    val noDouble: Boolean = false,
    val skipChecked: Boolean = true,
    val checkTme: Boolean = true,
    val checkFragment: Boolean = true
)

data class Item(val name: String, val algo: String)

data class GenResult(val items: List<Item>, val error: String? = null)

object Generator {
    private val NAME = Regex("^[a-z][a-z0-9_]{4,31}$")
    private val DOUBLE = Regex("(.)\\1")
    private const val DIGITS = "0123456789"

    fun isValid(n: String): Boolean = NAME.matches(n) && !n.contains("__") && !n.endsWith("_")

    /** "@Name", "t.me/Name", "https://t.me/Name?x" -> "name" */
    fun normalize(raw: String): String =
        raw.trim().lowercase().substringAfterLast('/').removePrefix("@")
            .takeWhile { it in 'a'..'z' || it in '0'..'9' || it == '_' }

    private fun sets(algo: Algo, ex: Set<Char>): Pair<String, String> {
        val (c, v) = when (algo) {
            Algo.PRONOUNCEABLE -> "bcdfghklmnprstvz" to "aeiou"
            Algo.SOFT -> "lmnrsvwyfh" to "aoueiy"
            Algo.SHARP -> "ktxzqdgpbc" to "aeiy"
            Algo.MIXED -> "" to ""
        }
        return c.filter { it !in ex } to v.filter { it !in ex }
    }

    private fun pick(s: String, rnd: Random): Char = s[rnd.nextInt(s.length)]

    private fun word(len: Int, cons: String, vow: String, clusterP: Double, rnd: Random): String {
        val sb = StringBuilder()
        var run = 0
        var lastVowel = false
        while (sb.length < len) {
            val wantVowel = when {
                sb.isEmpty() -> false
                lastVowel -> false
                run >= 2 -> true
                else -> rnd.nextDouble() >= clusterP
            }
            val pool = if (wantVowel) vow else cons
            var c = pick(pool, rnd)
            // повтор подряд допускаем редко; фильтр "No double" такие имена отсекает
            if (sb.isNotEmpty() && c == sb.last() && rnd.nextDouble() > 0.1) c = pick(pool, rnd)
            sb.append(c)
            lastVowel = wantVowel
            run = if (wantVowel) 0 else run + 1
        }
        return sb.toString()
    }

    fun generate(p: GenParams, known: Set<String>, rnd: Random = Random.Default): GenResult {
        val pre = p.prefix.lowercase()
        val suf = p.suffix.lowercase()
        val n = p.length - pre.length - suf.length
        if (n < 1) return GenResult(emptyList(), "Prefix and suffix are too long")

        val ex = p.exclude.lowercase().toSet()
        val algos = (if (p.algo == Algo.MIXED) listOf(Algo.PRONOUNCEABLE, Algo.SOFT, Algo.SHARP) else listOf(p.algo))
            .filter { a -> sets(a, ex).let { it.first.isNotEmpty() && it.second.isNotEmpty() } }
        if (algos.isEmpty()) return GenResult(emptyList(), "Too many excluded letters")

        val digits = DIGITS.filter { it !in ex }
        val style = when {
            p.style == Style.DIGITS && (p.noDigits || digits.isEmpty() || n < 3) -> Style.PLAIN
            p.style == Style.UNDERSCORE && (p.noUnderscore || '_' in ex || n < 5) -> Style.PLAIN
            p.style == Style.DOUBLE && n < 4 -> Style.PLAIN
            else -> p.style
        }

        val out = LinkedHashMap<String, Item>()
        repeat(p.count * 80 + 200) {
            if (out.size >= p.count) return@repeat
            val a = algos[rnd.nextInt(algos.size)]
            val (cons, vow) = sets(a, ex)
            val cp = if (a == Algo.SHARP) 0.35 else 0.0
            val core = when (style) {
                Style.PLAIN -> word(n, cons, vow, cp, rnd)
                Style.DIGITS -> {
                    val d = (n / 4).coerceIn(1, 3)
                    word(n - d, cons, vow, cp, rnd) + String(CharArray(d) { pick(digits, rnd) })
                }
                Style.UNDERSCORE -> {
                    val left = (n - 1) / 2
                    word(left, cons, vow, cp, rnd) + "_" + word(n - 1 - left, cons, vow, cp, rnd)
                }
                Style.DOUBLE -> {
                    val w = word(n / 2, cons, vow, cp, rnd)
                    w + w + if (n % 2 == 1) pick(vow, rnd).toString() else ""
                }
            }
            val name = pre + core + suf
            if (!isValid(name) || name in known) return@repeat
            if (p.noDouble && DOUBLE.containsMatchIn(name)) return@repeat
            if (p.noDigits && core.any { it.isDigit() }) return@repeat
            if (p.noUnderscore && '_' in core) return@repeat
            out.putIfAbsent(name, Item(name, a.name))
        }
        if (out.isEmpty()) return GenResult(emptyList(), "No names match the filters")
        return GenResult(out.values.toList())
    }
}
