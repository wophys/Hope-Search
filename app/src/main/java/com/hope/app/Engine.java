package com.hope.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Один запуск поиска: генерация, проверка, сохранение, отправка в бота. */
final class Engine {
    static final double RPS = 8;               // запросов в секунду к t.me
    static final double FRAG_RPS = 3;
    static final int THREADS = 10;
    static final int BATCH = 30;
    static final int EXTRA_THREADS = 60;       // экстра-режим: лимита и пауз нет
    static final int EXTRA_BATCH = 240;
    static final int EXTRA_WARN = 35;          // запр/сек, после которых показываем предупреждение
    static final int MAX_ERR_STREAK = 30;
    static final int DELAY_MS = 20;            // пауза между именами, когда проверка выключена

    static final String OUT_FILE = "hope_free.txt";
    static final String FRAG_FILE = "hope_fragment.txt";
    static final String RAW_FILE = "hope_generated.txt";

    /** Что уже проверяли в этой сессии. */
    private static final Set<String> SESSION_SEEN = Collections.synchronizedSet(new HashSet<String>());
    private static final Object FILE_LOCK = new Object();

    static final class State {
        volatile int checked;
        volatile int taken;
        volatile int errors;
        volatile int errStreak;
        volatile int made;
        volatile int attempts;
        final List<String> results = new CopyOnWriteArrayList<>();
        final List<String[]> frag = new CopyOnWriteArrayList<>();   // {имя, инфо}
        volatile String current = "";
        volatile String phase = "";
        volatile String reason;
        volatile String error;
        volatile boolean finished;
        volatile boolean stopping;
        final long start = System.currentTimeMillis();
        volatile long endTime;
    }

    final Settings cfg;
    final State st = new State();
    private final Bot bot;
    private final File dir;
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private volatile ExecutorService pool;
    private volatile Net.Limiter lim;
    private Thread thread;

    Engine(Settings cfg, Bot bot, File dir) {
        this.cfg = cfg;
        this.bot = bot;
        this.dir = dir;
    }

    void start() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                work();
            }
        }, "hope-engine");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        st.stopping = true;
        stop.set(true);
        ExecutorService p = pool;
        if (p != null) p.shutdownNow();
    }

    int speed() {
        Net.Limiter l = lim;
        return (l instanceof Net.ExtraLimiter) ? ((Net.ExtraLimiter) l).speed() : 0;
    }

    // ---------------------------------------------------------------- файлы

    static File file(File dir, String name) {
        return new File(dir, name);
    }

    private static void append(File f, String line) {
        try {
            synchronized (FILE_LOCK) {
                FileOutputStream out = new FileOutputStream(f, true);
                try {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                } finally {
                    out.close();
                }
            }
        } catch (Exception ignored) {
            // запись не должна ронять поиск
        }
    }

    static Set<String> loadSeen(File f) {
        Set<String> seen = new HashSet<>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] parts = line.trim().split(" ");
                    String n = parts[0];
                    while (n.startsWith("@")) n = n.substring(1);
                    n = n.toLowerCase();
                    if (!n.isEmpty()) seen.add(n);
                }
            } finally {
                r.close();
            }
        } catch (Exception ignored) {
            // файла может ещё не быть
        }
        return seen;
    }

    // ---------------------------------------------------------------- разбор результата

    private void handle(String name, Net.Res res) {
        st.current = name;
        String status = res.status;
        if (status.equals("stopped")) return;
        st.checked++;
        if (status.equals("error")) {
            st.errors++;
            st.errStreak++;
            SESSION_SEEN.remove(name);     // попробуем это имя ещё раз позже
            return;
        }
        st.errStreak = 0;
        if (status.equals("free")) {
            st.results.add(name);
            if (cfg.save) append(file(dir, OUT_FILE), "@" + name);
            if (cfg.notify && bot.ready()) bot.push("free", name, "");
        } else if (status.equals("fragment")) {
            st.frag.add(new String[]{name, res.info});
            if (cfg.save) append(file(dir, FRAG_FILE), "@" + name + " - " + res.info);
            if (cfg.notify && bot.ready()) bot.push("frag", name, res.info);
        } else {
            st.taken++;
        }
    }

    private static final class Item {
        final String name;
        final Net.Res res;

        Item(String name, Net.Res res) {
            this.name = name;
            this.res = res;
        }
    }

    // ---------------------------------------------------------------- поток поиска

    private void work() {
        try {
            NameGen.Plan plan;
            try {
                plan = NameGen.prepare(cfg);
            } catch (IllegalArgumentException e) {
                st.error = "Так не получится: " + e.getMessage();
                st.reason = "error";
                return;
            }

            boolean extra = cfg.check && cfg.extra;
            lim = extra ? new Net.ExtraLimiter() : new Net.Limiter(RPS);
            Net.Checker checker = null;
            if (cfg.check) {
                st.phase = "Проверка связи";
                checker = new Net.Checker(stop, lim, new Net.Limiter(FRAG_RPS), cfg.fragment);
                String problem = checker.selfTest();
                if (problem != null) {
                    st.error = problem;
                    st.reason = stop.get() ? "stopped" : "error";
                    return;
                }
            }

            st.phase = cfg.check ? "Поиск свободных" : "Генерация";
            boolean unique = cfg.unique || cfg.check;
            Set<String> seen = unique ? SESSION_SEEN : new HashSet<String>();
            if (unique && cfg.save) {
                seen.addAll(loadSeen(file(dir, cfg.check ? OUT_FILE : RAW_FILE)));
            }

            NameGen.Generator gen = new NameGen.Generator(plan, cfg, seen,
                    checker == null ? cfg.count : 0, unique, stop);
            if (checker != null) pool = Executors.newFixedThreadPool(extra ? EXTRA_THREADS : THREADS);
            final Net.Checker chk = checker;

            if (cfg.notify && bot.ready()) {
                bot.say("Hope: поиск начат. Находки буду присылать сразу.");
            }

            if (chk == null) {
                // только генерация
                String n;
                while (!stop.get() && (n = gen.next()) != null) {
                    st.results.add(n);
                    st.current = n;
                    st.made = gen.made;
                    st.attempts = gen.attempts;
                    if (cfg.save) append(file(dir, RAW_FILE), "@" + n);
                    if (cfg.notify && bot != null && bot.ready()) {
                        bot.push("free", n, "");
                        bot.flush(false);
                    }
                    try {
                        Thread.sleep(DELAY_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } else {
                // поток: имена генерируются и сразу уходят на проверку, без ожидания пачки
                int maxInflight = (extra ? EXTRA_THREADS : THREADS) * 2;
                ExecutorCompletionService<Item> ecs = new ExecutorCompletionService<>(pool);
                int inflight = 0;
                boolean genDone = false;
                boolean finished = false;
                while (!stop.get() && !finished) {
                    while (!genDone && inflight < maxInflight && !stop.get()) {
                        final String n = gen.next();
                        if (n == null) {
                            genDone = true;
                            break;
                        }
                        ecs.submit(new Callable<Item>() {
                            @Override
                            public Item call() {
                                return new Item(n, chk.check(n));
                            }
                        });
                        inflight++;
                    }
                    st.made = gen.made;
                    st.attempts = gen.attempts;
                    if (inflight == 0) break;

                    Future<Item> f;
                    try {
                        f = ecs.poll(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        break;
                    }
                    if (f != null) {
                        inflight--;
                        try {
                            Item it = f.get();
                            handle(it.name, it.res);
                        } catch (ExecutionException | java.util.concurrent.CancellationException
                                 | InterruptedException e) {
                            // пропускаем сорвавшуюся проверку
                        }
                        if (cfg.count > 0 && st.results.size() >= cfg.count) {
                            st.reason = "done";
                            finished = true;
                        } else if (st.errStreak >= MAX_ERR_STREAK) {
                            st.reason = "network";
                            finished = true;
                        }
                    }
                    if (cfg.notify) bot.flush(false);
                }
            }

            if (st.reason == null) {
                st.reason = (stop.get() || gen.reason == null) ? "stopped" : gen.reason;
            }
        } catch (Throwable t) {          // поток не должен падать молча
            if (!stop.get()) st.error = String.valueOf(t);   // при остановке пула сбой ожидаем
            if (st.reason == null) st.reason = stop.get() ? "stopped" : "error";
        } finally {
            ExecutorService p = pool;
            if (p != null) p.shutdownNow();
            if (cfg.notify && bot.ready()) {
                try {
                    bot.flush(true);
                } catch (Throwable ignored) {
                    // отправка в бота не критична
                }
            }
            if (st.reason == null) st.reason = "stopped";
            st.endTime = System.currentTimeMillis();
            st.finished = true;
        }
    }
}
