package com.baresudoku;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import java.util.Locale;

final class BoardView extends View implements Runnable {
    static final int S_UNDO = 4;
    static final int S_ERASE = 5;
    static final int S_NOTE = 6;
    static final int S_FILL = 7;
    static final int S_HINT = 8;
    static final int S_NEW = 9;
    static final int S_ERRORS = 10;
    static final int S_ON = 11;
    static final int S_OFF = 12;
    static final int S_CANCEL = 13;
    static final int S_SOLVED = 14;
    static final int S_PREPARING = 15;
    static final int S_WRONG = 16;
    static final int S_NAKED = 17;
    static final int S_ROW = 18;
    static final int S_COL = 19;
    static final int S_BOX = 20;
    static final int S_AGAIN = 21;
    static final int S_TECH = 22;
    static final int S_TITLE = 28;
    static final String[] EN = {"Easy", "Medium", "Hard", "Expert", "Undo", "Erase", "Notes", "Fill notes", "Hint",
        "New game", "Show mistakes", "On", "Off", "Cancel", "Solved!", "Preparing…", "This digit is wrong",
        "Only one candidate here: #", "Only place for # in this row", "Only place for # in this column",
        "Only place for # in this box", "Tap hint again to place it", "Locked candidates", "Pair or triple",
        "X-Wing", "Y-Wing", "Swordfish", "XYZ-Wing", "Bare Sudoku"};
    static final String[] TR = {"Kolay", "Orta", "Zor", "Uzman", "Geri al", "Sil", "Not", "Notları doldur", "İpucu",
        "Yeni oyun", "Yanlışları göster", "Açık", "Kapalı", "Vazgeç", "Tebrikler!", "Hazırlanıyor…", "Bu rakam yanlış",
        "Bu hücrede tek aday: #", "Bu satırda # için tek yer", "Bu sütunda # için tek yer",
        "Bu kutuda # için tek yer", "Yerleştirmek için ipucuna tekrar bas", "Kilitli adaylar", "Çift veya üçlü",
        "X-Wing", "Y-Wing", "Swordfish", "XYZ-Wing", "Bare Sudoku"};
    static final String[] DIGITS = {"", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

    final MainActivity host;
    final Game game;
    final String[] text;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final RectF rect = new RectF();
    final boolean dark;
    int cBg;
    int cLine;
    int cThick;
    int cGiven;
    int cEntered;
    int cWrong;
    int cNote;
    int cUnit;
    int cSame;
    int cSelected;
    int cKey;
    int cKeyText;
    int cMuted;
    int cPanel;
    int cDim;
    int cAccent;
    int insetL;
    int insetT;
    int insetR;
    int insetB;
    boolean landscape;
    float boardX;
    float boardY;
    float boardSize;
    float cell;
    float topX;
    float topY;
    float topW;
    float topH;
    float msgX;
    float msgY;
    float msgW;
    float msgH;
    final float[] toolX = new float[5];
    float toolY;
    float toolW;
    float toolH;
    final float[] keyX = new float[9];
    final float[] keyY = new float[9];
    float keyW;
    float keyH;
    boolean menuOpen;
    int downTarget = -1;

    BoardView(MainActivity activity) {
        super(activity);
        host = activity;
        game = MainActivity.game;
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        text = Locale.getDefault().getLanguage().equals("tr") ? TR : EN;
        if (dark) {
            cBg = 0xFF121212;
            cLine = 0xFF3A3A3A;
            cThick = 0xFFB0B0B0;
            cGiven = 0xFFF0F0F0;
            cEntered = 0xFF8AB4F8;
            cWrong = 0xFFF28B82;
            cNote = 0xFFA0A0A0;
            cUnit = 0xFF1E2430;
            cSame = 0xFF2B3A55;
            cSelected = 0xFF3B5A8A;
            cKey = 0xFF1F1F1F;
            cKeyText = 0xFFF0F0F0;
            cMuted = 0xFF707070;
            cPanel = 0xFF1F1F1F;
            cDim = 0x99000000;
            cAccent = 0xFF8AB4F8;
        } else {
            cBg = 0xFFFFFFFF;
            cLine = 0xFFD0D0D0;
            cThick = 0xFF303030;
            cGiven = 0xFF202124;
            cEntered = 0xFF1A73E8;
            cWrong = 0xFFD93025;
            cNote = 0xFF5F6368;
            cUnit = 0xFFEEF3FC;
            cSame = 0xFFD2E3FC;
            cSelected = 0xFFAECBFA;
            cKey = 0xFFF1F3F4;
            cKeyText = 0xFF202124;
            cMuted = 0xFF9AA0A6;
            cPanel = 0xFFFFFFFF;
            cDim = 0x66000000;
            cAccent = 0xFF1A73E8;
        }
    }

    static String clock(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        s %= 60;
        long h = m / 60;
        m %= 60;
        StringBuilder sb = new StringBuilder(8);
        if (h > 0) sb.append(h).append(':').append(m < 10 ? "0" : "");
        sb.append(m).append(':').append(s < 10 ? "0" : "").append(s);
        return sb.toString();
    }

    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        layout(w, h);
    }

    private void layout(int w, int h) {
        float left = insetL;
        float top = insetT;
        float cw = w - insetL - insetR;
        float ch = h - insetT - insetB;
        if (cw <= 0 || ch <= 0) return;
        landscape = false;
        float pad = Math.min(cw, ch) * 0.03f;
        topH = cw * 0.10f;
        msgH = cw * 0.09f;
        toolH = cw * 0.17f;
        keyH = cw * 0.15f;
        float fixed = topH + msgH + toolH + keyH;
        boardSize = Math.min(cw - 2 * pad, ch - 2 * pad - fixed - 4 * pad);
        float gap = Math.min((ch - 2 * pad - fixed - boardSize) / 4, pad * 3);
        if (gap < 0) gap = 0;
        float y = top + pad + (ch - 2 * pad - fixed - boardSize - 4 * gap) / 2;
        boardX = left + (cw - boardSize) / 2;
        topX = boardX;
        topW = boardSize;
        topY = y;
        boardY = topY + topH + gap;
        msgX = boardX;
        msgW = boardSize;
        msgY = boardY + boardSize + gap;
        toolY = msgY + msgH + gap;
        toolW = boardSize / 5;
        for (int i = 0; i < 5; i++) toolX[i] = boardX + i * toolW;
        keyW = boardSize / 9;
        for (int i = 0; i < 9; i++) {
            keyX[i] = boardX + i * keyW;
            keyY[i] = toolY + toolH + gap;
        }
        cell = boardSize / 9;
    }

    private void drawText(Canvas c, String s, float cx, float cy, float size, int color, Typeface face) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(face);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(s, cx, cy - (paint.ascent() + paint.descent()) / 2, paint);
    }

    private void fillRect(Canvas c, float l, float t, float r, float b, int color, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        rect.set(l, t, r, b);
        if (radius > 0) c.drawRoundRect(rect, radius, radius, paint);
        else c.drawRect(rect, paint);
    }

    protected void onDraw(Canvas c) {
        c.drawColor(cBg);
        if (game.active) drawBoard(c);
        drawKeys(c);
    }

    private void drawBoard(Canvas c) {
        for (int i = 0; i < 81; i++) {
            float cx = boardX + (Sudoku.COL[i] + 0.5f) * cell;
            float cy = boardY + (Sudoku.ROW[i] + 0.5f) * cell;
            int v = game.value[i];
            if (v != 0) {
                boolean given = game.given[i] != 0;
                drawText(c, DIGITS[v], cx, cy, cell * 0.62f, given ? cGiven : cEntered, given ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            } else if (game.notes[i] != 0) {
                float x0 = boardX + Sudoku.COL[i] * cell;
                float y0 = boardY + Sudoku.ROW[i] * cell;
                for (int d = 1; d <= 9; d++) {
                    if ((game.notes[i] & Sudoku.bit(d)) == 0) continue;
                    float nx = x0 + ((d - 1) % 3 + 0.5f) * cell / 3;
                    float ny = y0 + ((d - 1) / 3 + 0.5f) * cell / 3;
                    drawText(c, DIGITS[d], nx, ny, cell * 0.28f, cNote, Typeface.DEFAULT);
                }
            }
        }
        paint.setStyle(Paint.Style.STROKE);
        for (int k = 0; k <= 9; k++) {
            boolean thick = k % 3 == 0;
            paint.setColor(thick ? cThick : cLine);
            paint.setStrokeWidth(cell * (thick ? 0.06f : 0.02f));
            float p = k * cell;
            c.drawLine(boardX, boardY + p, boardX + boardSize, boardY + p, paint);
            c.drawLine(boardX + p, boardY, boardX + p, boardY + boardSize, paint);
        }
    }

    private void drawKeys(Canvas c) {
        float inset = keyW * 0.06f;
        for (int d = 1; d <= 9; d++) {
            float x = keyX[d - 1];
            float y = keyY[d - 1];
            fillRect(c, x + inset, y + inset, x + keyW - inset, y + keyH - inset, cKey, keyW * 0.15f);
            int left = game.active ? game.remaining(d) : 9;
            drawText(c, DIGITS[d], x + keyW / 2, y + keyH * 0.42f, keyH * 0.5f, left > 0 ? cKeyText : cMuted, Typeface.DEFAULT);
            if (left > 0) drawText(c, DIGITS[left], x + keyW / 2, y + keyH * 0.8f, keyH * 0.2f, cMuted, Typeface.DEFAULT);
        }
    }

    private int target(float x, float y) {
        if (x >= boardX && x < boardX + boardSize && y >= boardY && y < boardY + boardSize) {
            return (int) ((y - boardY) / cell) * 9 + (int) ((x - boardX) / cell);
        }
        for (int i = 0; i < 9; i++) {
            if (x >= keyX[i] && x < keyX[i] + keyW && y >= keyY[i] && y < keyY[i] + keyH) return 100 + i;
        }
        for (int i = 0; i < 5; i++) {
            if (x >= toolX[i] && x < toolX[i] + toolW && y >= toolY && y < toolY + toolH) return 200 + i;
        }
        if (x >= topX && x < topX + topW && y >= topY && y < topY + topH) return 300;
        return -1;
    }

    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int t = target(e.getX(), e.getY());
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            if (t >= 0 && t < 81) {
                if (action == MotionEvent.ACTION_DOWN) downTarget = -1;
                if (game.active && game.selected != t) {
                    game.select(t);
                    invalidate();
                }
            } else if (action == MotionEvent.ACTION_DOWN) {
                downTarget = t;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (t >= 100 && t == downTarget) act(t);
            downTarget = -1;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            downTarget = -1;
        }
        return true;
    }

    private void act(int t) {
        boolean changed = false;
        if (t >= 100 && t < 109) changed = game.enter(t - 99);
        if (game.solved) {
            game.pause(SystemClock.elapsedRealtime());
            removeCallbacks(this);
        }
        if (changed) MainActivity.save();
        invalidate();
    }

    void shown() {
        game.resume(SystemClock.elapsedRealtime());
        run();
    }

    void hidden() {
        game.pause(SystemClock.elapsedRealtime());
        removeCallbacks(this);
    }

    public void run() {
        removeCallbacks(this);
        if (game.running) {
            invalidate();
            postDelayed(this, 1000);
        }
    }
}
