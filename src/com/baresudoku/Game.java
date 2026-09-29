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
        runningSince = 0;
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
        if (active && !solved && runningSince == 0) runningSince = now;
    }

    void pause(long now) {
        if (runningSince != 0) {
            elapsed += now - runningSince;
            runningSince = 0;
        }
    }

    long time(long now) {
        return elapsed + (runningSince == 0 ? 0 : now - runningSince);
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
}
