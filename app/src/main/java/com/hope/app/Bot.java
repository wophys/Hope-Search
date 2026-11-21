package com.hope.app;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Шлёт найденные имена в чат Telegram-бота пачками, чтобы не упереться в лимиты. */
final class Bot {
    static final Pattern TOKEN_RE = Pattern.compile("^\\d{6,12}:[A-Za-z0-9_-]{30,50}$");

    private final SharedPreferences prefs;
    private final List<String[]> free = new ArrayList<>();
    private final List<String[]> frag = new ArrayList<>();
    private long last;

    volatile String token = "";
    volatile String chat = "";
    volatile String name = "";
    volatile int sent;
    volatile String error = "";

    Bot(SharedPreferences prefs) {
        this.prefs = prefs;
        token = prefs.getString("bot_token", "");
        chat = prefs.getString("bot_chat", "");
        name = prefs.getString("bot_name", "");
    }

    boolean ready() {
        return !token.isEmpty() && !chat.isEmpty();
    }

    void connect(String token, String chat, String name) {
        this.token = token;
        this.chat = chat;
        this.name = name;
        prefs.edit().putString("bot_token", token).putString("bot_chat", chat)
                .putString("bot_name", name).apply();
    }

    void clear() {
        token = "";
        chat = "";
        name = "";
        prefs.edit().remove("bot_token").remove("bot_chat").remove("bot_name").apply();
    }

    static JSONObject api(String token, String method, Map<String, String> params) {
        try {
            StringBuilder u = new StringBuilder("https://api.telegram.org/bot")
                    .append(token).append('/').append(method);
            if (params != null && !params.isEmpty()) {
                u.append('?');
                boolean first = true;
                for (Map.Entry<String, String> e : params.entrySet()) {
                    if (!first) u.append('&');
                    first = false;
                    u.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                            .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                }
            }
            Net.Http h = Net.http(u.toString(), 15000);
            if (h.code != 200) return null;
            return new JSONObject(h.body);
        } catch (Exception e) {
            return null;
        }
    }

    /** Короткое служебное сообщение. */
    void say(String text) {
        if (!ready()) return;
        Map<String, String> p = new java.util.LinkedHashMap<>();
        p.put("chat_id", chat);
        p.put("text", text);
        api(token, "sendMessage", p);
    }

    void push(String kind, String name, String info) {
        synchronized (this) {
            if (free.size() + frag.size() < 500) {
                (kind.equals("free") ? free : frag).add(new String[]{name, info});
            }
        }
    }

    void flush(boolean force) {
        List<String[]> f;
        List<String[]> g;
        synchronized (this) {
            int n = free.size() + frag.size();
            if (n == 0 || !ready()) return;
            if (!force && System.currentTimeMillis() - last < 1000) return;   // первая находка уходит сразу
            f = new ArrayList<>(free);
            g = new ArrayList<>(frag);
            free.clear();
            frag.clear();
            last = System.currentTimeMillis();
        }
        List<String> lines = new ArrayList<>();
        lines.add("Hope: найдено");
        if (!f.isEmpty()) {
            lines.add("");
            lines.add("Свободны:");
            for (String[] x : f) lines.add("@" + x[0]);
        }
        if (!g.isEmpty()) {
            lines.add("");
            lines.add("Fragment:");
            for (String[] x : g) lines.add("@" + x[0] + " - " + x[1]);
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : lines) {
            if (cur.length() + line.length() + 1 > 3900) {
                chunks.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(line).append('\n');
        }
        chunks.add(cur.toString());

        boolean ok = true;
        for (String ch : chunks) {
            Map<String, String> p = new java.util.LinkedHashMap<>();
            p.put("chat_id", chat);
            p.put("text", ch);
            JSONObject r = api(token, "sendMessage", p);
            ok = ok && r != null && r.optBoolean("ok");
        }
        if (ok) {
            sent += f.size() + g.size();
            error = "";
        } else {
            error = "не отправилось, попробую ещё";
            synchronized (this) {
                free.addAll(0, f);
                frag.addAll(0, g);
                while (free.size() > 500) free.remove(free.size() - 1);
                while (frag.size() > 500) frag.remove(frag.size() - 1);
            }
        }
    }
}
