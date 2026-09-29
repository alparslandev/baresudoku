package com.baresudoku;

import java.util.ArrayList;
import java.util.Arrays;

final class Game {
    static final int NONE = -1;
    final int[] given = new int[81];
    final int[] solution = new int[81];
    final int[] value = new int[81];
    final int[] notes = new int[81];
    final ArrayList<int[]> history = new ArrayList<int[]>();
    private final int[] record = new int[243];
    private int recordLength;
    boolean active;
    boolean solved;
    boolean noteMode;
    boolean showErrors = true;
    int level;
    int selected = NONE;
    long elapsed;
    long runningSince;

    void start(int[] puzzle, int[] full, int newLevel) {
        System.arraycopy(puzzle, 0, given, 0, 81);
        System.arraycopy(full, 0, solution, 0, 81);
        System.arraycopy(puzzle, 0, value, 0, 81);
        Arrays.fill(notes, 0);
        history.clear();
        recordLength = 0;
        active = true;
        solved = false;
        noteMode = false;
        level = newLevel;
        selected = NONE;
        elapsed = 0;
        running = false;
        hintKind = HINT_NONE;
    }

    boolean canEdit() {
        return active && !solved && selected >= 0 && given[selected] == 0;
    }

    void select(int cell) {
        selected = cell;
    }

    boolean enter(int d) {
        if (!canEdit()) return false;
        int c = selected;
        if (noteMode) {
            if (value[c] != 0) return false;
            touch(c);
            notes[c] ^= Sudoku.bit(d);
            return commit();
        }
        touch(c);
        if (value[c] == d) {
            value[c] = 0;
            return commit();
        }
        value[c] = d;
        notes[c] = 0;
        int b = Sudoku.bit(d);
        for (int p : Sudoku.PEERS[c]) {
            if ((notes[p] & b) != 0) {
                touch(p);
                notes[p] &= ~b;
            }
        }
        commit();
        checkSolved();
        return true;
    }

    boolean erase() {
        if (!canEdit() || (value[selected] == 0 && notes[selected] == 0)) return false;
        touch(selected);
        value[selected] = 0;
        notes[selected] = 0;
        return commit();
    }

    private void touch(int cell) {
        for (int i = 0; i < recordLength; i += 3) if (record[i] == cell) return;
        record[recordLength++] = cell;
        record[recordLength++] = value[cell];
        record[recordLength++] = notes[cell];
    }

    private boolean commit() {
        if (recordLength == 0) return false;
        history.add(Arrays.copyOf(record, recordLength));
        recordLength = 0;
        return true;
    }

    private void checkSolved() {
        for (int i = 0; i < 81; i++) if (value[i] != solution[i]) return;
        solved = true;
        selected = NONE;
    }

    boolean conflict(int cell) {
        int v = value[cell];
        if (v == 0) return false;
        for (int p : Sudoku.PEERS[cell]) if (value[p] == v) return true;
        return false;
    }

    boolean wrong(int cell) {
        return showErrors && given[cell] == 0 && value[cell] != 0 && value[cell] != solution[cell];
    }

    int remaining(int d) {
        int n = 9;
        for (int v : value) if (v == d) n--;
        return n;
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
        for (int i = 0; i < r.length; i += 3) {
            value[r[i]] = r[i + 1];
            notes[r[i]] = r[i + 2];
        }
        selected = r[0];
        return true;
    }

    boolean fillNotes() {
        if (!active || solved) return false;
        for (int i = 0; i < 81; i++) {
            if (value[i] != 0) continue;
            int m = Sudoku.candidates(value, i);
            if (notes[i] != m) {
                touch(i);
                notes[i] = m;
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

    boolean hint(Sudoku engine) {
        if (!active || solved) return false;
        if (hintActive() && hintKind == HINT_PLACE && value[hintCell] == 0) {
            noteMode = false;
            hintKind = HINT_NONE;
            return enter(hintDigit);
        }
        hintKind = HINT_NONE;
        for (int i = 0; i < 81; i++) {
            if (given[i] == 0 && value[i] != 0 && value[i] != solution[i]) {
                hintKind = HINT_WRONG;
                hintCell = i;
                selected = i;
                hintMoves = history.size();
                return true;
            }
        }
        if (!engine.hint(value)) return false;
        hintKind = HINT_PLACE;
        hintCell = engine.stepCell;
        hintDigit = engine.stepDigit;
        hintTech = engine.hintTech;
        hintUnit = engine.stepUnit;
        selected = hintCell;
        hintMoves = history.size();
        return true;
    }

    String encode(long now) {
        StringBuilder sb = new StringBuilder(700);
        sb.append(active ? 1 : 0).append('|').append(level).append('|').append(solved ? 1 : 0).append('|')
            .append(showErrors ? 1 : 0).append('|').append(noteMode ? 1 : 0).append('|').append(selected).append('|')
            .append(time(now)).append('|');
        appendDigits(sb, given);
        sb.append('|');
        appendDigits(sb, solution);
        sb.append('|');
        appendDigits(sb, value);
        sb.append('|');
        for (int m : notes) {
            sb.append((char) ('0' + (m >> 6))).append((char) ('0' + ((m >> 3) & 7))).append((char) ('0' + (m & 7)));
        }
        sb.append('|').append(history.size());
        for (int[] r : history) {
            sb.append('|');
            for (int i = 0; i < r.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(r[i]);
            }
        }
        return sb.toString();
    }

    private static void appendDigits(StringBuilder sb, int[] a) {
        for (int v : a) sb.append((char) ('0' + v));
    }

    private static void readDigits(String s, int[] a) {
        if (s.length() != 81) throw new IllegalArgumentException();
        for (int i = 0; i < 81; i++) {
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
            if (lvl < 0 || lvl > 3 || sel < NONE || sel > 80 || time < 0) return false;
            readDigits(f[7], given);
            readDigits(f[8], solution);
            readDigits(f[9], value);
            if (f[10].length() != 243) return false;
            for (int i = 0; i < 81; i++) {
                int m = 0;
                for (int k = 0; k < 3; k++) {
                    int o = f[10].charAt(i * 3 + k) - '0';
                    if (o < 0 || o > 7) return false;
                    m = (m << 3) | o;
                }
                notes[i] = m;
            }
            int n = Integer.parseInt(f[11]);
            if (n < 0 || f.length != 12 + n) return false;
            history.clear();
            for (int k = 0; k < n; k++) {
                String[] parts = f[12 + k].split(",");
                if (parts.length % 3 != 0) return false;
                int[] r = new int[parts.length];
                for (int i = 0; i < parts.length; i++) r[i] = Integer.parseInt(parts[i]);
                history.add(r);
            }
            recordLength = 0;
            level = lvl;
            solved = f[2].equals("1");
            noteMode = f[4].equals("1");
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
