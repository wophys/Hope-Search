package com.hope.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Проверка username на t.me и Fragment. Только стандартная библиотека. */
final class Net {
    private Net() {}

    static final int TIMEOUT_MS = 10000;

    static final Pattern OG_TITLE = Pattern.compile("<meta property=\"og:title\" content=\"([^\"]*)\"");
    static final Pattern FRAG_PRICE = Pattern.compile(
            "icon-ton[^>]*>\\s*([0-9][0-9,. ]*[0-9])\\s*<", Pattern.CASE_INSENSITIVE);
    static final Pattern FRAG_STATUS = Pattern.compile(
            "tm-section-header-status[^>]*>\\s*([^<]+?)\\s*<", Pattern.CASE_INSENSITIVE);

    // ---------------------------------------------------------------- HTTP

    static final class Http {
        final int code;
        final String body;
        final String url;

        Http(int code, String body, String url) {
            this.code = code;
            this.body = body;
            this.url = url;
        }
    }

    static Http http(String url, int timeoutMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            String finalUrl = c.getURL().toString();
            String body = "";
            if (code == 200) body = read(c.getInputStream(), 400000);
            return new Http(code, body, finalUrl);
        } catch (Exception e) {
            return new Http(0, String.valueOf(e.getMessage()), url);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in, int limit) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (out.size() < limit && (n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    // ---------------------------------------------------------------- лимитеры

    /** Не больше rps запросов в секунду на все потоки. */
    static class Limiter {
        private final long gapNs;
        private long next = System.nanoTime();

        Limiter(double rps) {
            this.gapNs = (long) (1e9 / rps);
        }

        void await() throws InterruptedException {
            long now;
            long slot;
            synchronized (this) {
                now = System.nanoTime();
                slot = Math.max(next, now);
                next = slot + gapNs;
            }
            long wait = slot - now;
            if (wait > 0) Thread.sleep(wait / 1000000L, (int) (wait % 1000000L));
        }
    }

    /** Экстра-режим: ничего не ограничивает, только считает текущую скорость. */
    static final class ExtraLimiter extends Limiter {
        private final ArrayDeque<Long> stamps = new ArrayDeque<>();

        ExtraLimiter() {
            super(1000);
        }

        @Override
        void await() {
            synchronized (stamps) {
                long now = System.nanoTime();
                stamps.addLast(now);
                trim(now);
            }
        }

        private void trim(long now) {
            while (!stamps.isEmpty() && now - stamps.peekFirst() >= 1000000000L) {
                stamps.pollFirst();
            }
        }

        int speed() {
            synchronized (stamps) {
                trim(System.nanoTime());
                return stamps.size();
            }
        }
    }

    // ---------------------------------------------------------------- проверки

    static final class Res {
        final String status;   // free, taken, fragment, error, stopped, listed, none
        final String info;

        Res(String status, String info) {
            this.status = status;
            this.info = info;
        }
    }

    static final class Checker {
        private final AtomicBoolean stop;
        private final Limiter lim;
        private final Limiter fragLim;
        private final boolean fragment;

        Checker(AtomicBoolean stop, Limiter lim, Limiter fragLim, boolean fragment) {
            this.stop = stop;
            this.lim = lim;
            this.fragLim = fragLim;
            this.fragment = fragment;
        }

        private boolean pause(long ms) {
            try {
                Thread.sleep(ms);
                return !stop.get();
            } catch (InterruptedException e) {
                return false;
            }
        }

        Res checkTme(String name) {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (stop.get()) return new Res("stopped", "");
                try {
                    lim.await();
                } catch (InterruptedException e) {
                    return new Res("stopped", "");
                }
                Http h = http("https://t.me/" + name, TIMEOUT_MS);
                if (h.code == 429) {
                    if (!pause(2000L + attempt * 2000L)) return new Res("stopped", "");
                    continue;
                }
                if (h.code != 200) {
                    if (!pause(1000)) return new Res("stopped", "");
                    continue;
                }
                Matcher m = OG_TITLE.matcher(h.body);
                if (!m.find()) {
                    if (!pause(1000)) return new Res("stopped", "");
                    continue;
                }
                String title = m.group(1);
                if (title.startsWith("Telegram: Contact @")) return new Res("free", "");
                return new Res("taken", title);
            }
            return new Res("error", "лимит/сеть");
        }

        Res checkFragment(String name) {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (stop.get()) return new Res("stopped", "");
                try {
                    fragLim.await();
                } catch (InterruptedException e) {
                    return new Res("stopped", "");
                }
                Http h = http("https://fragment.com/username/" + name, TIMEOUT_MS);
                if (h.code == 429) {
                    if (!pause(3000L + attempt * 3000L)) return new Res("stopped", "");
                    continue;
                }
                if (h.code != 200) {
                    if (!pause(1000)) return new Res("stopped", "");
                    continue;
                }
                if (!h.url.toLowerCase().contains("/username/")) return new Res("none", "");
                Matcher m = FRAG_STATUS.matcher(h.body);
                String info = m.find() ? m.group(1).trim() : "есть на Fragment";
                Matcher p = FRAG_PRICE.matcher(h.body);
                if (p.find()) info += " · " + p.group(1).trim() + " TON";
                return new Res("listed", info);
            }
            return new Res("error", "лимит/сеть");
        }

        /** Полная проверка одного имени. */
        Res check(String name) {
            try {
                Res r = checkTme(name);
                if (!r.status.equals("free")) return r;
                // свободное проверяем ещё раз, чтобы случайный сбой не дал ложный результат
                r = checkTme(name);
                if (!r.status.equals("free")) return r;
                if (fragment) {
                    Res f = checkFragment(name);
                    if (f.status.equals("listed")) return new Res("fragment", f.info);
                    if (!f.status.equals("none")) {
                        return f.status.equals("stopped") ? new Res("stopped", "") : new Res("error", "Fragment");
                    }
                }
                return new Res("free", "");
            } catch (Exception e) {
                return new Res("error", String.valueOf(e.getMessage()));
            }
        }

        /** Возвращает текст проблемы или null, если проверка работает верно. */
        String selfTest() {
            Random rnd = new Random();
            StringBuilder sb = new StringBuilder("xq");
            for (int i = 0; i < 14; i++) sb.append((char) ('a' + rnd.nextInt(26)));
            String random = sb.toString();

            String s1 = checkTme("durov").status;
            String s2 = checkTme(random).status;
            if (stop.get()) return "Остановлено.";
            if (s1.equals("error") || s2.equals("error")) {
                return "Нет связи с t.me. Проверь интернет и попробуй ещё раз.";
            }
            if (!(s1.equals("taken") && s2.equals("free"))) {
                return "t.me отвечает странно (durov: " + s1 + ", случайное: " + s2
                        + "). Не запускаю, чтобы не врать.";
            }
            if (fragment) {
                String k = checkFragment("king").status;
                String r = checkFragment(random).status;
                if (stop.get()) return "Остановлено.";
                if (k.equals("error") || r.equals("error")) {
                    return "Нет связи с Fragment. Выключи проверку Fragment или попробуй позже.";
                }
                if (!(k.equals("listed") && r.equals("none"))) {
                    return "Fragment отвечает странно. Выключи его проверку или попробуй позже.";
                }
            }
            return null;
        }
    }
}
