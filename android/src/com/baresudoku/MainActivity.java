package com.baresudoku;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.Arrays;

public class MainActivity extends Activity implements Runnable {
    static final Game game = new Game();
    static final Sudoku engine = new Sudoku();
    static SharedPreferences prefs;
    static BoardView current;
    static volatile int pendingLevel = -1;

    BoardView view;

    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (prefs == null) {
            prefs = getApplicationContext().getSharedPreferences("s", MODE_PRIVATE);
            game.decode(prefs.getString("g", null));
            if (game.active) rateSaved();
        }
        view = new BoardView(this);
        current = view;
        setContentView(view);
    }

    protected void onResume() {
        super.onResume();
        view.shown();
    }

    protected void onPause() {
        super.onPause();
        view.hidden();
        save();
    }

    protected void onDestroy() {
        super.onDestroy();
        if (current == view) current = null;
    }

    static void save() {
        synchronized (game) {
            prefs.edit().putString("g", game.encode(SystemClock.elapsedRealtime())).apply();
        }
    }

    public void run() {
        Sudoku worker = new Sudoku();
        int level = pendingLevel;
        int[] puzzle = worker.generate(level);
        synchronized (game) {
            game.start(puzzle, worker.solution, level);
            game.rating = worker.rating;
        }
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

    void generate(int level) {
        if (pendingLevel >= 0) return;
        pendingLevel = level;
        new Thread(this).start();
    }

    public void onBackPressed() {
        if (!view.back()) super.onBackPressed();
    }
}
