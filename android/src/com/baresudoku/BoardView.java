package com.baresudoku;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
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
    static final int S_DAILY = S_MASTER + 1;
    static final int S_SHARE = S_MASTER + 2;
    static final int S_PLAY = S_MASTER + 3;
    static final int S_STATS = S_MASTER + 4;
    static final int S_PLAYED = S_MASTER + 5;
    static final int S_BEST = S_MASTER + 6;
    static final int S_AVERAGE = S_MASTER + 7;
    static final int S_STREAK = S_MASTER + 8;
    static final int S_EXPLAIN = S_MASTER + 9;
    static final int S_TIMER = S_MASTER + 10;
    static final int S_COLOR = S_MASTER + 11;
    static final int S_CORNER = S_MASTER + 12;
    static final int S_VARIANTS = S_MASTER + 13;
    static final int S_DIAG = S_MASTER + 14;
    static final int S_REVEAL = S_MASTER + 15;
    static final int S_CAGE = S_MASTER + 16;
    static final int S_VARIANT_NAME = S_MASTER + 17;
    static final int[] VARIANT_ORDER = {Variant.KIND_KILLER, Variant.KIND_DIAGONAL, Variant.KIND_MINI};
    static final String[] VARIANT_PATHS = {"diagonal/", "killer/", "mini/"};
    static final int[] PALETTE_LIGHT = {0, 0xFFFFF1A8, 0xFFC8EFC4, 0xFFC4E0FF, 0xFFFFD0E6, 0xFFFFD6AC, 0xFFDDD0FF};
    static final int[] PALETTE_DARK = {0, 0xFF4D4418, 0xFF1F4526, 0xFF1D3A59, 0xFF55223D, 0xFF573616, 0xFF3A2C59};
    static final int TOOLS = 6;
    static final long IDLE_MS = 60000;
    static final String SITE = "https://baresudoku.com/";
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
        "Nested Forcing Net", "Sashimi X-Wing", "Sashimi Swordfish", "Sashimi Jellyfish", "Master",
        "Daily Sudoku", "Share", "Play Sudoku", "Statistics", "Solved", "Best time", "Average time", "Daily streak",
        "Explain", "Show timer", "Color", "Corner", "Variants", "Only place for # in this diagonal", "This cell is #",
        "Cage sum: #", "Diagonal Sudoku", "Killer Sudoku", "Mini Sudoku 6\u00d76"};
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
        "Nested Forcing Net", "Sashimi X-Wing", "Sashimi Swordfish", "Sashimi Jellyfish", "Usta",
        "Günlük Sudoku", "Paylaş", "Sudoku oyna", "İstatistik", "Çözülen", "En iyi süre", "Ortalama süre", "Günlük seri",
        "Açıkla", "Zamanlayıcıyı göster", "Renk", "Köşe", "Varyantlar", "Bu köşegende # için tek yer", "Bu hücre #",
        "Kafes toplamı: #", "Diagonal Sudoku", "Killer Sudoku", "Mini Sudoku 6\u00d76"};
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
    int cCorner;
    int cDiag;
    int[] palette;
    DashPathEffect cageDash;
    float cageDashCell;
    int layoutN;
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
    final float[] toolX = new float[TOOLS];
    float toolY;
    float toolW;
    float toolH;
    final float[] keyX = new float[9];
    final float[] keyY = new float[9];
    float keyW;
    float keyH;
    boolean menuOpen;
    boolean statsOpen;
    boolean variantsOpen;
    boolean colorMode;
    boolean idle;
    long lastInput = SystemClock.elapsedRealtime();
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
            cCorner = 0xFFFFAB70;
            cDiag = 0xFF211D2B;
            palette = PALETTE_DARK;
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
            cCorner = 0xFFC2410C;
            cDiag = 0xFFF3F0FB;
            palette = PALETTE_LIGHT;
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

    private void syncLayout() {
        if (layoutN != game.n) layout(getWidth(), getHeight());
    }

    private void layout(int w, int h) {
        int n = game.n;
        layoutN = n;
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
            toolW = pw / TOOLS;
            toolH = boardSize * 0.2f;
            toolY = msgY + msgH + pad;
            for (int i = 0; i < TOOLS; i++) toolX[i] = px + i * toolW;
            float ky = toolY + toolH + pad;
            keyW = pw / 3;
            keyH = (boardY + boardSize - ky) / (n / 3);
            for (int i = 0; i < n; i++) {
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
            toolW = boardSize / TOOLS;
            for (int i = 0; i < TOOLS; i++) toolX[i] = boardX + i * toolW;
            keyW = boardSize / n;
            for (int i = 0; i < n; i++) {
                keyX[i] = boardX + i * keyW;
                keyY[i] = toolY + toolH + gap;
            }
        }
        cell = boardSize / n;
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
        syncLayout();
        boolean generating = MainActivity.pendingLevel >= 0;
        if (wasGenerating && !generating) {
            wasGenerating = false;
            lastInput = SystemClock.elapsedRealtime();
            resumeIfAllowed();
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
        if (overlay()) {
            if (statsOpen) drawStats(c);
            else drawMenu(c);
        }
    }

    private Variant.Shape shape() {
        Variant v = MainActivity.variant;
        return v == null ? null : v.shape;
    }

    private void drawBoard(Canvas c) {
        int n = game.n;
        Variant.Shape shape = shape();
        boolean diagonal = shape != null && shape.kind == Variant.KIND_DIAGONAL;
        boolean killer = shape != null && shape.kind == Variant.KIND_KILLER;
        int sel = game.selected;
        int selValue = sel >= 0 && game.value[sel] != 0 ? game.value[sel] : game.sticky;
        int selBit = selValue == 0 ? 0 : Sudoku.bit(selValue);
        for (int i = 0; i < game.size; i++) {
            int row = i / n, col = i % n;
            int color = 0;
            if (i == sel) color = cSelected;
            else if (game.color[i] != 0) color = palette[game.color[i]];
            else if (selValue != 0 && (game.value[i] == selValue || (game.value[i] == 0 && ((game.notes[i] | game.corner[i]) & selBit) != 0))) color = cSame;
            else if (sel >= 0 && game.sees(sel, i)) color = cUnit;
            else if (diagonal && (row == col || row + col == n - 1)) color = cDiag;
            if (color == 0) continue;
            float x = boardX + col * cell;
            float y = boardY + row * cell;
            fillRect(c, x, y, x + cell, y + cell, color, 0);
        }
        int noteRows = n / 3;
        float noteL = killer ? cell * 0.1f : 0, noteT = killer ? cell * 0.27f : 0;
        float noteW = killer ? cell * 0.8f : cell, noteH = killer ? cell * 0.65f : cell;
        float noteSize = killer ? cell * 0.2f : cell * 0.28f;
        for (int i = 0; i < game.size; i++) {
            float x0 = boardX + (i % n) * cell;
            float y0 = boardY + (i / n) * cell;
            int v = game.value[i];
            if (v != 0) {
                boolean given = game.given[i] != 0;
                int color = game.conflict(i) || game.wrong(i) ? cWrong : given ? cGiven : cEntered;
                drawText(c, DIGITS[v], x0 + cell / 2, y0 + cell / 2, cell * 0.62f, color, given ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            } else if ((game.notes[i] | game.corner[i]) != 0) {
                for (int d = 1; d <= n; d++) {
                    int b = Sudoku.bit(d);
                    if (((game.notes[i] | game.corner[i]) & b) == 0) continue;
                    float nx = x0 + noteL + ((d - 1) % 3 + 0.5f) * noteW / 3;
                    float ny = y0 + noteT + ((d - 1) / 3 + 0.5f) * noteH / noteRows;
                    boolean same = d == selValue;
                    boolean corner = (game.corner[i] & b) != 0;
                    drawText(c, DIGITS[d], nx, ny, noteSize, same ? cEntered : corner ? cCorner : cNote, same || corner ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                }
            }
        }
        if (killer) drawCages(c, shape);
        int boxH = shape == null ? 3 : shape.boxH;
        int boxW = shape == null ? 3 : shape.boxW;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        for (int pass = 0; pass < 2; pass++) {
            boolean thick = pass == 1;
            paint.setColor(thick ? cThick : cLine);
            paint.setStrokeWidth(cell * (thick ? 0.06f : 0.02f) * 9 / n);
            for (int k = 0; k <= n; k++) {
                float p = k * cell;
                if ((k % boxH == 0) == thick) c.drawLine(boardX, boardY + p, boardX + boardSize, boardY + p, paint);
                if ((k % boxW == 0) == thick) c.drawLine(boardX + p, boardY, boardX + p, boardY + boardSize, paint);
            }
        }
    }

    private void drawCages(Canvas c, Variant.Shape shape) {
        int n = shape.n;
        float inset = cell * 0.07f;
        if (cageDash == null || cageDashCell != cell) {
            cageDash = new DashPathEffect(new float[] {cell * 0.09f, cell * 0.06f}, 0);
            cageDashCell = cell;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(Math.max(1, cell * 0.035f));
        paint.setColor(cNote);
        paint.setPathEffect(cageDash);
        for (int i = 0; i < shape.size; i++) {
            int k = shape.cageOf[i];
            if (k < 0) continue;
            int row = i / n, col = i % n;
            float l = boardX + col * cell + inset, r = boardX + (col + 1) * cell - inset;
            float t = boardY + row * cell + inset, b = boardY + (row + 1) * cell - inset;
            if (row == 0 || shape.cageOf[i - n] != k) c.drawLine(l, t, r, t, paint);
            if (row == n - 1 || shape.cageOf[i + n] != k) c.drawLine(l, b, r, b, paint);
            if (col == 0 || shape.cageOf[i - 1] != k) c.drawLine(l, t, l, b, paint);
            if (col == n - 1 || shape.cageOf[i + 1] != k) c.drawLine(r, t, r, b, paint);
        }
        paint.setPathEffect(null);
        float size = cell * 0.2f;
        for (int i = 0; i < shape.size; i++) {
            if (!shape.cageHead(i)) continue;
            String sum = Integer.toString(shape.cageSum[shape.cageOf[i]]);
            float w = width(sum, size, Typeface.DEFAULT_BOLD);
            float x = boardX + (i % n) * cell + inset * 0.5f;
            float y = boardY + (i / n) * cell + inset * 0.5f;
            int back = i == game.selected ? cSelected : game.color[i] != 0 ? palette[game.color[i]] : cBg;
            fillRect(c, x, y, x + w + size * 0.3f, y + size * 1.15f, back, 0);
            drawText(c, sum, x + size * 0.15f + w / 2, y + size * 0.58f, size, cGiven, Typeface.DEFAULT_BOLD);
        }
    }

    private void drawKeys(Canvas c) {
        float inset = keyW * 0.06f;
        for (int d = 1; d <= game.n; d++) {
            float x = keyX[d - 1];
            float y = keyY[d - 1];
            if (colorMode) {
                boolean usable = d <= 7 && game.selected >= 0;
                int fill = d <= 6 ? palette[d] : cKey;
                fillRect(c, x + inset, y + inset, x + keyW - inset, y + keyH - inset, d <= 7 ? fill : cBg, keyW * 0.15f);
                if (d <= 7) drawText(c, d <= 6 ? DIGITS[d] : "\u00d7", x + keyW / 2, y + keyH / 2, keyH * 0.42f, usable ? cKeyText : cMuted, Typeface.DEFAULT);
                continue;
            }
            boolean on = d == game.sticky;
            fillRect(c, x + inset, y + inset, x + keyW - inset, y + keyH - inset, on ? cAccent : cKey, keyW * 0.15f);
            int left = game.active ? Math.max(0, game.remaining(d)) : game.n;
            drawText(c, DIGITS[d], x + keyW / 2, y + keyH * 0.42f, keyH * 0.5f, on ? cBg : left > 0 ? cKeyText : cMuted, Typeface.DEFAULT);
            if (left > 0) drawText(c, DIGITS[left], x + keyW / 2, y + keyH * 0.8f, keyH * 0.2f, on ? cBg : cMuted, Typeface.DEFAULT);
        }
    }

    private int target(float x, float y) {
        syncLayout();
        int n = game.n;
        if (x >= boardX && x < boardX + boardSize && y >= boardY && y < boardY + boardSize) {
            return Math.min(n - 1, (int) ((y - boardY) / cell)) * n + Math.min(n - 1, (int) ((x - boardX) / cell));
        }
        for (int i = 0; i < n; i++) {
            if (x >= keyX[i] && x < keyX[i] + keyW && y >= keyY[i] && y < keyY[i] + keyH) return 100 + i;
        }
        for (int i = 0; i < TOOLS; i++) {
            if (x >= toolX[i] && x < toolX[i] + toolW && y >= toolY && y < toolY + toolH) return 200 + i;
        }
        if (x >= topX && x < topX + topW && y >= topY && y < topY + topH) return 300;
        if (explainable() && x >= msgX && x < msgX + msgW && y >= msgY && y < msgY + msgH) return 302;
        return -1;
    }

    private boolean explainable() {
        return game.active && !game.solved && game.hintActive() && game.hintKind == Game.HINT_PLACE && MainActivity.variant == null;
    }

    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        float x = e.getX();
        float y = e.getY();
        if (action == MotionEvent.ACTION_DOWN) input();
        if (overlay()) {
            if (action == MotionEvent.ACTION_DOWN) {
                downTarget = statsOpen ? statsTarget(x, y) : menuTarget(x, y);
            } else if (action == MotionEvent.ACTION_UP) {
                int t = statsOpen ? statsTarget(x, y) : menuTarget(x, y);
                if (t == downTarget) {
                    if (statsOpen) closeStats();
                    else menuAction(downTarget);
                }
                downTarget = -1;
            }
            return true;
        }
        if (MainActivity.pendingLevel >= 0) return true;
        int t = target(x, y);
        if (action == MotionEvent.ACTION_DOWN) {
            downTarget = t;
            if (t >= 0 && t < 100) finish(game.tap(t));
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (t >= 0 && t < 100 && t != downTarget) {
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
        if (t >= 100 && t < 109 && colorMode) changed = t - 99 <= 7 && game.paint(t - 99 <= 6 ? t - 99 : 0);
        else if (t >= 100 && t < 109) changed = game.key(t - 99);
        else if (t == 200) changed = game.undo();
        else if (t == 201) changed = colorMode ? game.paint(0) : game.erase();
        else if (t == 202) {
            game.cycleNotes();
            changed = true;
        } else if (t == 205) {
            colorMode = !colorMode;
            game.sticky = 0;
            changed = true;
        }
        else if (t == 203) changed = game.fillNotes();
        else if (t == 204) {
            Variant v = MainActivity.variant;
            changed = v != null ? game.hint(v) : game.hint(MainActivity.engine);
        }
        else if (t == 300) {
            openMenu();
            return;
        } else if (t == 302) {
            host.open(SITE + langPath() + "solver/?p=" + digits(game.value));
            return;
        }
        finish(changed);
    }

    private String langPath() {
        return text == TR ? "tr/" : "";
    }

    private static String digits(int[] values) {
        StringBuilder sb = new StringBuilder(81);
        for (int v : values) sb.append((char) ('0' + v));
        return sb.toString();
    }

    private void finish(boolean changed) {
        if (game.solved) {
            long now = SystemClock.elapsedRealtime();
            game.pause(now);
            removeCallbacks(this);
            if (!MainActivity.recorded) {
                MainActivity.recorded = true;
                if (MainActivity.variant == null) MainActivity.recordSolved(game.level, (int) (game.time(now) / 1000), MainActivity.dailyDate);
            }
        }
        if (changed) MainActivity.save();
        invalidate();
        String message = game.active && game.hintActive() ? hintMessage() : null;
        if (message != null && !message.equals(axLastMessage)) announceForAccessibility(message);
        axLastMessage = message;
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    void shown() {
        lastInput = SystemClock.elapsedRealtime();
        resumeIfAllowed();
    }

    void hidden() {
        game.pause(SystemClock.elapsedRealtime());
        removeCallbacks(this);
    }

    void focusChanged(boolean hasFocus) {
        if (hasFocus) {
            resumeIfAllowed();
            return;
        }
        game.pause(SystemClock.elapsedRealtime());
        removeCallbacks(this);
        MainActivity.save();
        invalidate();
    }

    private void input() {
        lastInput = SystemClock.elapsedRealtime();
        if (idle) {
            idle = false;
            resumeIfAllowed();
        }
    }

    private void resumeIfAllowed() {
        if (!overlay() && !idle && host.resumed && hasWindowFocus() && MainActivity.pendingLevel < 0) {
            game.resume(SystemClock.elapsedRealtime());
        }
        run();
        invalidate();
    }

    public void run() {
        removeCallbacks(this);
        if (!game.running) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastInput >= IDLE_MS) {
            idle = true;
            game.pause(now);
            MainActivity.save();
            invalidate();
            return;
        }
        invalidate();
        postDelayed(this, 1000);
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
        String s = text[u == 0 ? S_ROW : u == 1 ? S_COL : u == 2 ? S_BOX : u == 4 ? S_DIAG : u == 5 ? S_REVEAL : S_NAKED].replace("#", DIGITS[game.hintDigit]);
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
        String link = MainActivity.variant == null ? "  " + text[S_EXPLAIN] + " \u203a" : "";
        drawText(c, first, cx, msgY + msgH * 0.3f, fit(first, size, msgW * 0.96f), cAccent, Typeface.DEFAULT);
        float small = fit(second + link, size * 0.9f, msgW * 0.96f);
        float w1 = width(second, small, Typeface.DEFAULT), w2 = width(link, small, Typeface.DEFAULT);
        float left = cx - (w1 + w2) / 2;
        drawText(c, second, left + w1 / 2, msgY + msgH * 0.72f, small, cMuted, Typeface.DEFAULT);
        drawText(c, link, left + w1 + w2 / 2, msgY + msgH * 0.72f, small, cAccent, Typeface.DEFAULT);
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
        } else if (kind == 5) {
            c.drawCircle(cx, cy, s * 0.62f, paint);
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(cx - s * 0.25f, cy - s * 0.18f, s * 0.11f, paint);
            c.drawCircle(cx + s * 0.08f, cy - s * 0.32f, s * 0.11f, paint);
            c.drawCircle(cx + s * 0.3f, cy + s * 0.05f, s * 0.11f, paint);
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

    private String toolLabel(int i) {
        if (i == 5) return text[S_COLOR];
        if (i == 2 && game.noteMode && game.cornerMode) return text[S_CORNER];
        return text[S_UNDO + i];
    }

    private void drawTools(Canvas c) {
        float inset = toolW * 0.06f;
        boolean playable = game.active && !game.solved;
        if (!playable) colorMode = false;
        for (int i = 0; i < TOOLS; i++) {
            float x = toolX[i];
            boolean on = (i == 2 && game.noteMode) || (i == 4 && game.active && game.hintActive() && game.hintKind == Game.HINT_PLACE) || (i == 5 && colorMode);
            boolean enabled = playable && (i != 0 || !game.history.isEmpty());
            fillRect(c, x + inset, toolY + inset, x + toolW - inset, toolY + toolH - inset, on ? cSame : cKey, toolW * 0.15f);
            int color = !enabled ? cMuted : i == 2 && game.cornerMode ? cCorner : on ? cAccent : cKeyText;
            drawIcon(c, i, x + toolW / 2, toolY + toolH * 0.38f, toolH * 0.3f, color);
            String label = toolLabel(i);
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
    int[][] menuSpec = new int[0][];

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
        if (game.active && !generating && MainActivity.showTimer) {
            boolean paused = !game.running && !game.solved && !overlay();
            drawText(c, clock(game.time(SystemClock.elapsedRealtime())), topX + topW / 2, cy, size, paused ? (cMuted & 0x00FFFFFF) | 0x66000000 : cMuted, Typeface.MONOSPACE);
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

    private int[][] buildMenuSpec() {
        if (variantsOpen) return new int[][] {{441}, {442}, {443}, {444}};
        boolean solved = game.active && game.solved;
        boolean variants = MainActivity.menuKind >= 0;
        int levels = variants ? Variant.LEVELS : Sudoku.LEVELS;
        int n = (solved ? 1 : 0) + levels + (variants ? 2 : 3) + (cancellable() ? 2 : 0);
        int[][] rows = new int[n][];
        int k = 0;
        if (solved) rows[k++] = new int[] {412};
        for (int level = 0; level < levels; level++) rows[k++] = new int[] {400 + level};
        rows[k++] = new int[] {405, 408};
        if (variants) rows[k++] = new int[] {409, 413};
        else {
            rows[k++] = new int[] {409, 411};
            rows[k++] = new int[] {413};
        }
        if (cancellable()) {
            rows[k++] = new int[] {406};
            rows[k++] = new int[] {407};
        }
        return rows;
    }

    private int titleLines() {
        if (variantsOpen) return 1;
        boolean solved = game.active && game.solved;
        boolean dated = solved ? !MainActivity.dailyDate.isEmpty() : MainActivity.dailyMenu;
        return 2 + (solved ? 1 : 0) + (dated ? 1 : 0);
    }

    private void layoutMenu(int w, int h) {
        float base = Math.min(w - insetL - insetR, h - insetT - insetB);
        menuW = base * 0.82f;
        menuSpec = buildMenuSpec();
        menuRows = menuSpec.length;
        float titleRows = 0.4f + titleLines() * 0.6f;
        menuRowH = Math.min(base * 0.095f, (h - insetT - insetB) * 0.94f / (titleRows + menuRows + 0.4f));
        menuTitleH = menuRowH * titleRows;
        float total = menuTitleH + menuRows * menuRowH + menuRowH * 0.4f;
        menuX = insetL + (w - insetL - insetR - menuW) / 2;
        menuTop = insetT + (h - insetT - insetB - total) / 2;
    }

    private float menuRowY(int row) {
        return menuTop + menuTitleH + row * menuRowH;
    }

    private RectF menuItemRect(int row, int item) {
        int n = menuSpec[row].length;
        float side = menuRowH * 0.4f;
        float gap = menuRowH * 0.12f;
        float w = (menuW - 2 * side - gap * (n - 1)) / n;
        float x = menuX + side + item * (w + gap);
        float ry = menuRowY(row);
        return new RectF(x, ry, x + w, ry + menuRowH);
    }

    private String dateText(String day) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault()).format(MainActivity.parseDate(day).getTime());
    }

    private String gameName() {
        Variant v = MainActivity.variant;
        return v == null ? "" : text[S_VARIANT_NAME + v.shape.kind];
    }

    private String[] titleTexts() {
        if (variantsOpen) return new String[] {text[S_VARIANTS]};
        boolean solved = game.active && game.solved;
        String[] lines = new String[titleLines()];
        int k = 0;
        int kind = MainActivity.menuKind;
        lines[k++] = solved ? text[S_SOLVED] : MainActivity.dailyMenu ? text[S_DAILY] : kind >= 0 ? text[S_VARIANT_NAME + kind] : text[S_TITLE];
        if (solved) lines[k++] = (MainActivity.variant != null ? gameName() + " \u00b7 " : "") + levelText() + "  " + clock(game.time(0));
        if (solved ? !MainActivity.dailyDate.isEmpty() : MainActivity.dailyMenu) lines[k++] = dateText(solved ? MainActivity.dailyDate : MainActivity.today());
        lines[k] = text[S_NEW];
        return lines;
    }

    private void drawMenu(Canvas c) {
        c.drawColor(cDim);
        layoutMenu(getWidth(), getHeight());
        float bottom = menuRowY(menuRows) + menuRowH * 0.4f;
        fillRect(c, menuX, menuTop, menuX + menuW, bottom, cPanel, menuRowH * 0.3f);
        boolean solved = game.active && game.solved;
        float cx = menuX + menuW / 2;
        String[] lines = titleTexts();
        float y = menuTop + menuRowH * 0.7f;
        drawText(c, lines[0], cx, y, fit(lines[0], menuRowH * 0.5f, menuW * 0.9f), cKeyText, Typeface.DEFAULT_BOLD);
        for (int k = 1; k < lines.length; k++) {
            y += menuRowH * 0.6f;
            boolean result = solved && k == 1;
            drawText(c, lines[k], cx, y, fit(lines[k], menuRowH * (result ? 0.38f : 0.34f), menuW * 0.9f), result ? cAccent : cMuted, Typeface.DEFAULT);
        }
        float inset = menuRowH * 0.08f;
        for (int row = 0; row < menuRows; row++) {
            for (int item = 0; item < menuSpec[row].length; item++) {
                RectF r = menuItemRect(row, item);
                String label = menuLabel(menuSpec[row][item]);
                fillRect(c, r.left, r.top + inset, r.right, r.bottom - inset, cKey, menuRowH * 0.25f);
                drawText(c, label, r.centerX(), r.centerY(), fit(label, menuRowH * 0.4f, r.width() * 0.9f), cKeyText, Typeface.DEFAULT);
            }
        }
    }

    private int menuTarget(float x, float y) {
        layoutMenu(getWidth(), getHeight());
        float bottom = menuRowY(menuRows) + menuRowH * 0.4f;
        if (x < menuX || x > menuX + menuW || y < menuTop || y > bottom) return 499;
        for (int row = 0; row < menuRows; row++) {
            for (int item = 0; item < menuSpec[row].length; item++) {
                if (menuItemRect(row, item).contains(x, y)) return menuSpec[row][item];
            }
        }
        return -1;
    }

    private void menuAction(int t) {
        if (variantsOpen) {
            if (t >= 441 && t <= 443) {
                MainActivity.menuKind = VARIANT_ORDER[t - 441];
                MainActivity.dailyMenu = false;
            }
            if (t >= 441 && t <= 444 || t == 499) {
                variantsOpen = false;
                invalidate();
                sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            }
            return;
        }
        if (t >= 400 && t < 405) {
            startGame(t - 400);
        } else if (t == 405) {
            game.showErrors = !game.showErrors;
            MainActivity.save();
            invalidate();
        } else if (t == 408) {
            MainActivity.showTimer = !MainActivity.showTimer;
            MainActivity.prefs.edit().putString("t", MainActivity.showTimer ? "1" : "0").apply();
            invalidate();
        } else if (t == 409) {
            if (MainActivity.menuKind >= 0) MainActivity.menuKind = -1;
            else MainActivity.dailyMenu = !MainActivity.dailyMenu;
            invalidate();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        } else if (t == 413) {
            variantsOpen = true;
            invalidate();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        } else if (t == 411) {
            statsOpen = true;
            invalidate();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        } else if (t == 412 && game.active && game.solved) {
            host.share(shareText());
        } else if (t == 406 && cancellable()) {
            game.restart();
            MainActivity.save();
            closeMenu();
        } else if ((t == 407 || t == 499) && cancellable()) {
            closeMenu();
        }
    }

    private String shareText() {
        String result = levelText() + " \u00b7 " + clock(game.time(0));
        Variant v = MainActivity.variant;
        if (v != null) return gameName() + " \u00b7 " + result + "\n" + SITE + langPath() + VARIANT_PATHS[v.shape.kind];
        if (!MainActivity.dailyDate.isEmpty()) return text[S_DAILY] + " " + dateText(MainActivity.dailyDate) + " \u00b7 " + result + "\n" + SITE + langPath() + "daily/";
        return text[S_TITLE] + " \u00b7 " + result + "\n" + SITE + langPath() + "?p=" + digits(game.given);
    }

    float statsX;
    float statsW;
    float statsTop;
    float statsRowH;
    int statsLines;

    private void layoutStats(int w, int h) {
        float base = Math.min(w - insetL - insetR, h - insetT - insetB);
        statsW = base * 0.9f;
        statsLines = 0;
        int[] a = MainActivity.stats();
        for (int level = 0; level < Sudoku.LEVELS; level++) if (a[level * 4] > 0 || a[level * 4 + 1] > 0) statsLines++;
        float rows = 1.4f + 1 + statsLines + 1.2f + 1.2f;
        statsRowH = Math.min(base * 0.085f, (h - insetT - insetB) * 0.94f / rows);
        float total = statsRowH * rows;
        statsX = insetL + (w - insetL - insetR - statsW) / 2;
        statsTop = insetT + (h - insetT - insetB - total) / 2;
    }

    private RectF statsCloseRect() {
        float y = statsTop + statsRowH * (1.4f + 1 + statsLines + 1.2f);
        float side = statsRowH * 0.4f;
        return new RectF(statsX + side, y, statsX + statsW - side, y + statsRowH);
    }

    private String[] statsRow(int level, int[] a) {
        int p = a[level * 4], s = a[level * 4 + 1], t = a[level * 4 + 2], b = a[level * 4 + 3];
        return new String[] {levelName(level), s + " / " + p, b > 0 ? clock(b * 1000L) : "-", s > 0 ? clock(Math.round((double) t / s) * 1000L) : "-"};
    }

    private void drawStats(Canvas c) {
        c.drawColor(cDim);
        layoutStats(getWidth(), getHeight());
        RectF close = statsCloseRect();
        fillRect(c, statsX, statsTop, statsX + statsW, close.bottom + statsRowH * 0.4f, cPanel, statsRowH * 0.3f);
        float cx = statsX + statsW / 2;
        drawText(c, text[S_STATS], cx, statsTop + statsRowH * 0.75f, fit(text[S_STATS], statsRowH * 0.55f, statsW * 0.9f), cKeyText, Typeface.DEFAULT_BOLD);
        float[] colX = {statsX + statsW * 0.2f, statsX + statsW * 0.45f, statsX + statsW * 0.66f, statsX + statsW * 0.86f};
        float colW = statsW * 0.2f;
        float y = statsTop + statsRowH * 1.4f + statsRowH / 2;
        String[] head = {"", text[S_PLAYED], text[S_BEST], text[S_AVERAGE]};
        for (int k = 1; k < 4; k++) drawText(c, head[k], colX[k], y, fit(head[k], statsRowH * 0.3f, colW), cMuted, Typeface.DEFAULT);
        int[] a = MainActivity.stats();
        for (int level = 0; level < Sudoku.LEVELS; level++) {
            if (a[level * 4] == 0 && a[level * 4 + 1] == 0) continue;
            y += statsRowH;
            String[] cells = statsRow(level, a);
            for (int k = 0; k < 4; k++) drawText(c, cells[k], colX[k], y, fit(cells[k], statsRowH * 0.38f, k == 0 ? statsW * 0.28f : colW), cKeyText, k == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        String streak = text[S_STREAK] + ": " + MainActivity.streak();
        drawText(c, streak, cx, y + statsRowH * 1.1f, fit(streak, statsRowH * 0.38f, statsW * 0.9f), cMuted, Typeface.DEFAULT);
        fillRect(c, close.left, close.top + statsRowH * 0.08f, close.right, close.bottom - statsRowH * 0.08f, cKey, statsRowH * 0.25f);
        drawText(c, text[S_CANCEL], close.centerX(), close.centerY(), fit(text[S_CANCEL], statsRowH * 0.4f, close.width() * 0.9f), cKeyText, Typeface.DEFAULT);
    }

    private int statsTarget(float x, float y) {
        layoutStats(getWidth(), getHeight());
        RectF close = statsCloseRect();
        if (close.contains(x, y)) return 431;
        if (x < statsX || x > statsX + statsW || y < statsTop || y > close.bottom + statsRowH * 0.4f) return 431;
        return 430;
    }

    private String statsSummary() {
        StringBuilder sb = new StringBuilder(text[S_STATS]);
        int[] a = MainActivity.stats();
        for (int level = 0; level < Sudoku.LEVELS; level++) {
            if (a[level * 4] == 0 && a[level * 4 + 1] == 0) continue;
            String[] cells = statsRow(level, a);
            sb.append(". ").append(cells[0]).append(": ").append(text[S_PLAYED]).append(' ').append(cells[1]).append(", ").append(text[S_BEST]).append(' ').append(cells[2]).append(", ").append(text[S_AVERAGE]).append(' ').append(cells[3]);
        }
        return sb.append(". ").append(text[S_STREAK]).append(": ").append(MainActivity.streak()).toString();
    }

    private void closeStats() {
        statsOpen = false;
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private void startGame(int level) {
        if (MainActivity.pendingLevel >= 0) return;
        menuOpen = false;
        statsOpen = false;
        idle = false;
        game.pause(SystemClock.elapsedRealtime());
        removeCallbacks(this);
        wasGenerating = true;
        host.generate(level, MainActivity.dailyMenu, MainActivity.menuKind);
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private void openMenu() {
        menuOpen = true;
        variantsOpen = false;
        game.pause(SystemClock.elapsedRealtime());
        removeCallbacks(this);
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private void closeMenu() {
        menuOpen = false;
        statsOpen = false;
        variantsOpen = false;
        idle = false;
        lastInput = SystemClock.elapsedRealtime();
        resumeIfAllowed();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private String levelName(int level) {
        return text[level < 4 ? level : S_MASTER];
    }

    private String levelText() {
        String name = levelName(game.level);
        return game.rating > 0 ? name + " " + String.format(Locale.getDefault(), "%.1f", game.rating / 10.0) : name;
    }

    private String menuLabel(int id) {
        if (id < 405) return levelName(id - 400);
        if (id == 405) return text[S_ERRORS] + ": " + text[game.showErrors ? S_ON : S_OFF];
        if (id == 408) return text[S_TIMER] + ": " + text[MainActivity.showTimer ? S_ON : S_OFF];
        if (id == 409) return text[MainActivity.dailyMenu || MainActivity.menuKind >= 0 ? S_PLAY : S_DAILY];
        if (id == 413) return text[S_VARIANTS];
        if (id >= 441 && id <= 443) return text[S_VARIANT_NAME + VARIANT_ORDER[id - 441]];
        if (id == 411) return text[S_STATS];
        if (id == 412) return text[S_SHARE];
        return text[id == 406 ? S_RESTART : S_CANCEL];
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
            if (statsOpen) return statsTarget(x, y);
            int t = menuTarget(x, y);
            return t >= 400 && t < 499 ? t : -1;
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
            if (statsOpen) return new int[] {430, 431};
            layoutMenu(getWidth(), getHeight());
            int n = 1;
            for (int[] row : menuSpec) n += row.length;
            int[] ids = new int[n];
            int k = 0;
            ids[k++] = 410;
            for (int[] row : menuSpec) for (int id : row) ids[k++] = id;
            return ids;
        }
        boolean hint = game.active && game.hintActive();
        int[] ids = new int[2 + (hint ? 1 : 0) + game.size + game.n + TOOLS];
        int n = 0;
        ids[n++] = 301;
        ids[n++] = 300;
        if (hint) ids[n++] = 302;
        for (int i = 0; i < game.size; i++) ids[n++] = i;
        for (int d = 1; d <= game.n; d++) ids[n++] = 99 + d;
        for (int i = 0; i < TOOLS; i++) ids[n++] = 200 + i;
        return ids;
    }

    String axClass(int id) {
        return axClickable(id) ? "android.widget.Button" : "android.widget.TextView";
    }

    boolean axClickable(int id) {
        if (id == 302) return explainable();
        return id != 301 && id != 410 && id != 420 && id != 430;
    }

    String axLabel(int id) {
        if (id == 420) return text[S_PREPARING];
        if (id == 430) return statsSummary();
        if (id == 431) return text[S_CANCEL];
        if (id == 410) return String.join(". ", titleTexts());
        if (id >= 400) return menuLabel(id);
        if (id == 301) {
            if (!game.active) return "";
            String level = MainActivity.variant != null ? gameName() + ", " + levelText() : levelText();
            return MainActivity.showTimer ? level + ", " + clock(game.time(SystemClock.elapsedRealtime())) : level;
        }
        if (id == 300) return text[S_NEW];
        if (id == 302) {
            String message = hintMessage();
            if (game.hintKind != Game.HINT_PLACE) return message;
            return MainActivity.variant == null ? message + ". " + text[S_AGAIN] + ". " + text[S_EXPLAIN] : message + ". " + text[S_AGAIN];
        }
        if (id >= 200) return toolLabel(id - 200);
        if (id >= 100) {
            if (colorMode) return id - 99 <= 6 ? text[S_COLOR] + " " + (id - 99) : text[S_ERASE];
            int left = game.active ? Math.max(0, game.remaining(id - 99)) : game.n;
            return DIGITS[id - 99] + ", " + text[S_LEFT].replace("#", Integer.toString(left));
        }
        String label = text[S_ROWLABEL] + " " + (id / game.n + 1) + ", " + text[S_COLLABEL] + " " + (id % game.n + 1);
        if (!game.active) return label;
        int v = game.value[id];
        Variant.Shape shape = shape();
        String paint = game.color[id] != 0 ? ", " + text[S_COLOR] + " " + game.color[id] : "";
        if (shape != null && shape.cageHead(id)) paint += ", " + text[S_CAGE].replace("#", Integer.toString(shape.cageSum[shape.cageOf[id]]));
        if (v != 0) return label + ", " + DIGITS[v] + (game.conflict(id) || game.wrong(id) ? ", " + text[S_WRONG] : "") + paint;
        StringBuilder notes = new StringBuilder(label);
        if (game.notes[id] != 0) {
            notes.append(", ").append(text[S_NOTE]);
            for (int d = 1; d <= 9; d++) if ((game.notes[id] & Sudoku.bit(d)) != 0) notes.append(' ').append(d);
        }
        if (game.corner[id] != 0) {
            notes.append(", ").append(text[S_CORNER]);
            for (int d = 1; d <= 9; d++) if ((game.corner[id] & Sudoku.bit(d)) != 0) notes.append(' ').append(d);
        }
        return notes.append(paint).toString();
    }

    boolean axEnabled(int id) {
        boolean playable = game.active && !game.solved;
        if (id < 100) return playable;
        if (id < 109) return playable && (colorMode ? id - 99 <= 7 && game.selected >= 0 : game.remaining(id - 99) > 0);
        if (id < 200 + TOOLS) return playable && (id != 200 || !game.history.isEmpty());
        return true;
    }

    boolean axSelected(int id) {
        if (id < 100) return id == game.selected;
        if (id < 109) return !colorMode && id - 99 == game.sticky;
        if (id == 202) return game.noteMode;
        if (id == 205) return colorMode;
        if (id == 204) return game.active && game.hintActive() && game.hintKind == Game.HINT_PLACE;
        return false;
    }

    Rect axRect(int id) {
        if (id == 420) return new Rect(0, 0, getWidth(), getHeight());
        if (id == 430 || id == 431) {
            layoutStats(getWidth(), getHeight());
            RectF close = statsCloseRect();
            if (id == 431) return new Rect((int) close.left, (int) close.top, (int) close.right, (int) close.bottom);
            return new Rect((int) statsX, (int) statsTop, (int) (statsX + statsW), (int) close.top);
        }
        if (id == 410) return new Rect((int) menuX, (int) menuTop, (int) (menuX + menuW), (int) (menuTop + menuTitleH));
        if (id >= 400) {
            for (int row = 0; row < menuRows; row++) {
                for (int item = 0; item < menuSpec[row].length; item++) {
                    if (menuSpec[row][item] != id) continue;
                    RectF r = menuItemRect(row, item);
                    return new Rect((int) r.left, (int) r.top, (int) r.right, (int) r.bottom);
                }
            }
            return new Rect();
        }
        if (id == 301) return new Rect((int) topX, (int) topY, (int) (topX + topW - topH * 0.9f), (int) (topY + topH));
        if (id == 300) return new Rect((int) (topX + topW - topH * 0.9f), (int) topY, (int) (topX + topW), (int) (topY + topH));
        if (id == 302) return new Rect((int) msgX, (int) msgY, (int) (msgX + msgW), (int) (msgY + msgH));
        if (id >= 200) return new Rect((int) toolX[id - 200], (int) toolY, (int) (toolX[id - 200] + toolW), (int) (toolY + toolH));
        syncLayout();
        if (id >= 100) return new Rect((int) keyX[id - 100], (int) keyY[id - 100], (int) (keyX[id - 100] + keyW), (int) (keyY[id - 100] + keyH));
        float x = boardX + (id % game.n) * cell;
        float y = boardY + (id / game.n) * cell;
        return new Rect((int) x, (int) y, (int) (x + cell), (int) (y + cell));
    }

    void activate(int t) {
        if (MainActivity.pendingLevel >= 0 || t < 0) return;
        input();
        if (overlay()) {
            if (statsOpen) {
                if (t == 431) closeStats();
            } else if (t >= 400 && t != 410 && t != 420) menuAction(t);
            return;
        }
        if (t < 100) finish(game.tap(t));
        else if (t >= 100 && t <= 300) act(t);
        else if (t == 302 && explainable()) act(t);
    }

    boolean back() {
        if (variantsOpen) {
            variantsOpen = false;
            invalidate();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            return true;
        }
        if (statsOpen) {
            closeStats();
            return true;
        }
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
