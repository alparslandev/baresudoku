package com.baresudoku;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;

public class MainActivity extends Activity implements Runnable {
    static final Game game = new Game();
    static final Sudoku engine = new Sudoku();
    static SharedPreferences prefs;
    static BoardView current;
    static volatile int pendingLevel = -1;
    static volatile String pendingDaily = "";
    static volatile int pendingKind = -1;
    static volatile Variant variant;
    static int menuKind = -1;
    static String dailyDate = "";
    static boolean dailyMenu;
    static boolean showTimer = true;
    static boolean recorded;

    BoardView view;
    boolean resumed;

    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (prefs == null) {
            prefs = getApplicationContext().getSharedPreferences("s", MODE_PRIVATE);
            if (restoreVariant()) game.decode(prefs.getString("g", null));
            if (!game.active) {
                variant = null;
                game.setShape(null);
            }
            dailyDate = game.active ? prefs.getString("dd", "") : "";
            dailyMenu = !dailyDate.isEmpty();
            menuKind = variant == null ? -1 : variant.shape.kind;
            showTimer = !"0".equals(prefs.getString("t", "1"));
            recorded = game.solved;
            if (game.active && variant == null) rateSaved();
        }
        view = new BoardView(this);
        current = view;
        setContentView(view);
    }

    protected void onResume() {
        super.onResume();
        resumed = true;
        view.shown();
    }

    protected void onPause() {
        super.onPause();
        resumed = false;
        view.hidden();
        save();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        view.focusChanged(hasFocus);
    }

    protected void onDestroy() {
        super.onDestroy();
        if (current == view) current = null;
    }

    static void save() {
        synchronized (game) {
            Variant v = variant;
            prefs.edit().putString("g", game.encode(SystemClock.elapsedRealtime())).putString("dd", dailyDate)
                .putString("vk", v == null ? "" : Integer.toString(v.shape.kind))
                .putString("vc", v == null || v.shape.kind != Variant.KIND_KILLER ? "" : cageText(v.shape)).apply();
        }
    }

    static String cageText(Variant.Shape shape) {
        StringBuilder sb = new StringBuilder(400);
        for (int c = 0; c < shape.size; c++) {
            if (c > 0) sb.append(',');
            sb.append(shape.cageOf[c]);
        }
        sb.append(';');
        for (int k = 0; k < shape.cageCount; k++) {
            if (k > 0) sb.append(',');
            sb.append(shape.cageSum[k]);
        }
        return sb.toString();
    }

    static boolean readCages(Variant.Shape shape, String text) {
        try {
            String[] parts = text.split(";", -1);
            if (parts.length != 2) return false;
            String[] cells = parts[0].split(",");
            String[] sums = parts[1].split(",");
            if (cells.length != shape.size || sums.length < 1 || sums.length > Variant.MAX_CAGES) return false;
            int[] cageOf = new int[shape.size];
            int[] cageSum = new int[sums.length];
            int[] members = new int[sums.length];
            for (int k = 0; k < sums.length; k++) {
                cageSum[k] = Integer.parseInt(sums[k]);
                if (cageSum[k] < 1 || cageSum[k] > 45) return false;
            }
            for (int c = 0; c < shape.size; c++) {
                cageOf[c] = Integer.parseInt(cells[c]);
                if (cageOf[c] < -1 || cageOf[c] >= sums.length) return false;
                if (cageOf[c] >= 0 && ++members[cageOf[c]] > 9) return false;
            }
            for (int m : members) if (m == 0) return false;
            shape.setCages(cageOf, cageSum, sums.length);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static boolean restoreVariant() {
        variant = null;
        game.setShape(null);
        String kindText = prefs.getString("vk", "");
        if (kindText.isEmpty()) return true;
        int kind;
        try {
            kind = Integer.parseInt(kindText);
        } catch (NumberFormatException e) {
            return false;
        }
        if (kind < 0 || kind > Variant.KIND_MINI) return false;
        Variant v = new Variant(kind);
        if (kind == Variant.KIND_KILLER && !readCages(v.shape, prefs.getString("vc", ""))) return false;
        variant = v;
        game.setShape(v.shape);
        return true;
    }

    static String today() {
        return dateKey(Calendar.getInstance());
    }

    static String dateKey(Calendar c) {
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        return y + (m < 10 ? "-0" : "-") + m + (d < 10 ? "-0" : "-") + d;
    }

    static Calendar parseDate(String day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(day.substring(0, 4)), Integer.parseInt(day.substring(5, 7)) - 1, Integer.parseInt(day.substring(8, 10)));
        return c;
    }

    static int dailySeed(String day, int level) {
        int ymd = Integer.parseInt(day.substring(0, 4)) * 10000 + Integer.parseInt(day.substring(5, 7)) * 100 + Integer.parseInt(day.substring(8, 10));
        return ymd * 8 + level + 1;
    }

    static int[] stats() {
        int[] a = new int[Sudoku.LEVELS * 4];
        String[] parts = prefs.getString("st", "").split(",");
        for (int i = 0; i < a.length && i < parts.length; i++) {
            try {
                a[i] = Math.max(0, Integer.parseInt(parts[i]));
            } catch (NumberFormatException e) {
                a[i] = 0;
            }
        }
        return a;
    }

    static void storeStats(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(a[i]);
        }
        prefs.edit().putString("st", sb.toString()).apply();
    }

    static void recordStart(int level) {
        int[] a = stats();
        a[level * 4]++;
        storeStats(a);
    }

    static void recordSolved(int level, int seconds, String day) {
        int[] a = stats();
        a[level * 4 + 1]++;
        a[level * 4 + 2] += seconds;
        if (a[level * 4 + 3] == 0 || seconds < a[level * 4 + 3]) a[level * 4 + 3] = seconds;
        storeStats(a);
        if (!day.isEmpty()) logDaily(day);
    }

    static ArrayList<String> dailyLog() {
        ArrayList<String> days = new ArrayList<String>();
        for (String day : prefs.getString("dl", "").split(";")) if (day.length() == 10) days.add(day);
        return days;
    }

    static void logDaily(String day) {
        ArrayList<String> days = dailyLog();
        if (days.contains(day)) return;
        days.add(day);
        Collections.sort(days);
        while (days.size() > 400) days.remove(0);
        StringBuilder sb = new StringBuilder();
        for (String d : days) {
            if (sb.length() > 0) sb.append(';');
            sb.append(d);
        }
        prefs.edit().putString("dl", sb.toString()).apply();
    }

    static int streak() {
        ArrayList<String> days = dailyLog();
        Calendar c = Calendar.getInstance();
        if (!days.contains(dateKey(c))) c.add(Calendar.DAY_OF_MONTH, -1);
        int n = 0;
        while (days.contains(dateKey(c))) {
            n++;
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        return n;
    }

    void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            return;
        }
    }

    void share(String text) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, null));
        } catch (RuntimeException e) {
            return;
        }
    }

    public void run() {
        int level = pendingLevel;
        int kind = pendingKind;
        String day = pendingDaily;
        if (kind >= 0) {
            Variant worker = new Variant(kind);
            int[] puzzle = worker.generate(level);
            synchronized (game) {
                game.setShape(worker.shape);
                game.start(puzzle, worker.solution, level);
                game.rating = 0;
                variant = worker;
            }
        } else {
            Sudoku worker = new Sudoku();
            if (!day.isEmpty()) worker.seed(dailySeed(day, level));
            int[] puzzle = worker.generate(level);
            synchronized (game) {
                game.setShape(null);
                game.start(puzzle, worker.solution, level);
                game.rating = worker.rating;
                variant = null;
            }
        }
        dailyDate = day;
        recorded = false;
        if (kind < 0) recordStart(level);
        save();
        pendingLevel = -1;
        BoardView v = current;
        if (v != null) v.postInvalidate();
    }

    static void rateSaved() {
        new Thread(new Runnable() {
            public void run() {
                int[] given;
                synchronized (game) {
                    given = game.given.clone();
                }
                int rating = new Sudoku().rate(given);
                synchronized (game) {
                    if (game.active && Arrays.equals(given, game.given)) game.rating = rating;
                }
                BoardView v = current;
                if (v != null) v.postInvalidate();
            }
        }).start();
    }

    void generate(int level, boolean daily, int kind) {
        if (pendingLevel >= 0) return;
        pendingDaily = daily && kind < 0 ? today() : "";
        pendingKind = kind;
        pendingLevel = level;
        new Thread(this).start();
    }

    public void onBackPressed() {
        if (!view.back()) super.onBackPressed();
    }
}
