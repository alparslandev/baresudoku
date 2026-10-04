package com.baresudoku;

import java.util.ArrayList;
import java.util.Arrays;

final class Game {
    static final int NONE = -1;
    static final int COLORS = 6;
    final int[] given = new int[81];
    final int[] solution = new int[81];
    final int[] value = new int[81];
    final int[] notes = new int[81];
    final int[] corner = new int[81];
    final int[] color = new int[81];
    final ArrayList<int[]> history = new ArrayList<int[]>();
    private final int[] record = new int[324];
    private int recordLength;
    boolean active;
    boolean solved;
    boolean noteMode;
    boolean cornerMode;
    boolean showErrors = true;
    int level;
    int rating;
    int selected = NONE;
    int sticky;
    long elapsed;
    long runningSince;
    int size = 81;
    int n = 9;
    int all = Sudoku.ALL;
    int[][] peers = Sudoku.PEERS;

    void setShape(Variant.Shape shape) {
        size = shape == null ? 81 : shape.size;
        n = shape == null ? 9 : shape.n;
        all = shape == null ? Sudoku.ALL : shape.all;
        peers = Sudoku.PEERS;
        if (shape == null) return;
        peers = new int[shape.size][];
        for (int c = 0; c < shape.size; c++) peers[c] = Arrays.copyOfRange(shape.peerCells, c * Variant.MAX_PEERS, c * Variant.MAX_PEERS + shape.peerCount[c]);
    }

    int candidatesOf(int cell) {
        int used = 0;
        for (int p : peers[cell]) if (value[p] != 0) used |= Sudoku.bit(value[p]);
        return all & ~used;
    }

    boolean sees(int a, int b) {
        for (int p : peers[a]) if (p == b) return true;
        return false;
    }

    void start(int[] puzzle, int[] full, int newLevel) {
        Arrays.fill(given, 0);
        Arrays.fill(solution, 0);
        System.arraycopy(puzzle, 0, given, 0, size);
        System.arraycopy(full, 0, solution, 0, size);
        level = newLevel;
        rating = 0;
        active = true;
        restart();
    }

    void restart() {
        System.arraycopy(given, 0, value, 0, 81);
        Arrays.fill(notes, 0);
        Arrays.fill(corner, 0);
        Arrays.fill(color, 0);
        history.clear();
        recordLength = 0;
        solved = false;
        noteMode = false;
        cornerMode = false;
        selected = NONE;
        sticky = 0;
        elapsed = 0;
        running = false;
        hintKind = HINT_NONE;
    }

    boolean canEdit() {
        return active && !solved && selected >= 0 && given[selected] == 0;
    }

    void select(int cell) {
        if (sticky == 0) selected = cell;
    }

    boolean enter(int d) {
        if (!canEdit()) return false;
        int c = selected;
        if (noteMode) {
            if (value[c] != 0) return false;
            touch(c);
            if (cornerMode) corner[c] ^= Sudoku.bit(d);
            else notes[c] ^= Sudoku.bit(d);
            return commit();
        }
        touch(c);
        if (value[c] == d) {
            value[c] = 0;
            return commit();
        }
        value[c] = d;
        notes[c] = 0;
        corner[c] = 0;
        int b = Sudoku.bit(d);
        for (int p : peers[c]) {
            if (((notes[p] | corner[p]) & b) != 0) {
                touch(p);
                notes[p] &= ~b;
                corner[p] &= ~b;
            }
        }
        commit();
        checkSolved();
        if (sticky != 0 && (solved || remaining(sticky) <= 0)) sticky = 0;
        return true;
    }

    boolean key(int d) {
        if (d < 1 || d > n) return false;
        if (sticky == 0 && canEdit() && (noteMode ? value[selected] == 0 : value[selected] != d)) return enter(d);
        sticky = sticky == d || remaining(d) <= 0 ? 0 : d;
        if (sticky != 0) selected = NONE;
        return false;
    }

    boolean tap(int cell) {
        if (!active || solved) return false;
        if (sticky == 0) {
            selected = cell == selected ? NONE : cell;
            return false;
        }
        selected = cell;
        boolean changed = enter(sticky);
        selected = NONE;
        return changed;
    }

    boolean erase() {
        if (!canEdit() || (value[selected] == 0 && notes[selected] == 0 && corner[selected] == 0)) return false;
        touch(selected);
        value[selected] = 0;
        notes[selected] = 0;
        corner[selected] = 0;
        return commit();
    }

    void cycleNotes() {
        if (!noteMode) noteMode = true;
        else if (!cornerMode) cornerMode = true;
        else {
            noteMode = false;
            cornerMode = false;
        }
    }

    boolean paint(int k) {
        if (!active || solved || selected < 0) return false;
        color[selected] = color[selected] == k ? 0 : k;
        return true;
    }

    private void touch(int cell) {
        for (int i = 0; i < recordLength; i += 4) if (record[i] == cell) return;
        record[recordLength++] = cell;
        record[recordLength++] = value[cell];
        record[recordLength++] = notes[cell];
        record[recordLength++] = corner[cell];
    }

    private boolean commit() {
        if (recordLength == 0) return false;
        history.add(Arrays.copyOf(record, recordLength));
        recordLength = 0;
        return true;
    }

    private void checkSolved() {
        for (int i = 0; i < size; i++) if (value[i] != solution[i]) return;
        solved = true;
        selected = NONE;
    }

    boolean conflict(int cell) {
        int v = value[cell];
        if (v == 0) return false;
        for (int p : peers[cell]) if (value[p] == v) return true;
        return false;
    }

    boolean wrong(int cell) {
        return showErrors && given[cell] == 0 && value[cell] != 0 && value[cell] != solution[cell];
    }

    int remaining(int d) {
        int left = n;
        for (int i = 0; i < size; i++) if (value[i] == d) left--;
        return left;
    }

    void resume(long now) {
        if (active && !solved && !running) {
            running = true;
            runningSince = now;
        }
    }

    void pause(long now) {
        if (running) {
            elapsed += now - runningSince;
            running = false;
        }
    }

    long time(long now) {
        return elapsed + (running ? now - runningSince : 0);
    }

    boolean undo() {
        if (!active || solved || history.isEmpty()) return false;
        int[] r = history.remove(history.size() - 1);
        for (int i = 0; i < r.length; i += 4) {
            value[r[i]] = r[i + 1];
            notes[r[i]] = r[i + 2];
            corner[r[i]] = r[i + 3];
        }
        if (sticky == 0) selected = r[0];
        return true;
    }

    boolean fillNotes() {
        if (!active || solved) return false;
        for (int i = 0; i < size; i++) {
            if (value[i] != 0) continue;
            int m = candidatesOf(i);
            if (notes[i] != m) {
                touch(i);
                notes[i] = m;
            }
        }
        if (commit()) return true;
        for (int i = 0; i < size; i++) {
            if (value[i] == 0 && notes[i] != 0) {
                touch(i);
                notes[i] = 0;
            }
        }
        return commit();
    }

    static final int HINT_NONE = 0;
    static final int HINT_WRONG = 1;
    static final int HINT_PLACE = 2;
    int hintKind;
    int hintCell = NONE;
    int hintDigit;
    int hintTech;
    int hintUnit;
    private int hintMoves;

    boolean hintActive() {
        return hintKind != HINT_NONE && active && !solved && history.size() == hintMoves && selected == hintCell;
    }

    private static final int HINT_ASK = -1;

    private int hintBeforeSolver() {
        if (!active || solved) return 0;
        sticky = 0;
        if (hintActive() && hintKind == HINT_PLACE && value[hintCell] == 0) {
            noteMode = false;
            cornerMode = false;
            hintKind = HINT_NONE;
            return enter(hintDigit) ? 1 : 0;
        }
        hintKind = HINT_NONE;
        for (int i = 0; i < size; i++) {
            if (given[i] == 0 && value[i] != 0 && value[i] != solution[i]) {
                hintKind = HINT_WRONG;
                hintCell = i;
                selected = i;
                hintMoves = history.size();
                return 1;
            }
        }
        return HINT_ASK;
    }

    private boolean showHint(int cell, int digit, int tech, int unit) {
        hintKind = HINT_PLACE;
        hintCell = cell;
        hintDigit = digit;
        hintTech = tech;
        hintUnit = unit;
        selected = hintCell;
        hintMoves = history.size();
        return true;
    }

    boolean hint(Sudoku engine) {
        int done = hintBeforeSolver();
        if (done != HINT_ASK) return done == 1;
        return engine.hint(value, given) && showHint(engine.stepCell, engine.stepDigit, engine.hintTech, engine.stepUnit);
    }

    boolean hint(Variant engine) {
        int done = hintBeforeSolver();
        if (done != HINT_ASK) return done == 1;
        return engine.hint(value, given) && showHint(engine.stepCell, engine.stepDigit, engine.hintTech, engine.stepUnit);
    }

    String encode(long now) {
        StringBuilder sb = new StringBuilder(700);
        sb.append(active ? 1 : 0).append('|').append(level).append('|').append(solved ? 1 : 0).append('|')
            .append(showErrors ? 1 : 0).append('|').append(noteMode ? 1 : 0).append('|').append(selected).append('|')
            .append(time(now)).append('|');
        appendDigits(sb, given, size);
        sb.append('|');
        appendDigits(sb, solution, size);
        sb.append('|');
        appendDigits(sb, value, size);
        sb.append('|');
        appendMasks(sb, notes, size);
        sb.append('|').append(history.size());
        for (int[] r : history) {
            sb.append('|');
            for (int i = 0; i < r.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(r[i]);
            }
        }
        sb.append('|');
        appendMasks(sb, corner, size);
        sb.append('|');
        appendDigits(sb, color, size);
        sb.append('|').append(cornerMode ? 1 : 0);
        return sb.toString();
    }

    private static void appendMasks(StringBuilder sb, int[] masks, int size) {
        for (int i = 0; i < size; i++) {
            int m = masks[i];
            sb.append((char) ('0' + (m >> 6))).append((char) ('0' + ((m >> 3) & 7))).append((char) ('0' + (m & 7)));
        }
    }

    private boolean readMasks(String s, int[] masks) {
        if (s.length() != size * 3) return false;
        Arrays.fill(masks, 0);
        for (int i = 0; i < size; i++) {
            int m = 0;
            for (int k = 0; k < 3; k++) {
                int o = s.charAt(i * 3 + k) - '0';
                if (o < 0 || o > 7) return false;
                m = (m << 3) | o;
            }
            masks[i] = m & all;
        }
        return true;
    }

    private static void appendDigits(StringBuilder sb, int[] a, int size) {
        for (int i = 0; i < size; i++) sb.append((char) ('0' + a[i]));
    }

    private void readDigits(String s, int[] a) {
        if (s.length() != size) throw new IllegalArgumentException();
        Arrays.fill(a, 0);
        for (int i = 0; i < size; i++) {
            int v = s.charAt(i) - '0';
            if (v < 0 || v > 9) throw new IllegalArgumentException();
            a[i] = v;
        }
    }

    boolean decode(String s) {
        active = false;
        if (s == null) return false;
        try {
            String[] f = s.split("\\|", -1);
            if (f.length < 12) return false;
            showErrors = f[3].equals("1");
            if (!f[0].equals("1")) return true;
            int lvl = Integer.parseInt(f[1]);
            int sel = Integer.parseInt(f[5]);
            long time = Long.parseLong(f[6]);
            if (lvl < 0 || lvl >= Sudoku.LEVELS || sel < NONE || sel >= size || time < 0) return false;
            readDigits(f[7], given);
            readDigits(f[8], solution);
            readDigits(f[9], value);
            if (!readMasks(f[10], notes)) return false;
            int n = Integer.parseInt(f[11]);
            boolean extended = n >= 0 && f.length == 15 + n;
            if (n < 0 || (!extended && f.length != 12 + n)) return false;
            int width = extended ? 4 : 3;
            history.clear();
            for (int k = 0; k < n; k++) {
                String[] parts = f[12 + k].split(",");
                if (parts.length % width != 0) return false;
                int[] r = new int[parts.length / width * 4];
                for (int i = 0, j = 0; i < parts.length; i += width, j += 4) {
                    r[j] = Integer.parseInt(parts[i]);
                    r[j + 1] = Integer.parseInt(parts[i + 1]);
                    r[j + 2] = Integer.parseInt(parts[i + 2]);
                    r[j + 3] = extended ? Integer.parseInt(parts[i + 3]) : 0;
                }
                history.add(r);
            }
            Arrays.fill(corner, 0);
            Arrays.fill(color, 0);
            cornerMode = false;
            if (extended) {
                if (!readMasks(f[12 + n], corner)) return false;
                readDigits(f[13 + n], color);
                for (int i = 0; i < size; i++) if (color[i] > COLORS) return false;
                cornerMode = f[14 + n].equals("1");
            }
            recordLength = 0;
            level = lvl;
            rating = 0;
            solved = f[2].equals("1");
            noteMode = f[4].equals("1");
            cornerMode = noteMode && cornerMode;
            selected = sel;
            elapsed = time;
            running = false;
            hintKind = HINT_NONE;
            active = true;
            return true;
        } catch (RuntimeException e) {
            active = false;
            return false;
        }
    }

    boolean running;
}
