package com.hope.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Генератор username: порт алгоритмов из Kataract. */
final class NameGen {
    private NameGen() {}

    static final int MIN_NAME = 5;
    static final int MAX_NAME = 32;
    static final int STALE_LIMIT = 4000;

    static final Pattern VALID = Pattern.compile("^[a-z][a-z0-9_]{3,30}[a-z0-9]$");
    static final String[] ALGO_KEYS = {"pron", "soft", "sharp", "mixed"};
    static final String[] ALGO_LABELS = {"Благозвучные", "Мягкие", "Резкие", "Смесь"};

    private static final Pattern DOUBLE = Pattern.compile("(.)\\1");
    private static final Pattern TRIPLE = Pattern.compile("(.)\\1\\1");
    private static final Pattern BAD_Q = Pattern.compile("q(?!u[aeio])");
    private static final Pattern CONS3 = Pattern.compile("[^aeiouy]{3,}");
    private static final Pattern VOW3 = Pattern.compile("[aeiouy]{3,}");
    private static final String RARE = "xzqj";
    private static final String[] BAD_PAIRS = {"wv", "vw", "fv", "vf", "kg", "gk", "bp", "pb",
            "hj", "jh", "xh", "hx", "wj", "jw", "jv", "vj", "zj", "jz"};

    // ---------------------------------------------------------------- разбор ввода

    static List<String> tokens(String raw) {
        List<String> out = new ArrayList<>();
        for (String t : raw.trim().split("[\\s,;]+")) {
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static String cleanPart(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        while (t.startsWith("@")) t = t.substring(1);
        t = t.replaceAll("[^a-z0-9_]", "");
        return t.replaceAll("_+", "_");
    }

    private static String stripTrailingUnderscores(String s) {
        int e = s.length();
        while (e > 0 && s.charAt(e - 1) == '_') e--;
        return s.substring(0, e);
    }

    static List<String> parsePrefixes(String raw) {
        List<String> good = new ArrayList<>();
        for (String t : tokens(raw)) {
            String p = cleanPart(t);
            if (!p.isEmpty() && p.charAt(0) >= 'a' && p.charAt(0) <= 'z' && !good.contains(p)) {
                good.add(p);
            }
        }
        return good;
    }

    static List<String> parseSuffixes(String raw) {
        List<String> good = new ArrayList<>();
        for (String t : tokens(raw)) {
            String s = stripTrailingUnderscores(cleanPart(t));
            if (!s.isEmpty() && !good.contains(s)) good.add(s);
        }
        return good;
    }

    static void parseExclusions(String raw, Set<String> letters, List<String> combos) {
        for (String t : tokens(raw.toLowerCase(Locale.ROOT))) {
            String c = t.replaceAll("[^a-z0-9_]", "");
            if (c.length() == 1) {
                letters.add(c);
            } else if (c.length() > 1 && !combos.contains(c)) {
                combos.add(c);
            }
        }
    }

    // ---------------------------------------------------------------- веса букв

    static final class Pool {
        final String[] keys;
        final int[] weights;
        final int total;

        private Pool(String[] keys, int[] weights, int total) {
            this.keys = keys;
            this.weights = weights;
            this.total = total;
        }

        static Pool of(Map<String, Integer> m) {
            if (m.isEmpty()) return null;
            String[] k = new String[m.size()];
            int[] w = new int[m.size()];
            int i = 0;
            int t = 0;
            for (Map.Entry<String, Integer> e : m.entrySet()) {
                k[i] = e.getKey();
                w[i] = e.getValue();
                t += w[i];
                i++;
            }
            return new Pool(k, w, t);
        }

        static Pool merge(Pool a, Pool b) {
            Map<String, Integer> d = new LinkedHashMap<>();
            for (Pool p : new Pool[]{a, b}) {
                if (p == null) continue;
                for (int i = 0; i < p.keys.length; i++) {
                    Integer cur = d.get(p.keys[i]);
                    d.put(p.keys[i], (cur == null ? 0 : cur) + p.weights[i]);
                }
            }
            return of(d);
        }

        String pick(Random r) {
            int x = r.nextInt(total);
            for (int i = 0; i < keys.length; i++) {
                x -= weights[i];
                if (x < 0) return keys[i];
            }
            return keys[keys.length - 1];
        }
    }

    private static Map<String, Integer> map(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    private static final class Style {
        final String boostC;
        final String boostV;
        final Map<String, Integer> onset;
        final Map<String, Integer> coda;
        final String ends;
        final String[] digraphs;

        Style(String boostC, String boostV, Map<String, Integer> onset, Map<String, Integer> coda,
              String ends, String[] digraphs) {
            this.boostC = boostC;
            this.boostV = boostV;
            this.onset = onset;
            this.coda = coda;
            this.ends = ends;
            this.digraphs = digraphs;
        }
    }

    private static final Map<String, Integer> BASE_C = map(
            "b", 2, "c", 1, "d", 2, "f", 1, "g", 2, "h", 1, "j", 1, "k", 2, "l", 3, "m", 3,
            "n", 3, "p", 2, "r", 3, "s", 3, "t", 3, "v", 2, "w", 1, "x", 1, "y", 1, "z", 1);
    private static final Map<String, Integer> BASE_V = map(
            "a", 3, "e", 3, "i", 2, "o", 3, "u", 2);

    private static final Map<String, Style> STYLES = new LinkedHashMap<>();

    static {
        STYLES.put("pron", new Style("", "",
                map("st", 2, "tr", 2, "br", 1, "pl", 1, "kr", 1, "gr", 1,
                        "dr", 1, "fl", 1, "sl", 1, "pr", 1, "sn", 1, "sk", 1),
                map("nd", 2, "nt", 2, "nk", 1, "st", 2, "rk", 1, "rt", 1,
                        "lt", 1, "ld", 1, "mp", 1, "rn", 1),
                "nrslmtkd",
                new String[]{"ea", "ou", "ia", "io", "ai", "ue", "ie", "oa"}));
        STYLES.put("soft", new Style("gphnms", "euoa",
                map("qu", 4, "pl", 2, "pr", 2, "gl", 2, "gr", 1, "sm", 2,
                        "sn", 2, "sh", 2, "ph", 2, "sp", 1),
                map("mp", 2, "nd", 1, "ng", 1, "ns", 1, "ms", 1, "sh", 2),
                "nmshgp",
                new String[]{"ea", "ou", "ia", "io", "ue", "ua", "eo", "ie", "oe"}));
        STYLES.put("sharp", new Style("kxzvwtfyj", "",
                map("kr", 2, "tr", 2, "vr", 1, "fr", 1, "zv", 1, "kl", 1,
                        "sk", 2, "st", 2, "tw", 1, "sw", 1, "fl", 1, "zh", 1),
                map("kt", 1, "xt", 1, "st", 2, "sk", 2, "ft", 2, "nk", 1,
                        "tz", 1, "rk", 1),
                "kxztf",
                new String[]{"ai", "ei", "ou", "ia", "io", "au"}));
    }

    private static boolean ok(String s, Set<String> exLetters, List<String> exCombos) {
        for (int i = 0; i < s.length(); i++) {
            if (exLetters.contains(String.valueOf(s.charAt(i)))) return false;
        }
        for (String cb : exCombos) {
            if (s.contains(cb)) return false;
        }
        return true;
    }

    /** Наборы букв и сочетаний одного алгоритма, уже без исключённого. */
    static final class Profile {
        Pool cons, vows, onset, coda, mid, ends;
        List<String> digraphs = new ArrayList<>();

        private Profile() {}

        Profile(String style, Set<String> exL, List<String> exC) {
            Style sp = STYLES.get(style);
            Map<String, Integer> c = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : BASE_C.entrySet()) {
                if (ok(e.getKey(), exL, exC)) {
                    c.put(e.getKey(), e.getValue() * (sp.boostC.contains(e.getKey()) ? 6 : 1));
                }
            }
            Map<String, Integer> v = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : BASE_V.entrySet()) {
                if (ok(e.getKey(), exL, exC)) {
                    v.put(e.getKey(), e.getValue() * (sp.boostV.contains(e.getKey()) ? 6 : 1));
                }
            }
            Map<String, Integer> on = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : sp.onset.entrySet()) {
                if (ok(e.getKey(), exL, exC)) on.put(e.getKey(), e.getValue());
            }
            Map<String, Integer> co = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : sp.coda.entrySet()) {
                if (ok(e.getKey(), exL, exC)) co.put(e.getKey(), e.getValue());
            }
            Map<String, Integer> en = new LinkedHashMap<>();
            for (int i = 0; i < sp.ends.length(); i++) {
                String ch = String.valueOf(sp.ends.charAt(i));
                if (ok(ch, exL, exC)) en.put(ch, 1);
            }
            cons = Pool.of(c);
            vows = Pool.of(v);
            onset = Pool.of(on);
            coda = Pool.of(co);
            mid = Pool.merge(onset, coda);
            ends = Pool.of(en);
            for (String d : sp.digraphs) {
                if (ok(d, exL, exC)) digraphs.add(d);
            }
        }

        boolean usable() {
            return cons != null && vows != null && ends != null;
        }

        /** Гласные от этого стиля, согласные от другого: разнообразие для Mixed. */
        Profile mixedWith(Profile other) {
            Profile p = new Profile();
            p.cons = other.cons;
            p.vows = vows;
            p.onset = other.onset;
            p.coda = other.coda;
            p.mid = other.mid;
            p.ends = other.ends;
            p.digraphs = digraphs;
            return p;
        }
    }

    // ---------------------------------------------------------------- сборка имени

    private static String consGroup(Profile p, int left, boolean first, Random rnd) {
        if (left == 1) return p.ends.pick(rnd);
        if (left == 2 && p.coda != null && rnd.nextDouble() < 0.3) return p.coda.pick(rnd);
        if (left >= 3 && rnd.nextDouble() < 0.3) {
            Pool cluster = first ? p.onset : p.mid;
            if (cluster != null) return cluster.pick(rnd);
        }
        return p.cons.pick(rnd);
    }

    private static String vowelGroup(Profile p, int left, boolean first, Random rnd) {
        if (!first && left >= 2 && !p.digraphs.isEmpty() && rnd.nextDouble() < 0.18) {
            return p.digraphs.get(rnd.nextInt(p.digraphs.size()));
        }
        return p.vows.pick(rnd);
    }

    static String buildCore(int n, Profile p, Random rnd) {
        StringBuilder out = new StringBuilder();
        boolean wantCons = rnd.nextDouble() < 0.9;
        while (out.length() < n) {
            int left = n - out.length();
            String piece = wantCons
                    ? consGroup(p, left, out.length() == 0, rnd)
                    : vowelGroup(p, left, out.length() == 0, rnd);
            if (piece == null) return null;
            out.append(piece);
            wantCons = !wantCons;
        }
        return out.length() == n ? out.toString() : null;
    }

    /** Отсеивает некрасивое: удвоения, горы согласных/гласных, странные пары. */
    static boolean readable(String s) {
        if (DOUBLE.matcher(s).find()) return false;
        if (s.indexOf('q') >= 0 && BAD_Q.matcher(s).find()) return false;
        if (CONS3.matcher(s).find() || VOW3.matcher(s).find()) return false;
        boolean vowel = false;
        for (int i = 0; i < s.length(); i++) {
            if ("aeiou".indexOf(s.charAt(i)) >= 0) {
                vowel = true;
                break;
            }
        }
        if (!vowel) return false;
        for (int i = 0; i + 1 < s.length(); i++) {
            if (RARE.indexOf(s.charAt(i)) >= 0 && RARE.indexOf(s.charAt(i + 1)) >= 0) return false;
        }
        for (String bp : BAD_PAIRS) {
            if (s.contains(bp)) return false;
        }
        return true;
    }

    static boolean nameOk(String name, List<String> exCombos) {
        if (!VALID.matcher(name).matches() || name.contains("__")) return false;
        if (TRIPLE.matcher(name).find()) return false;
        for (String cb : exCombos) {
            if (name.contains(cb)) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- план и генерация

    static final class Plan {
        final Map<String, Profile> profiles = new LinkedHashMap<>();
        final Map<Integer, Map<String, List<String[]>>> lengths = new LinkedHashMap<>();
        final List<String> exCombos = new ArrayList<>();
    }

    /** Проверяет настройки заранее. Если генерация невозможна, говорит почему. */
    static Plan prepare(Settings cfg) {
        Plan plan = new Plan();
        Set<String> exL = new HashSet<>();
        parseExclusions(cfg.excludeRaw, exL, plan.exCombos);
        List<String> prefixes = parsePrefixes(cfg.prefixesRaw);
        List<String> suffixes = parseSuffixes(cfg.suffixesRaw);

        String[] names = cfg.algo.equals("mixed")
                ? new String[]{"pron", "soft", "sharp"} : new String[]{cfg.algo};
        for (String k : names) {
            Profile p = new Profile(k, exL, plan.exCombos);
            if (!p.usable()) {
                throw new IllegalArgumentException("после исключений не осталось букв для стиля " + k);
            }
            plan.profiles.put(k, p);
        }

        Map<String, List<String[]>> pairs = new LinkedHashMap<>();
        List<String[]> plain = new ArrayList<>();
        plain.add(new String[]{"", ""});
        pairs.put("plain", plain);
        if (!prefixes.isEmpty()) {
            List<String[]> l = new ArrayList<>();
            for (String p : prefixes) l.add(new String[]{p, ""});
            pairs.put("prefix", l);
        }
        if (!suffixes.isEmpty()) {
            List<String[]> l = new ArrayList<>();
            for (String s : suffixes) l.add(new String[]{"", s});
            pairs.put("suffix", l);
        }
        if (!prefixes.isEmpty() && !suffixes.isEmpty()) {
            List<String[]> l = new ArrayList<>();
            for (String p : prefixes) {
                for (String s : suffixes) l.add(new String[]{p, s});
            }
            pairs.put("both", l);
        }
        if (!cfg.keepPlain && pairs.size() > 1) pairs.remove("plain");

        int lo = Math.max(MIN_NAME, Math.min(cfg.minLen, cfg.maxLen));
        int hi = Math.min(MAX_NAME, Math.max(cfg.minLen, cfg.maxLen));
        for (int length = lo; length <= hi; length++) {
            Map<String, List<String[]>> kinds = new LinkedHashMap<>();
            for (Map.Entry<String, List<String[]>> e : pairs.entrySet()) {
                List<String[]> good = new ArrayList<>();
                for (String[] ps : e.getValue()) {
                    if (length - ps[0].length() - ps[1].length() >= 2) good.add(ps);
                }
                if (!good.isEmpty()) kinds.put(e.getKey(), good);
            }
            if (!kinds.isEmpty()) plan.lengths.put(length, kinds);
        }
        if (plan.lengths.isEmpty()) {
            throw new IllegalArgumentException("префиксы и суффиксы слишком длинные для такой длины имени");
        }
        return plan;
    }

    /** Выдаёт имена-кандидаты по одному. Сама останавливается, если дальше искать нечего. */
    static final class Generator {
        private final Plan plan;
        private final String algo;
        private final Set<String> seen;
        private final int want;
        private final boolean unique;
        private final AtomicBoolean stop;
        private final Random rnd = new Random();
        private final List<Integer> lengths;
        private final List<String> profileNames;
        private final long maxAttempts;

        volatile int made;
        volatile int attempts;
        volatile String reason;
        private int stale;

        Generator(Plan plan, Settings cfg, Set<String> seen, int want, boolean unique,
                  AtomicBoolean stop) {
            this.plan = plan;
            this.algo = cfg.algo;
            this.seen = seen;
            this.want = want;
            this.unique = unique;
            this.stop = stop;
            this.lengths = new ArrayList<>(plan.lengths.keySet());
            this.profileNames = new ArrayList<>(plan.profiles.keySet());
            this.maxAttempts = want > 0 ? (long) want * 500 + 20000 : 3000000L;
        }

        private Profile pickProfile() {
            if (!algo.equals("mixed")) return plan.profiles.get(algo);
            Profile base = plan.profiles.get(profileNames.get(rnd.nextInt(profileNames.size())));
            if (rnd.nextDouble() < 0.25) {
                return base.mixedWith(plan.profiles.get(profileNames.get(rnd.nextInt(profileNames.size()))));
            }
            return base;
        }

        /** Следующее имя или null, если поиск закончен (причина в reason). */
        String next() {
            while (!stop.get()) {
                if (want > 0 && made >= want) {
                    reason = "done";
                    return null;
                }
                if (attempts >= maxAttempts) {
                    reason = "limit";
                    return null;
                }
                if (stale >= STALE_LIMIT) {
                    reason = "exhausted";
                    return null;
                }
                attempts++;
                stale++;
                int length = lengths.get(rnd.nextInt(lengths.size()));
                Map<String, List<String[]>> kinds = plan.lengths.get(length);
                List<String> kindNames = new ArrayList<>(kinds.keySet());
                List<String[]> lst = kinds.get(kindNames.get(rnd.nextInt(kindNames.size())));
                String[] ps = lst.get(rnd.nextInt(lst.size()));
                String core = buildCore(length - ps[0].length() - ps[1].length(), pickProfile(), rnd);
                if (core == null || !readable(core)) continue;
                String name = ps[0] + core + ps[1];
                if (!nameOk(name, plan.exCombos)) continue;
                if (unique) {
                    if (seen.contains(name)) continue;
                    seen.add(name);
                }
                stale = 0;
                made++;
                return name;
            }
            reason = "stopped";
            return null;
        }
    }
}
