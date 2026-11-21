package com.hope.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

public class MainActivity extends Activity {

    // Палитра: слои Canvas -> Surface -> Interactive -> Border, текст не чисто белый.
    static final int CANVAS = 0xFF000000;
    static final int S1 = 0xFF0C0C0D;
    static final int S2 = 0xFF151517;
    static final int S3 = 0xFF1F1F23;
    static final int BORDER = 0xFF26262A;
    static final int TEXT = 0xFFE9E9EC;
    static final int TEXT2 = 0xFF9B9BA3;
    static final int TEXT3 = 0xFF62626A;
    static final int ACCENT = 0xFFE8C37A;
    static final int ON_ACCENT = 0xFF0A0A0A;
    static final int OK = 0xFF6FCF97;
    static final int DANGER = 0xFFF2706B;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int MAX_ROWS = 150;

    private static Engine engine;       // переживает пересоздание Activity
    private static Bot bot;

    private SharedPreferences prefs;
    private Settings cfg;
    private File filesDir;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // экран
    private ScrollView scroll;
    private LinearLayout liveCard;
    private TextView liveStatus, liveCurrent, liveWarn, liveBot, liveError, liveReason;
    private final TextView[] statVals = new TextView[6];
    private final TextView[] statLabs = new TextView[6];
    private LinearLayout resultsBox, fragBox;
    private TextView fragLabel;
    private TextView runBtn;
    private TextView[] algoBtns;
    private Stepper stepMin, stepMax;
    private EditText countEdit, prefixEdit, suffixEdit, excludeEdit;
    private TextView extraNote, botSub, botBtn, filesInfo;

    private Engine renderedEngine;
    private int renderedFree, renderedFrag;
    private boolean wasRunning;

    // ---------------------------------------------------------------- жизненный цикл

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("hope", MODE_PRIVATE);
        cfg = loadSettings();
        if (bot == null) bot = new Bot(prefs);
        File ext = getExternalFilesDir(null);
        filesDir = ext != null ? ext : getFilesDir();
        buildUi();
        refreshBot();
        refreshFiles();
        if (engine != null) {
            wasRunning = !engine.st.finished;
            render();
            if (wasRunning) ui.postDelayed(tick, 300);
        }
        updateRunButton();
    }

    @Override
    protected void onPause() {
        super.onPause();
        collectInputs();
        storeSettings();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(tick);
        super.onDestroy();
    }

    // ---------------------------------------------------------------- настройки

    private Settings loadSettings() {
        Settings s = new Settings();
        s.algo = prefs.getString("algo", s.algo);
        s.minLen = prefs.getInt("minLen", s.minLen);
        s.maxLen = prefs.getInt("maxLen", s.maxLen);
        s.count = prefs.getInt("count", s.count);
        s.prefixesRaw = prefs.getString("prefixes", s.prefixesRaw);
        s.suffixesRaw = prefs.getString("suffixes", s.suffixesRaw);
        s.excludeRaw = prefs.getString("exclude", s.excludeRaw);
        s.keepPlain = prefs.getBoolean("keepPlain", s.keepPlain);
        s.unique = prefs.getBoolean("unique", s.unique);
        s.check = prefs.getBoolean("check", s.check);
        s.fragment = prefs.getBoolean("fragment", s.fragment);
        s.save = prefs.getBoolean("save", s.save);
        s.extra = prefs.getBoolean("extra", s.extra);
        s.notify = prefs.getBoolean("notify", s.notify);
        return s;
    }

    private void storeSettings() {
        prefs.edit()
                .putString("algo", cfg.algo)
                .putInt("minLen", cfg.minLen)
                .putInt("maxLen", cfg.maxLen)
                .putInt("count", cfg.count)
                .putString("prefixes", cfg.prefixesRaw)
                .putString("suffixes", cfg.suffixesRaw)
                .putString("exclude", cfg.excludeRaw)
                .putBoolean("keepPlain", cfg.keepPlain)
                .putBoolean("unique", cfg.unique)
                .putBoolean("check", cfg.check)
                .putBoolean("fragment", cfg.fragment)
                .putBoolean("save", cfg.save)
                .putBoolean("extra", cfg.extra)
                .putBoolean("notify", cfg.notify)
                .apply();
    }

    private void collectInputs() {
        if (countEdit == null) return;
        String c = countEdit.getText().toString().trim();
        int n = 0;
        try {
            n = c.isEmpty() ? 0 : Integer.parseInt(c);
        } catch (NumberFormatException ignored) {
            n = cfg.count;
        }
        cfg.count = Math.max(0, Math.min(n, 100000));
        cfg.prefixesRaw = prefixEdit.getText().toString();
        cfg.suffixesRaw = suffixEdit.getText().toString();
        cfg.excludeRaw = excludeEdit.getText().toString();
    }

    // ---------------------------------------------------------------- помощники UI

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rect(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private Drawable ripple(Drawable bg, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), bg, rect(0xFFFFFFFF, 0, radiusDp));
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Мелкая подпись заглавными с разрядкой. */
    private TextView label(String s) {
        TextView t = tv(s, 11, TEXT2, true);
        t.setLetterSpacing(0.1f);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout l = column();
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(rect(S1, BORDER, 14));
        return l;
    }

    private LinearLayout.LayoutParams lpv(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.topMargin = dp(topDp);
        return p;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, WRAP, 1f);
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, 1));
        return v;
    }

    private TextView textButton(String s, View.OnClickListener l) {
        TextView t = tv(s, 14, ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(44));
        t.setPadding(dp(14), 0, dp(14), 0);
        t.setBackground(ripple(rect(0x00000000, 0, 10), 10));
        t.setOnClickListener(l);
        return t;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(TEXT3);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setMinHeight(dp(48));
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        e.setBackground(rect(S2, BORDER, 10));
        return e;
    }

    private LinearLayout fieldBlock(String title, EditText e) {
        LinearLayout b = column();
        b.addView(tv(title, 13, TEXT2, false));
        b.addView(e, lpv(6));
        return b;
    }

    private Switch toggle(boolean on, final Runnable changed) {
        Switch sw = new Switch(this);
        sw.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, TEXT3}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{0x66E8C37A, S3}));
        sw.setOnCheckedChangeListener((v, checked) -> changed.run());
        return sw;
    }

    private LinearLayout switchRow(String title, String hint, Switch sw) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(52));
        LinearLayout texts = column();
        texts.addView(tv(title, 15, TEXT, false));
        if (hint != null) texts.addView(tv(hint, 12, TEXT2, false));
        r.addView(texts, weight());
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        return r;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** Плюс/минус со значением по центру. */
    private final class Stepper extends LinearLayout {
        int value;
        final int min;
        final int max;
        final TextView val;
        final IntConsumer on;

        Stepper(Context c, int initial, int min, int max, IntConsumer on) {
            super(c);
            this.min = min;
            this.max = max;
            this.on = on;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBackground(rect(S2, BORDER, 10));
            val = tv("", 16, TEXT, true);
            val.setFontFeatureSettings("tnum");
            val.setGravity(Gravity.CENTER);
            addView(stepBtn("−", -1), new LayoutParams(dp(48), dp(44)));
            addView(val, new LayoutParams(dp(44), WRAP));
            addView(stepBtn("+", 1), new LayoutParams(dp(48), dp(44)));
            set(initial);
        }

        private TextView stepBtn(String s, final int delta) {
            TextView t = tv(s, 20, TEXT, false);
            t.setGravity(Gravity.CENTER);
            t.setBackground(ripple(rect(0x00000000, 0, 10), 10));
            t.setOnClickListener(v -> {
                set(value + delta);
                on.accept(value);
            });
            return t;
        }

        void set(int v) {
            value = Math.max(min, Math.min(max, v));
            val.setText(String.valueOf(value));
        }
    }

    private Dialog sheet(String title, String message, View content, String okText, final Runnable ok) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(20), dp(20), dp(12));
        box.setBackground(rect(S1, BORDER, 16));
        box.addView(tv(title, 18, TEXT, true));
        if (message != null) {
            TextView m = tv(message, 14, TEXT2, false);
            m.setLineSpacing(0, 1.2f);
            box.addView(m, lpv(8));
        }
        if (content != null) box.addView(content, lpv(14));
        LinearLayout btns = row();
        btns.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        btns.addView(textButton("Отмена", v -> d.dismiss()));
        btns.addView(textButton(okText, v -> {
            d.dismiss();
            if (ok != null) ok.run();
        }));
        box.addView(btns, lpv(10));
        d.setContentView(box);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }

    // ---------------------------------------------------------------- сборка экрана

    private void buildUi() {
        LinearLayout root = column();
        root.setBackgroundColor(CANVAS);

        scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = column();
        col.setPadding(dp(16), dp(20), dp(16), dp(24));
        scroll.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        TextView title = tv("Hope", 32, TEXT, true);
        title.setLetterSpacing(-0.02f);
        col.addView(title);
        col.addView(tv("Поиск свободных username в Telegram", 14, TEXT2, false), lpv(2));

        buildLiveCard(col);
        buildSettingsCard(col);
        buildFilesCard(col);

        // нижняя панель с главной кнопкой
        root.addView(divider());
        LinearLayout bar = column();
        bar.setBackgroundColor(CANVAS);
        bar.setPadding(dp(16), dp(10), dp(16), dp(10));
        runBtn = tv("Запустить", 16, ON_ACCENT, true);
        runBtn.setGravity(Gravity.CENTER);
        runBtn.setOnClickListener(v -> onRunClicked());
        bar.addView(runBtn, new LinearLayout.LayoutParams(MATCH, dp(52)));
        root.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));

        setContentView(root);
    }

    private void buildLiveCard(LinearLayout col) {
        liveCard = card();
        liveCard.setVisibility(View.GONE);

        LinearLayout head = row();
        head.addView(label("ПОИСК"), weight());
        liveStatus = tv("", 13, TEXT2, false);
        liveStatus.setFontFeatureSettings("tnum");
        head.addView(liveStatus);
        liveCard.addView(head);

        for (int r = 0; r < 2; r++) {
            LinearLayout line = row();
            for (int c = 0; c < 3; c++) {
                int i = r * 3 + c;
                LinearLayout cell = column();
                statVals[i] = tv("0", 22, TEXT, true);
                statVals[i].setFontFeatureSettings("tnum");
                statLabs[i] = tv("", 12, TEXT2, false);
                cell.addView(statVals[i]);
                cell.addView(statLabs[i]);
                line.addView(cell, weight());
            }
            liveCard.addView(line, lpv(r == 0 ? 14 : 12));
        }

        liveCurrent = tv("", 13, TEXT2, false);
        liveCurrent.setTypeface(Typeface.MONOSPACE);
        liveCard.addView(liveCurrent, lpv(14));

        liveWarn = tv("", 13, DANGER, false);
        liveBot = tv("", 13, TEXT2, false);
        liveReason = tv("", 14, TEXT, false);
        liveError = tv("", 14, DANGER, false);
        liveCard.addView(liveWarn, lpv(8));
        liveCard.addView(liveBot, lpv(4));
        liveCard.addView(liveReason, lpv(8));
        liveCard.addView(liveError, lpv(4));
        liveWarn.setVisibility(View.GONE);
        liveBot.setVisibility(View.GONE);
        liveReason.setVisibility(View.GONE);
        liveError.setVisibility(View.GONE);

        liveCard.addView(divider(), lpv(14));
        LinearLayout rh = row();
        rh.addView(label("НАЙДЕНО"), weight());
        rh.addView(textButton("Копировать", v -> copyAll()));
        rh.addView(textButton("Поделиться", v -> shareText(allResults())));
        liveCard.addView(rh, lpv(4));

        resultsBox = column();
        liveCard.addView(resultsBox, lpv(4));

        fragLabel = label("НА FRAGMENT");
        fragLabel.setVisibility(View.GONE);
        liveCard.addView(fragLabel, lpv(14));
        fragBox = column();
        liveCard.addView(fragBox, lpv(6));

        col.addView(liveCard, lpv(20));
    }

    private void buildSettingsCard(LinearLayout col) {
        LinearLayout card = card();
        card.addView(label("НАСТРОЙКИ"));

        // алгоритм
        card.addView(tv("Алгоритм", 13, TEXT2, false), lpv(14));
        LinearLayout seg = row();
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        seg.setBackground(rect(S2, BORDER, 12));
        algoBtns = new TextView[NameGen.ALGO_KEYS.length];
        for (int i = 0; i < algoBtns.length; i++) {
            final String key = NameGen.ALGO_KEYS[i];
            TextView b = tv(NameGen.ALGO_LABELS[i], 13, TEXT2, true);
            b.setGravity(Gravity.CENTER);
            b.setOnClickListener(v -> {
                cfg.algo = key;
                refreshAlgo();
            });
            algoBtns[i] = b;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1f);
            seg.addView(b, p);
        }
        card.addView(seg, lpv(6));
        refreshAlgo();

        // длина
        LinearLayout len = row();
        len.setMinimumHeight(dp(56));
        len.addView(tv("Длина: от и до", 15, TEXT, false), weight());
        stepMin = new Stepper(this, cfg.minLen, NameGen.MIN_NAME, NameGen.MAX_NAME, v -> {
            cfg.minLen = v;
            if (cfg.maxLen < v) {
                cfg.maxLen = v;
                stepMax.set(v);
            }
        });
        stepMax = new Stepper(this, cfg.maxLen, NameGen.MIN_NAME, NameGen.MAX_NAME, v -> {
            cfg.maxLen = v;
            if (cfg.minLen > v) {
                cfg.minLen = v;
                stepMin.set(v);
            }
        });
        len.addView(stepMin);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(WRAP, WRAP);
        gap.leftMargin = dp(8);
        len.addView(stepMax, gap);
        card.addView(len, lpv(8));

        // количество, префиксы, суффиксы, исключения
        countEdit = field("0 = пока не остановлю", InputType.TYPE_CLASS_NUMBER);
        countEdit.setText(String.valueOf(cfg.count));
        card.addView(fieldBlock("Сколько найти", countEdit), lpv(8));

        prefixEdit = field("например: mr the", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        prefixEdit.setText(cfg.prefixesRaw);
        card.addView(fieldBlock("Префиксы через пробел", prefixEdit), lpv(14));

        suffixEdit = field("например: x pro", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        suffixEdit.setText(cfg.suffixesRaw);
        card.addView(fieldBlock("Суффиксы через пробел", suffixEdit), lpv(14));

        excludeEdit = field("например: x z ck", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        excludeEdit.setText(cfg.excludeRaw);
        card.addView(fieldBlock("Исключить буквы и сочетания", excludeEdit), lpv(14));

        card.addView(divider(), lpv(14));

        final Switch swPlain = toggle(cfg.keepPlain, null2());
        swPlain.setOnCheckedChangeListener((v, c) -> cfg.keepPlain = c);
        card.addView(switchRow("Варианты без префиксов и суффиксов", null, swPlain));

        final Switch swUnique = toggle(cfg.unique, null2());
        swUnique.setOnCheckedChangeListener((v, c) -> cfg.unique = c);
        card.addView(switchRow("Убирать повторы", "При проверке повторы убираются всегда", swUnique));

        final Switch swCheck = toggle(cfg.check, null2());
        swCheck.setOnCheckedChangeListener((v, c) -> cfg.check = c);
        card.addView(switchRow("Проверка на t.me", "Выключено: только генерация имён", swCheck));

        final Switch swFrag = toggle(cfg.fragment, null2());
        swFrag.setOnCheckedChangeListener((v, c) -> cfg.fragment = c);
        card.addView(switchRow("Проверка Fragment", "Статус и цена, если имя там продаётся", swFrag));

        final Switch swSave = toggle(cfg.save, null2());
        swSave.setOnCheckedChangeListener((v, c) -> cfg.save = c);
        card.addView(switchRow("Сохранять в файл", null, swSave));

        final Switch swExtra = toggle(cfg.extra, null2());
        swExtra.setOnCheckedChangeListener((v, c) -> {
            cfg.extra = c;
            extraNote.setVisibility(c ? View.VISIBLE : View.GONE);
        });
        card.addView(switchRow("Экстра-режим", "Без лимита скорости. Не рекомендуется", swExtra));
        extraNote = tv("Telegram может ограничить запросы с этого устройства. Поиск остановится сам, если пойдут ошибки подряд.",
                12, DANGER, false);
        extraNote.setVisibility(cfg.extra ? View.VISIBLE : View.GONE);
        card.addView(extraNote, lpv(2));

        card.addView(divider(), lpv(10));

        // бот
        LinearLayout botRow = row();
        botRow.setMinimumHeight(dp(56));
        LinearLayout botTexts = column();
        botTexts.addView(tv("Бот Telegram", 15, TEXT, false));
        botSub = tv("", 12, TEXT2, false);
        botTexts.addView(botSub);
        botRow.addView(botTexts, weight());
        botBtn = textButton("Подключить", v -> onBotClicked());
        botRow.addView(botBtn);
        card.addView(botRow, lpv(4));

        final Switch swNotify = toggle(cfg.notify, null2());
        swNotify.setOnCheckedChangeListener((v, c) -> cfg.notify = c);
        card.addView(switchRow("Слать находки в бота", null, swNotify));

        col.addView(card, lpv(16));
    }

    private Runnable null2() {
        return new Runnable() {
            @Override
            public void run() {
            }
        };
    }

    private void buildFilesCard(LinearLayout col) {
        LinearLayout card = card();
        card.addView(label("СОХРАНЁННОЕ"));
        filesInfo = tv("", 14, TEXT2, false);
        filesInfo.setLineSpacing(0, 1.2f);
        card.addView(filesInfo, lpv(10));
        LinearLayout btns = row();
        btns.addView(textButton("Свободные", v -> shareFile(Engine.OUT_FILE)));
        btns.addView(textButton("Fragment", v -> shareFile(Engine.FRAG_FILE)));
        btns.addView(textButton("Очистить", v -> confirmClear()));
        card.addView(btns, lpv(6));
        col.addView(card, lpv(16));
    }

    private void refreshAlgo() {
        for (int i = 0; i < algoBtns.length; i++) {
            boolean sel = NameGen.ALGO_KEYS[i].equals(cfg.algo);
            algoBtns[i].setTextColor(sel ? TEXT : TEXT2);
            algoBtns[i].setBackground(sel ? rect(S3, BORDER, 9) : null);
        }
    }

    // ---------------------------------------------------------------- запуск и остановка

    private void onRunClicked() {
        Engine e = engine;
        if (e != null && !e.st.finished) {
            e.stop();
            render();
            return;
        }
        collectInputs();
        storeSettings();
        engine = new Engine(cfg.copy(), bot, filesDir);
        engine.start();
        wasRunning = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        render();
        scroll.post(() -> scroll.smoothScrollTo(0, 0));
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, 300);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            Engine e = engine;
            if (e != null && !e.st.finished) ui.postDelayed(this, 300);
        }
    };

    private void updateRunButton() {
        Engine e = engine;
        boolean running = e != null && !e.st.finished;
        if (running) {
            runBtn.setText(e.st.stopping ? "Останавливаю…" : "Остановить");
            runBtn.setTextColor(DANGER);
            runBtn.setBackground(ripple(rect(S2, BORDER, 14), 14));
        } else {
            runBtn.setText("Запустить");
            runBtn.setTextColor(ON_ACCENT);
            runBtn.setBackground(ripple(rect(ACCENT, 0, 14), 14));
        }
    }

    // ---------------------------------------------------------------- живой экран

    private static String reasonText(String reason) {
        if (reason == null) return "";
        switch (reason) {
            case "done":
                return "Нашёл нужное количество.";
            case "stopped":
                return "Остановлено.";
            case "exhausted":
                return "Новые варианты закончились: ограничения слишком строгие или всё уже перебрано.";
            case "limit":
                return "Достигнут предел попыток.";
            case "network":
                return "Много ошибок подряд. Проверь интернет или подожди: возможно, Telegram временно ограничил запросы.";
            default:
                return "";
        }
    }

    private static void setText(TextView t, String s) {
        if (s == null || s.isEmpty()) {
            t.setVisibility(View.GONE);
        } else {
            t.setText(s);
            t.setVisibility(View.VISIBLE);
        }
    }

    private void render() {
        Engine e = engine;
        if (e == null) return;
        Engine.State s = e.st;
        Settings c = e.cfg;
        liveCard.setVisibility(View.VISIBLE);

        if (renderedEngine != e) {
            renderedEngine = e;
            renderedFree = 0;
            renderedFrag = 0;
            resultsBox.removeAllViews();
            fragBox.removeAllViews();
            fragLabel.setVisibility(View.GONE);
        }

        long end = s.finished ? s.endTime : System.currentTimeMillis();
        double el = Math.max(0.001, (end - s.start) / 1000.0);
        String clock = String.format(Locale.US, "%02d:%02d", (int) el / 60, (int) el % 60);
        String goal = c.count > 0 ? " / " + c.count : "";

        if (c.check) {
            set(0, s.results.size() + goal, "Свободно", OK);
            set(1, String.valueOf(s.checked), "Проверено", TEXT);
            set(2, String.valueOf(s.taken), "Занято", TEXT);
            set(3, String.valueOf(s.frag.size()), "Fragment", TEXT);
            set(4, String.valueOf(s.errors), "Ошибок", s.errors > 0 ? DANGER : TEXT);
            set(5, String.format(Locale.US, "%.1f", s.checked / el), "в секунду", TEXT);
        } else {
            set(0, s.results.size() + goal, "Создано", OK);
            set(1, String.valueOf(s.attempts), "Попыток", TEXT);
            set(2, clock, "Время", TEXT);
            set(3, "", "", TEXT);
            set(4, "", "", TEXT);
            set(5, "", "", TEXT);
        }

        if (s.finished) {
            liveStatus.setText(clock);
        } else {
            liveStatus.setText(clock + " · " + (s.stopping ? "останавливаю" : s.phase));
        }
        liveCurrent.setText(!s.finished && !s.current.isEmpty() ? "Сейчас: @" + s.current : "");
        liveCurrent.setVisibility(liveCurrent.getText().length() > 0 ? View.VISIBLE : View.GONE);

        if (!s.finished && c.check && c.extra) {
            int sp = e.speed();
            String w = "Экстра-режим: " + sp + " запр/сек";
            if (sp >= Engine.EXTRA_WARN) w += ". Аномально высокая скорость, лучше остановить";
            setText(liveWarn, w);
        } else {
            setText(liveWarn, null);
        }
        setText(liveBot, c.notify && bot.ready()
                ? "Бот: отправлено " + bot.sent + (bot.error.isEmpty() ? "" : " (" + bot.error + ")") : null);
        setText(liveReason, s.finished ? reasonText(s.reason) : null);
        setText(liveError, s.error);

        // новые находки добавляем сверху, держим не больше MAX_ROWS строк
        int n = s.results.size();
        for (int i = renderedFree; i < n; i++) {
            resultsBox.addView(nameRow(s.results.get(i), null), 0, rowParams());
            while (resultsBox.getChildCount() > MAX_ROWS) {
                resultsBox.removeViewAt(resultsBox.getChildCount() - 1);
            }
        }
        renderedFree = n;
        int m = s.frag.size();
        for (int i = renderedFrag; i < m; i++) {
            String[] f = s.frag.get(i);
            fragBox.addView(nameRow(f[0], f[1]), 0, rowParams());
            fragLabel.setVisibility(View.VISIBLE);
            while (fragBox.getChildCount() > MAX_ROWS) {
                fragBox.removeViewAt(fragBox.getChildCount() - 1);
            }
        }
        renderedFrag = m;

        updateRunButton();
        if (s.finished && wasRunning) {
            wasRunning = false;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            refreshFiles();
        }
    }

    private void set(int i, String value, String lab, int color) {
        statVals[i].setText(value);
        statVals[i].setTextColor(color);
        statLabs[i].setText(lab);
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.bottomMargin = dp(6);
        return p;
    }

    private View nameRow(final String name, String info) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(48));
        r.setPadding(dp(14), dp(4), dp(4), dp(4));
        r.setBackground(ripple(rect(S2, 0, 10), 10));
        LinearLayout texts = column();
        TextView n = tv("@" + name, 15, TEXT, false);
        n.setTypeface(Typeface.MONOSPACE);
        texts.addView(n);
        if (info != null) texts.addView(tv(info, 12, TEXT2, false));
        r.addView(texts, weight());
        TextView open = tv("t.me", 13, ACCENT, true);
        open.setGravity(Gravity.CENTER);
        open.setMinWidth(dp(56));
        open.setMinHeight(dp(44));
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + name)));
            } catch (ActivityNotFoundException ex) {
                toast("Нечем открыть ссылку");
            }
        });
        r.addView(open);
        r.setOnClickListener(v -> copy("@" + name));
        return r;
    }

    // ---------------------------------------------------------------- буфер и «поделиться»

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Hope", text));
            toast("Скопировано");
        }
    }

    private String allResults() {
        Engine e = engine;
        if (e == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String n : e.st.results) sb.append('@').append(n).append('\n');
        for (String[] f : e.st.frag) sb.append('@').append(f[0]).append(" - ").append(f[1]).append('\n');
        return sb.toString();
    }

    private void copyAll() {
        String t = allResults();
        if (t.isEmpty()) {
            toast("Пока нечего копировать");
        } else {
            copy(t);
        }
    }

    private void shareText(String text) {
        if (text.isEmpty()) {
            toast("Пока нечем делиться");
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Поделиться"));
    }

    private static String readFile(File f, int limit) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = r.readLine()) != null && sb.length() < limit) {
                    sb.append(line).append('\n');
                }
            } finally {
                r.close();
            }
        } catch (Exception ignored) {
            // файла нет
        }
        return sb.toString();
    }

    private static int countLines(File f) {
        int n = 0;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            try {
                while (r.readLine() != null) n++;
            } finally {
                r.close();
            }
        } catch (Exception ignored) {
            // файла нет
        }
        return n;
    }

    private void shareFile(String name) {
        shareText(readFile(new File(filesDir, name), 200000));
    }

    private void refreshFiles() {
        int free = countLines(new File(filesDir, Engine.OUT_FILE));
        int frag = countLines(new File(filesDir, Engine.FRAG_FILE));
        int raw = countLines(new File(filesDir, Engine.RAW_FILE));
        String s = "Свободные: " + free + "\nFragment: " + frag + "\nСгенерированные: " + raw;
        filesInfo.setText(s);
    }

    private void confirmClear() {
        sheet("Очистить сохранённое?", "Файлы со свободными, Fragment и сгенерированными именами будут удалены.",
                null, "Очистить", () -> {
                    new File(filesDir, Engine.OUT_FILE).delete();
                    new File(filesDir, Engine.FRAG_FILE).delete();
                    new File(filesDir, Engine.RAW_FILE).delete();
                    refreshFiles();
                    toast("Удалено");
                });
    }

    // ---------------------------------------------------------------- бот

    private void refreshBot() {
        if (bot.ready()) {
            botSub.setText("@" + bot.name);
            botBtn.setText("Отключить");
        } else {
            botSub.setText("не подключён");
            botBtn.setText("Подключить");
        }
    }

    private void onBotClicked() {
        if (bot.ready()) {
            bot.clear();
            refreshBot();
            toast("Бот отключён, токен удалён");
            return;
        }
        final EditText tok = field("123456:ABC…", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        sheet("Бот Telegram",
                "Создай бота в @BotFather и вставь токен. Он хранится только на этом устройстве.",
                tok, "Дальше", () -> verifyToken(tok.getText().toString().trim()));
    }

    private void verifyToken(final String token) {
        if (!Bot.TOKEN_RE.matcher(token).matches()) {
            toast("Это не похоже на токен бота");
            return;
        }
        toast("Проверяю…");
        new Thread(() -> {
            JSONObject me = Bot.api(token, "getMe", null);
            String uname = null;
            if (me != null && me.optBoolean("ok")) {
                JSONObject res = me.optJSONObject("result");
                if (res != null) uname = res.optString("username", "");
            }
            final String name = uname;
            runOnUiThread(() -> {
                if (name == null) {
                    toast("Telegram не принял токен или нет связи");
                } else {
                    sheet("Привязка чата",
                            "Открой @" + name + " в Telegram, нажми Start и вернись сюда.",
                            null, "Привязать", () -> linkChat(token, name));
                }
            });
        }).start();
    }

    private void linkChat(final String token, final String name) {
        toast("Привязываю…");
        new Thread(() -> {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("limit", "20");
            JSONObject upd = Bot.api(token, "getUpdates", p);
            String chat = "";
            if (upd != null) {
                JSONArray arr = upd.optJSONArray("result");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject msg = arr.optJSONObject(i) == null ? null : arr.optJSONObject(i).optJSONObject("message");
                        if (msg == null) continue;
                        JSONObject ch = msg.optJSONObject("chat");
                        if (ch != null && ch.has("id")) chat = String.valueOf(ch.optLong("id"));
                    }
                }
            }
            if (chat.isEmpty()) {
                runOnUiThread(() -> toast("Не вижу сообщений боту. Нажми Start в боте и повтори"));
                return;
            }
            Map<String, String> t = new LinkedHashMap<>();
            t.put("chat_id", chat);
            t.put("text", "Hope подключён. Находки буду слать сюда.");
            JSONObject r = Bot.api(token, "sendMessage", t);
            if (r == null || !r.optBoolean("ok")) {
                runOnUiThread(() -> toast("Не получилось отправить тестовое сообщение"));
                return;
            }
            bot.connect(token, chat, name);
            runOnUiThread(() -> {
                refreshBot();
                toast("Бот подключён: @" + name);
            });
        }).start();
    }
}
