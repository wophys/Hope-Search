package dev.hope.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun tonText(p: Double?): String? = p?.let {
    if (it % 1.0 == 0.0) String.format(Locale.US, "%,d TON", it.toLong())
    else String.format(Locale.US, "%,.2f TON", it)
}

object Export {
    fun txt(list: List<UsernameEntity>): String = list.joinToString("\n") { "@${it.name}" }

    fun csv(list: List<UsernameEntity>): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder("username,tme,fragment,price_ton,favorite,algo,checked_at\n")
        for (e in list) {
            val at = if (e.checkedAt > 0) f.format(Date(e.checkedAt)) else ""
            sb.append("${e.name},${e.tme},${e.fragment},${e.priceTon ?: ""},${e.favorite},${e.algo},$at\n")
        }
        return sb.toString()
    }

    fun botText(hits: List<UsernameEntity>): String {
        val sb = StringBuilder("HOPE · ${hits.size} found")
        for (e in hits) {
            sb.append("\n@").append(e.name)
            if (e.tme == Tme.FREE.name) sb.append(" · free")
            if (e.fragment == Frag.LISTED.name) {
                sb.append(" · fragment")
                tonText(e.priceTon)?.let { sb.append(' ').append(it) }
            }
        }
        return sb.toString()
    }
}

data class Stats(
    val total: Int = 0,
    val checked: Int = 0,
    val free: Int = 0,
    val taken: Int = 0,
    val invalid: Int = 0,
    val listed: Int = 0,
    val sold: Int = 0,
    val favorites: Int = 0,
    val avgPrice: Double? = null,
    val byLength: List<Pair<Int, Int>> = emptyList(),
    /** algo, free, total */
    val byAlgo: List<Triple<String, Int, Int>> = emptyList()
) {
    val hitRate: Int get() = if (checked == 0) 0 else free * 100 / checked
}

fun computeStats(list: List<UsernameEntity>): Stats {
    val tmeChecked = list.filter { it.tme == Tme.FREE.name || it.tme == Tme.TAKEN.name || it.tme == Tme.INVALID.name }
    val listed = list.filter { it.fragment == Frag.LISTED.name }
    val prices = listed.mapNotNull { it.priceTon }
    return Stats(
        total = list.size,
        checked = tmeChecked.size,
        free = list.count { it.tme == Tme.FREE.name },
        taken = list.count { it.tme == Tme.TAKEN.name },
        invalid = list.count { it.tme == Tme.INVALID.name },
        listed = listed.size,
        sold = list.count { it.fragment == Frag.SOLD.name },
        favorites = list.count { it.favorite },
        avgPrice = if (prices.isEmpty()) null else prices.average(),
        byLength = list.groupingBy { it.name.length }.eachCount().toList().sortedBy { it.first },
        byAlgo = list.filter { it.algo.isNotEmpty() }.groupBy { it.algo }
            .map { (k, v) -> Triple(k, v.count { it.tme == Tme.FREE.name }, v.size) }
            .sortedByDescending { it.third }
    )
}
