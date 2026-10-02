package com.baresudoku;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeProvider;
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
    static final int S_RESTART = 29;
    static final int S_ROWLABEL = 30;
    static final int S_COLLABEL = 31;
    static final int S_LEFT = 32;
    static final int S_TECH_EXTRA = 33;
    static final int S_MASTER = S_TECH_EXTRA + Sudoku.TECH_COUNT - 7;
    static final String[] EN = {"Easy", "Medium", "Hard", "Expert", "Undo", "Erase", "Notes", "Fill notes", "Hint",
        "New game", "Show mistakes", "On", "Off", "Cancel", "Solved!", "Preparing…", "This digit is wrong",
        "Only one candidate here: #", "Only place for # in this row", "Only place for # in this column",
        "Only place for # in this box", "Tap hint again to place it", "Locked candidates", "Pair or triple",
        "X-Wing", "Y-Wing", "Swordfish", "XYZ-Wing", "Bare Sudoku", "Restart", "Row", "Column", "# left",
        "Skyscraper", "2-String Kite", "W-Wing", "Unique Rectangle",
        "Naked Quad", "Hidden Quad", "Jellyfish", "Finned X-Wing", "Finned Swordfish", "Finned Jellyfish",
        "Empty Rectangle", "Remote Pair", "WXYZ-Wing", "Unique Rectangle Type 2", "Unique Rectangle Type 3",
        "Unique Rectangle Type 4", "Unique Rectangle Type 5", "Unique Rectangle Type 6", "Hidden Rectangle", "BUG+1",
        "X-Chain", "XY-Chain", "Continuous Nice Loop", "AIC", "Grouped AIC", "Sue de Coq", "ALS-XZ", "ALS-XY-Wing",
        "Death Blossom", "ALS Chain",
        "Nishio Forcing Chain", "Cell Forcing Chain", "Unit Forcing Chain", "Dynamic Forcing Net",
        "Nested Forcing Net", "Sashimi X-Wing", "Sashimi Swordfish", "Sashimi Jellyfish", "Master"};
    static final String[] TR = {"Kolay", "Orta", "Zor", "Uzman", "Geri al", "Sil", "Not", "Notları doldur", "İpucu",
        "Yeni oyun", "Yanlışları göster", "Açık", "Kapalı", "Vazgeç", "Tebrikler!", "Hazırlanıyor…", "Bu rakam yanlış",
        "Bu hücrede tek aday: #", "Bu satırda # için tek yer", "Bu sütunda # için tek yer",
        "Bu kutuda # için tek yer", "Yerleştirmek için ipucuna tekrar bas", "Kilitli adaylar", "Çift veya üçlü",
        "X-Wing", "Y-Wing", "Swordfish", "XYZ-Wing", "Bare Sudoku", "Baştan başla", "Satır", "Sütun", "# kaldı",
        "Skyscraper", "2-String Kite", "W-Wing", "Unique Rectangle",
        "Naked Quad", "Hidden Quad", "Jellyfish", "Finned X-Wing", "Finned Swordfish", "Finned Jellyfish",
        "Empty Rectangle", "Remote Pair", "WXYZ-Wing", "Unique Rectangle Type 2", "Unique Rectangle Type 3",
        "Unique Rectangle Type 4", "Unique Rectangle Type 5", "Unique Rectangle Type 6", "Hidden Rectangle", "BUG+1",
        "X-Chain", "XY-Chain", "Continuous Nice Loop", "AIC", "Grouped AIC", "Sue de Coq", "ALS-XZ", "ALS-XY-Wing",
        "Death Blossom", "ALS Chain",
        "Nishio Forcing Chain", "Cell Forcing Chain", "Unit Forcing Chain", "Dynamic Forcing Net",
        "Nested Forcing Net", "Sashimi X-Wing", "Sashimi Swordfish", "Sashimi Jellyfish", "Usta"};
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
    BoardNodes nodes;
    int hovered = -1;
    String axLastMessage;

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
        menuOpen = !game.active && MainActivity.pendingLevel < 0;
        wasGenerating = MainActivity.pendingLevel >= 0;
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
        float pad = Math.min(cw, ch) * 0.03f;
        landscape = cw > ch;
        if (landscape) {
            boardSize = Math.min(ch - 2 * pad, cw * 0.55f);
            boardX = left + pad;
            boardY = top + (ch - boardSize) / 2;
            float px = boardX + boardSize + pad * 2;
            float pw = left + cw - pad - px;
            topX = px;
            topW = pw;
            topY = boardY;
            topH = boardSize * 0.11f;
            msgX = px;
            msgW = pw;
            msgY = topY + topH + pad;
            msgH = boardSize * 0.11f;
            toolW = pw / 5;
            toolH = boardSize * 0.2f;
            toolY = msgY + msgH + pad;
            for (int i = 0; i < 5; i++) toolX[i] = px + i * toolW;
            float ky = toolY + toolH + pad;
            keyW = pw / 3;
            keyH = (boardY + boardSize - ky) / 3;
            for (int i = 0; i < 9; i++) {
                keyX[i] = px + (i % 3) * keyW;
                keyY[i] = ky + (i / 3) * keyH;
            }
        } else {
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
        boolean generating = MainActivity.pendingLevel >= 0;
        if (wasGenerating && !generating) {
            wasGenerating = false;
            game.resume(SystemClock.elapsedRealtime());
            run();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }
        drawTop(c, generating);
        if (generating) {
            drawText(c, text[S_PREPARING], boardX + boardSize / 2, boardY + boardSize / 2, cell * 0.55f, cMuted, Typeface.DEFAULT);
        } else if (game.active) {
            drawBoard(c);
        }
        drawMessage(c);
        drawTools(c);
        drawKeys(c);
        if (overlay()) drawMenu(c);
    }

    private void drawBoard(Canvas c) {
        int sel = game.selected;
        int selValue = sel >= 0 && game.value[sel] != 0 ? game.value[sel] : game.sticky;
        int selBit = selValue == 0 ? 0 : Sudoku.bit(selValue);
        for (int i = 0; i < 81; i++) {
            int color = 0;
            if (i == sel) color = cSelected;
            else if (selValue != 0 && (game.value[i] == selValue || (game.value[i] == 0 && (game.notes[i] & selBit) != 0))) color = cSame;
            else if (sel >= 0 && Sudoku.sees(i, sel)) color = cUnit;
            if (color == 0) continue;
            float x = boardX + Sudoku.COL[i] * cell;
            float y = boardY + Sudoku.ROW[i] * cell;
            fillRect(c, x, y, x + cell, y + cell, color, 0);
        }
        for (int i = 0; i < 81; i++) {
            float cx = boardX + (Sudoku.COL[i] + 0.5f) * cell;
            float cy = boardY + (Sudoku.ROW[i] + 0.5f) * cell;
            int v = game.value[i];
            if (v != 0) {
                boolean given = game.given[i] != 0;
                int color = game.conflict(i) || game.wrong(i) ? cWrong : given ? cGiven : cEntered;
                drawText(c, DIGITS[v], cx, cy, cell * 0.62f, color, given ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            } else if (game.notes[i] != 0) {
                float x0 = boardX + Sudoku.COL[i] * cell;
                float y0 = boardY + Sudoku.ROW[i] * cell;
                for (int d = 1; d <= 9; d++) {
                    if ((game.notes[i] & Sudoku.bit(d)) == 0) continue;
                    float nx = x0 + ((d - 1) % 3 + 0.5f) * cell / 3;
                    float ny = y0 + ((d - 1) / 3 + 0.5f) * cell / 3;
                    boolean same = d == selValue;
                    drawText(c, DIGITS[d], nx, ny, cell * 0.28f, same ? cEntered : cNote, same ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                }
            }
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
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
            boolean on = d == game.sticky;
            fillRect(c, x + inset, y + inset, x + keyW - inset, y + keyH - inset, on ? cAccent : cKey, keyW * 0.15f);
            int left = game.active ? Math.max(0, game.remaining(d)) : 9;
            drawText(c, DIGITS[d], x + keyW / 2, y + keyH * 0.42f, keyH * 0.5f, on ? cBg : left > 0 ? cKeyText : cMuted, Typeface.DEFAULT);
            if (left > 0) drawText(c, DIGITS[left], x + keyW / 2, y + keyH * 0.8f, keyH * 0.2f, on ? cBg : cMuted, Typeface.DEFAULT);
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
        float x = e.getX();
        float y = e.getY();
        if (overlay()) {
            if (action == MotionEvent.ACTION_DOWN) {
                downTarget = menuTarget(x, y);
            } else if (action == MotionEvent.ACTION_UP) {
                if (menuTarget(x, y) == downTarget) menuAction(downTarget);
                downTarget = -1;
            }
            return true;
        }
        if (MainActivity.pendingLevel >= 0) return true;
        int t = target(x, y);
        if (action == MotionEvent.ACTION_DOWN) {
            downTarget = t;
            if (t >= 0 && t < 81) finish(game.tap(t));
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (t >= 0 && t < 81 && t != downTarget) {
                downTarget = -1;
                if (game.active && game.selected != t) {
                    game.select(t);
                    invalidate();
                }
            }
        } else {
            if (action == MotionEvent.ACTION_UP && t >= 100 && t == downTarget) act(t);
            downTarget = -1;
        }
        return true;
    }

    private void act(int t) {
        boolean changed = false;
        if (t >= 100 && t < 109) changed = game.key(t - 99);
        else if (t == 200) changed = game.undo();
        else if (t == 201) changed = game.erase();
        else if (t == 202) game.noteMode = !game.noteMode;
        else if (t == 203) changed = game.fillNotes();
        else if (t == 204) changed = game.hint(MainActivity.engine);
        else if (t == 300) {
            openMenu();
            return;
        }
        finish(changed || t == 202);
    }

    private void finish(boolean changed) {
        if (game.solved) {
            game.pause(SystemClock.elapsedRealtime());
            removeCallbacks(this);
        }
        if (changed) MainActivity.save();
        invalidate();
        String message = game.active && game.hintActive() ? hintMessage() : null;
        if (message != null && !message.equals(axLastMessage)) announceForAccessibility(message);
        axLastMessage = message;
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
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

    private float fit(String s, float size, float maxWidth) {
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        float w = paint.measureText(s);
        return w > maxWidth ? size * maxWidth / w : size;
    }

    private String hintMessage() {
        if (game.hintKind == Game.HINT_WRONG) return text[S_WRONG];
        int u = game.hintUnit;
        String s = text[u == 0 ? S_ROW : u == 1 ? S_COL : u == 2 ? S_BOX : S_NAKED].replace("#", DIGITS[game.hintDigit]);
        int t = game.hintTech;
        if (t > 0) s = s + " (" + text[t < 7 ? S_TECH + t - 1 : S_TECH_EXTRA + t - 7] + ")";
        return s;
    }

    private void drawMessage(Canvas c) {
        if (!game.active || !game.hintActive()) return;
        String first = hintMessage();
        float cx = msgX + msgW / 2;
        float size = msgH * 0.36f;
        if (game.hintKind != Game.HINT_PLACE) {
            drawText(c, first, cx, msgY + msgH / 2, fit(first, size, msgW * 0.96f), cWrong, Typeface.DEFAULT);
            return;
        }
        String second = text[S_AGAIN];
        drawText(c, first, cx, msgY + msgH * 0.3f, fit(first, size, msgW * 0.96f), cAccent, Typeface.DEFAULT);
        drawText(c, second, cx, msgY + msgH * 0.72f, fit(second, size * 0.9f, msgW * 0.96f), cMuted, Typeface.DEFAULT);
    }

    private void drawIcon(Canvas c, int kind, float cx, float cy, float s, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.18f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        if (kind == 0) {
            path.moveTo(cx + s * 0.65f, cy);
            path.lineTo(cx - s * 0.6f, cy);
            path.moveTo(cx - s * 0.1f, cy - s * 0.5f);
            path.lineTo(cx - s * 0.6f, cy);
            path.lineTo(cx - s * 0.1f, cy + s * 0.5f);
        } else if (kind == 1) {
            path.moveTo(cx + s * 0.686f, cy - s * 0.234f);
            path.lineTo(cx + s * 0.234f, cy - s * 0.686f);
            path.lineTo(cx - s * 0.686f, cy + s * 0.234f);
            path.lineTo(cx - s * 0.234f, cy + s * 0.686f);
            path.close();
            path.moveTo(cx + s * 0.134f, cy + s * 0.318f);
            path.lineTo(cx - s * 0.318f, cy - s * 0.134f);
        } else if (kind == 2) {
            paint.setStrokeWidth(s * 0.3f);
            path.moveTo(cx - s * 0.3f, cy + s * 0.3f);
            path.lineTo(cx + s * 0.5f, cy - s * 0.5f);
            c.drawPath(path, paint);
            path.reset();
            paint.setStrokeWidth(s * 0.1f);
            path.moveTo(cx - s * 0.3f, cy + s * 0.3f);
            path.lineTo(cx - s * 0.62f, cy + s * 0.62f);
        } else if (kind == 3) {
            paint.setStyle(Paint.Style.FILL);
            for (int k = 0; k < 9; k++) {
                c.drawCircle(cx + (k % 3 - 1) * s * 0.5f, cy + (k / 3 - 1) * s * 0.5f, s * 0.12f, paint);
            }
            return;
        } else {
            c.drawCircle(cx, cy - s * 0.15f, s * 0.45f, paint);
            path.moveTo(cx - s * 0.22f, cy + s * 0.48f);
            path.lineTo(cx + s * 0.22f, cy + s * 0.48f);
            path.moveTo(cx - s * 0.15f, cy + s * 0.7f);
            path.lineTo(cx + s * 0.15f, cy + s * 0.7f);
        }
        c.drawPath(path, paint);
    }

    private void drawTools(Canvas c) {
        float inset = toolW * 0.06f;
        for (int i = 0; i < 5; i++) {
            float x = toolX[i];
            boolean on = (i == 2 && game.noteMode) || (i == 4 && game.active && game.hintActive() && game.hintKind == Game.HINT_PLACE);
            boolean enabled = game.active && !game.solved && (i != 0 || !game.history.isEmpty());
            fillRect(c, x + inset, toolY + inset, x + toolW - inset, toolY + toolH - inset, on ? cSame : cKey, toolW * 0.15f);
            int color = !enabled ? cMuted : on ? cAccent : cKeyText;
            drawIcon(c, i, x + toolW / 2, toolY + toolH * 0.38f, toolH * 0.3f, color);
            String label = text[S_UNDO + i];
            drawText(c, label, x + toolW / 2, toolY + toolH * 0.78f, fit(label, toolH * 0.17f, toolW * 0.88f), color, Typeface.DEFAULT);
        }
    }

    boolean wasGenerating;
    float menuX;
    float menuW;
    float menuTop;
    float menuRowH;
    float menuTitleH;
    int menuRows;

    private boolean overlay() {
        return menuOpen || (game.active && game.solved);
    }

    private boolean cancellable() {
        return game.active && !game.solved;
    }

    private float width(String s, float size, Typeface face) {
        paint.setTextSize(size);
        paint.setTypeface(face);
        return paint.measureText(s);
    }

    private void drawTop(Canvas c, boolean generating) {
        int level = generating ? MainActivity.pendingLevel : game.active ? game.level : -1;
        float size = topH * 0.42f;
        float cy = topY + topH / 2;
        if (level >= 0) {
            String s = generating ? levelName(level) : levelText();
            drawText(c, s, topX + topH * 0.2f + width(s, size, Typeface.DEFAULT_BOLD) / 2, cy, size, cKeyText, Typeface.DEFAULT_BOLD);
        }
        if (game.active && !generating) {
            drawText(c, clock(game.time(SystemClock.elapsedRealtime())), topX + topW / 2, cy, size, cMuted, Typeface.MONOSPACE);
        }
        float mx = topX + topW - topH * 0.45f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(topH * 0.07f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(cKeyText);
        for (int k = -1; k <= 1; k++) {
            c.drawLine(mx - topH * 0.22f, cy + k * topH * 0.17f, mx + topH * 0.22f, cy + k * topH * 0.17f, paint);
        }
    }

    private void layoutMenu(int w, int h) {
        float base = Math.min(w - insetL - insetR, h - insetT - insetB);
        menuW = base * 0.82f;
        menuRowH = base * 0.095f;
        boolean solved = game.active && game.solved;
        menuTitleH = menuRowH * (solved ? 2.2f : 1.6f);
        menuRows = cancellable() ? 8 : 6;
        float total = menuTitleH + menuRows * menuRowH + menuRowH * 0.4f;
        menuX = insetL + (w - insetL - insetR - menuW) / 2;
        menuTop = insetT + (h - insetT - insetB - total) / 2;
    }

    private float menuRowY(int row) {
        return menuTop + menuTitleH + row * menuRowH;
    }

    private void drawMenu(Canvas c) {
        c.drawColor(cDim);
        layoutMenu(getWidth(), getHeight());
        float bottom = menuRowY(menuRows) + menuRowH * 0.4f;
        fillRect(c, menuX, menuTop, menuX + menuW, bottom, cPanel, menuRowH * 0.3f);
        boolean solved = game.active && game.solved;
        float cx = menuX + menuW / 2;
        float y = menuTop + menuRowH * 0.7f;
        drawText(c, text[solved ? S_SOLVED : S_TITLE], cx, y, menuRowH * 0.5f, cKeyText, Typeface.DEFAULT_BOLD);
        y += menuRowH * 0.6f;
        if (solved) {
            drawText(c, levelText() + "  " + clock(game.time(0)), cx, y, menuRowH * 0.38f, cAccent, Typeface.DEFAULT);
            y += menuRowH * 0.6f;
        }
        drawText(c, text[S_NEW], cx, y, menuRowH * 0.34f, cMuted, Typeface.DEFAULT);
        float side = menuRowH * 0.4f;
        float inset = menuRowH * 0.08f;
        for (int row = 0; row < menuRows; row++) {
            float ry = menuRowY(row);
            String label = menuLabel(row);
            fillRect(c, menuX + side, ry + inset, menuX + menuW - side, ry + menuRowH - inset, cKey, menuRowH * 0.25f);
            drawText(c, label, cx, ry + menuRowH / 2, fit(label, menuRowH * 0.4f, menuW * 0.8f), cKeyText, Typeface.DEFAULT);
        }
    }

    private int menuTarget(float x, float y) {
        layoutMenu(getWidth(), getHeight());
        float bottom = menuRowY(menuRows) + menuRowH * 0.4f;
        if (x < menuX || x > menuX + menuW || y < menuTop || y > bottom) return 499;
        for (int row = 0; row < menuRows; row++) {
            float ry = menuRowY(row);
            if (y >= ry && y < ry + menuRowH) return 400 + row;
        }
        return -1;
    }

    private void menuAction(int t) {
        if (t >= 400 && t < 405) {
            startGame(t - 400);
        } else if (t == 405) {
            game.showErrors = !game.showErrors;
            MainActivity.save();
            invalidate();
        } else if (t == 406 && cancellable()) {
            game.restart();
            MainActivity.save();
            closeMenu();
        } else if ((t == 407 || t == 499) && cancellable()) {
            closeMenu();
        }
    }

    private void startGame(int level) {
        if (MainActivity.pendingLevel >= 0) return;
        menuOpen = false;
        game.pause(SystemClock.elapsedRealtime());
        removeCallbacks(this);
        wasGenerating = true;
        host.generate(level);
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private void openMenu() {
        menuOpen = true;
        game.pause(SystemClock.elapsedRealtime());
        removeCallbacks(this);
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private void closeMenu() {
        menuOpen = false;
        game.resume(SystemClock.elapsedRealtime());
        run();
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private String levelName(int level) {
        return text[level < 4 ? level : S_MASTER];
    }

    private String levelText() {
        String name = levelName(game.level);
        return game.rating > 0 ? name + " " + String.format(Locale.getDefault(), "%.1f", game.rating / 10.0) : name;
    }

    private String menuLabel(int row) {
        if (row < 5) return levelName(row);
        if (row == 5) return text[S_ERRORS] + ": " + text[game.showErrors ? S_ON : S_OFF];
        return text[row == 6 ? S_RESTART : S_CANCEL];
    }

    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (nodes == null) nodes = new BoardNodes(this);
        return nodes;
    }

    public boolean dispatchHoverEvent(MotionEvent e) {
        AccessibilityManager manager = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (!manager.isTouchExplorationEnabled()) return super.dispatchHoverEvent(e);
        int t = e.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT ? -1 : axTargetAt(e.getX(), e.getY());
        if (t != hovered) {
            if (hovered >= 0) axEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
            if (t >= 0) axEvent(t, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
            hovered = t;
        }
        return true;
    }

    int axTargetAt(float x, float y) {
        if (MainActivity.pendingLevel >= 0) return -1;
        if (overlay()) {
            int t = menuTarget(x, y);
            return t >= 400 && t < 400 + menuRows ? t : -1;
        }
        return target(x, y);
    }

    void axEvent(int id, int type) {
        if (getParent() == null) return;
        AccessibilityEvent event = AccessibilityEvent.obtain(type);
        event.setSource(this, id);
        event.setPackageName(getContext().getPackageName());
        event.setClassName(axClass(id));
        event.getText().add(axLabel(id));
        getParent().requestSendAccessibilityEvent(this, event);
    }

    int[] axTargets() {
        if (MainActivity.pendingLevel >= 0) return new int[] {420};
        if (overlay()) {
            layoutMenu(getWidth(), getHeight());
            int[] ids = new int[menuRows + 1];
            ids[0] = 410;
            for (int row = 0; row < menuRows; row++) ids[row + 1] = 400 + row;
            return ids;
        }
        boolean hint = game.active && game.hintActive();
        int[] ids = new int[hint ? 98 : 97];
        int n = 0;
        ids[n++] = 301;
        ids[n++] = 300;
        if (hint) ids[n++] = 302;
        for (int i = 0; i < 81; i++) ids[n++] = i;
        for (int d = 1; d <= 9; d++) ids[n++] = 99 + d;
        for (int i = 0; i < 5; i++) ids[n++] = 200 + i;
        return ids;
    }

    String axClass(int id) {
        return id == 301 || id == 302 || id == 410 || id == 420 ? "android.widget.TextView" : "android.widget.Button";
    }

    String axLabel(int id) {
        if (id == 420) return text[S_PREPARING];
        if (id == 410) {
            boolean solved = game.active && game.solved;
            String title = solved ? text[S_SOLVED] + " " + levelText() + " " + clock(game.time(0)) : text[S_TITLE];
            return title + ". " + text[S_NEW];
        }
        if (id >= 400) return menuLabel(id - 400);
        if (id == 301) return game.active ? levelText() + ", " + clock(game.time(SystemClock.elapsedRealtime())) : "";
        if (id == 300) return text[S_NEW];
        if (id == 302) {
            String message = hintMessage();
            return game.hintKind == Game.HINT_PLACE ? message + ". " + text[S_AGAIN] : message;
        }
        if (id >= 200) return text[S_UNDO + id - 200];
        if (id >= 100) {
            int left = game.active ? Math.max(0, game.remaining(id - 99)) : 9;
            return DIGITS[id - 99] + ", " + text[S_LEFT].replace("#", Integer.toString(left));
        }
        String label = text[S_ROWLABEL] + " " + (Sudoku.ROW[id] + 1) + ", " + text[S_COLLABEL] + " " + (Sudoku.COL[id] + 1);
        if (!game.active) return label;
        int v = game.value[id];
        if (v != 0) return label + ", " + DIGITS[v] + (game.conflict(id) || game.wrong(id) ? ", " + text[S_WRONG] : "");
        if (game.notes[id] == 0) return label;
        StringBuilder notes = new StringBuilder(label).append(", ").append(text[S_NOTE]);
        for (int d = 1; d <= 9; d++) if ((game.notes[id] & Sudoku.bit(d)) != 0) notes.append(' ').append(d);
        return notes.toString();
    }

    boolean axEnabled(int id) {
        boolean playable = game.active && !game.solved;
        if (id < 81) return playable;
        if (id < 109) return playable && game.remaining(id - 99) > 0;
        if (id < 205) return playable && (id != 200 || !game.history.isEmpty());
        return true;
    }

    boolean axSelected(int id) {
        if (id < 81) return id == game.selected;
        if (id < 109) return id - 99 == game.sticky;
        if (id == 202) return game.noteMode;
        if (id == 204) return game.active && game.hintActive() && game.hintKind == Game.HINT_PLACE;
        return false;
    }

    Rect axRect(int id) {
        if (id == 420) return new Rect(0, 0, getWidth(), getHeight());
        if (id == 410) return new Rect((int) menuX, (int) menuTop, (int) (menuX + menuW), (int) (menuTop + menuTitleH));
        if (id >= 400) {
            float ry = menuRowY(id - 400);
            return new Rect((int) menuX, (int) ry, (int) (menuX + menuW), (int) (ry + menuRowH));
        }
        if (id == 301) return new Rect((int) topX, (int) topY, (int) (topX + topW - topH * 0.9f), (int) (topY + topH));
        if (id == 300) return new Rect((int) (topX + topW - topH * 0.9f), (int) topY, (int) (topX + topW), (int) (topY + topH));
        if (id == 302) return new Rect((int) msgX, (int) msgY, (int) (msgX + msgW), (int) (msgY + msgH));
        if (id >= 200) return new Rect((int) toolX[id - 200], (int) toolY, (int) (toolX[id - 200] + toolW), (int) (toolY + toolH));
        if (id >= 100) return new Rect((int) keyX[id - 100], (int) keyY[id - 100], (int) (keyX[id - 100] + keyW), (int) (keyY[id - 100] + keyH));
        float x = boardX + Sudoku.COL[id] * cell;
        float y = boardY + Sudoku.ROW[id] * cell;
        return new Rect((int) x, (int) y, (int) (x + cell), (int) (y + cell));
    }

    void activate(int t) {
        if (MainActivity.pendingLevel >= 0 || t < 0) return;
        if (overlay()) {
            if (t >= 400) menuAction(t);
            return;
        }
        if (t < 81) finish(game.tap(t));
        else if (t >= 100 && t <= 300) act(t);
    }

    boolean back() {
        if (menuOpen && cancellable()) {
            closeMenu();
            return true;
        }
        return false;
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
    }

    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        insetL = insets.getSystemWindowInsetLeft();
        insetT = insets.getSystemWindowInsetTop();
        insetR = insets.getSystemWindowInsetRight();
        insetB = insets.getSystemWindowInsetBottom();
        if (Build.VERSION.SDK_INT >= 28) applyCutout(insets);
        layout(getWidth(), getHeight());
        invalidate();
        return insets;
    }

    private void applyCutout(WindowInsets insets) {
        DisplayCutout cutout = insets.getDisplayCutout();
        if (cutout == null) return;
        insetL = Math.max(insetL, cutout.getSafeInsetLeft());
        insetT = Math.max(insetT, cutout.getSafeInsetTop());
        insetR = Math.max(insetR, cutout.getSafeInsetRight());
        insetB = Math.max(insetB, cutout.getSafeInsetBottom());
    }
}
