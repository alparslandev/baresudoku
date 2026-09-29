package com.baresudoku;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;

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
        }
        save();
        pendingLevel = -1;
        BoardView v = current;
        if (v != null) v.postInvalidate();
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
