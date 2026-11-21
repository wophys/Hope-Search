package com.hope.app;

/** Настройки поиска. Без зависимостей от Android, чтобы ядро можно было тестировать на JVM. */
final class Settings {
    String algo = "mixed";
    int minLen = 5;
    int maxLen = 6;
    int count = 10;                 // 0 = пока не остановлю
    String prefixesRaw = "";
    String suffixesRaw = "";
    String excludeRaw = "";
    boolean keepPlain = true;       // варианты без префиксов/суффиксов
    boolean unique = true;
    boolean check = true;           // проверка на t.me
    boolean fragment = true;        // проверка Fragment
    boolean save = true;
    boolean extra = false;          // без лимита скорости
    boolean notify = true;          // слать находки в бота

    Settings copy() {
        Settings s = new Settings();
        s.algo = algo;
        s.minLen = minLen;
        s.maxLen = maxLen;
        s.count = count;
        s.prefixesRaw = prefixesRaw;
        s.suffixesRaw = suffixesRaw;
        s.excludeRaw = excludeRaw;
        s.keepPlain = keepPlain;
        s.unique = unique;
        s.check = check;
        s.fragment = fragment;
        s.save = save;
        s.extra = extra;
        s.notify = notify;
        return s;
    }
}
