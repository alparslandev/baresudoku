package com.baresudoku;

import java.util.Arrays;
import java.util.Random;

final class Sudoku {
    static final int ALL = 0x1FF;
    static final int[] ROW = new int[81];
    static final int[] COL = new int[81];
    static final int[] BOX = new int[81];
    static final int[][] UNITS = new int[27][9];
    static final int[][] PEERS = new int[81][20];

    static {
        for (int i = 0; i < 81; i++) {
            ROW[i] = i / 9;
            COL[i] = i % 9;
            BOX[i] = (i / 27) * 3 + (i % 9) / 3;
        }
        for (int u = 0; u < 9; u++) {
            for (int k = 0; k < 9; k++) {
                UNITS[u][k] = u * 9 + k;
                UNITS[9 + u][k] = k * 9 + u;
                UNITS[18 + u][k] = (u / 3) * 27 + (u % 3) * 3 + (k / 3) * 9 + k % 3;
            }
        }
        for (int i = 0; i < 81; i++) {
            int n = 0;
            for (int j = 0; j < 81; j++) {
                if (sees(i, j)) PEERS[i][n++] = j;
            }
        }
    }

    final Random random;

    Sudoku() {
        random = new Random();
    }

    Sudoku(long seed) {
        random = new Random(seed);
    }

    static boolean sees(int a, int b) {
        return a != b && (ROW[a] == ROW[b] || COL[a] == COL[b] || BOX[a] == BOX[b]);
    }

    static int bit(int digit) {
        return 1 << (digit - 1);
    }

    static int digit(int singleBit) {
        return Integer.numberOfTrailingZeros(singleBit) + 1;
    }

    static int candidates(int[] values, int cell) {
        int used = 0;
        for (int p : PEERS[cell]) {
            if (values[p] != 0) used |= bit(values[p]);
        }
        return ALL & ~used;
    }

    private final int[] work = new int[81];
    private int count;
    private int limit;
    int[] found;

    int countSolutions(int[] puzzle, int max) {
        System.arraycopy(puzzle, 0, work, 0, 81);
        count = 0;
        limit = max;
        found = null;
        search();
        return count;
    }

    private void search() {
        int best = -1;
        int bestMask = 0;
        int bestSize = 10;
        for (int i = 0; i < 81; i++) {
            if (work[i] != 0) continue;
            int m = candidates(work, i);
            int n = Integer.bitCount(m);
            if (n == 0) return;
            if (n < bestSize) {
                bestSize = n;
                best = i;
                bestMask = m;
                if (n == 1) break;
            }
        }
        if (best < 0) {
            count++;
            if (found == null) found = work.clone();
            return;
        }
        for (int m = bestMask; m != 0 && count < limit; m &= m - 1) {
            work[best] = digit(m & -m);
            search();
        }
        work[best] = 0;
    }

    int[] fullGrid() {
        Arrays.fill(work, 0);
        fillRandom();
        return work.clone();
    }

    private boolean fillRandom() {
        int best = -1;
        int bestMask = 0;
        int bestSize = 10;
        for (int i = 0; i < 81; i++) {
            if (work[i] != 0) continue;
            int m = candidates(work, i);
            int n = Integer.bitCount(m);
            if (n == 0) return false;
            if (n < bestSize) {
                bestSize = n;
                best = i;
                bestMask = m;
                if (n == 1) break;
            }
        }
        if (best < 0) return true;
        int[] digits = new int[bestSize];
        int n = 0;
        for (int m = bestMask; m != 0; m &= m - 1) digits[n++] = digit(m & -m);
        shuffle(digits);
        for (int d : digits) {
            work[best] = d;
            if (fillRandom()) return true;
        }
        work[best] = 0;
        return false;
    }

    private void shuffle(int[] a) {
        for (int k = a.length - 1; k > 0; k--) {
            int j = random.nextInt(k + 1);
            int t = a[k];
            a[k] = a[j];
            a[j] = t;
        }
    }

    int[] dig(int[] full, int minClues) {
        int[] puzzle = full.clone();
        int[] order = new int[41];
        for (int i = 0; i < 41; i++) order[i] = i;
        shuffle(order);
        int clues = 81;
        for (int k = 0; k < 41 && clues > minClues; k++) {
            int a = order[k];
            int b = 80 - a;
            int va = puzzle[a];
            int vb = puzzle[b];
            puzzle[a] = 0;
            puzzle[b] = 0;
            if (keeps(puzzle)) {
                clues -= a == b ? 1 : 2;
            } else {
                puzzle[a] = va;
                puzzle[b] = vb;
            }
        }
        return puzzle;
    }

    private boolean keeps(int[] puzzle) {
        return countSolutions(puzzle, 2) == 1;
    }

    private final int[] lv = new int[81];
    private final int[] lc = new int[81];
    int stepCell;
    int stepDigit;
    int stepUnit;

    void load(int[] values) {
        System.arraycopy(values, 0, lv, 0, 81);
        for (int i = 0; i < 81; i++) lc[i] = lv[i] == 0 ? candidates(lv, i) : 0;
    }

    int valueAt(int cell) {
        return lv[cell];
    }

    int candidatesAt(int cell) {
        return lc[cell];
    }

    boolean complete() {
        for (int i = 0; i < 81; i++) if (lv[i] == 0) return false;
        return true;
    }

    boolean stuck() {
        for (int i = 0; i < 81; i++) if (lv[i] == 0 && lc[i] == 0) return true;
        return false;
    }

    private void place(int cell, int d, int unit) {
        lv[cell] = d;
        lc[cell] = 0;
        int keep = ~bit(d);
        for (int p : PEERS[cell]) lc[p] &= keep;
        stepCell = cell;
        stepDigit = d;
        stepUnit = unit;
    }

    boolean singles() {
        for (int i = 0; i < 81; i++) {
            if (lv[i] == 0 && Integer.bitCount(lc[i]) == 1) {
                place(i, digit(lc[i]), 3);
                return true;
            }
        }
        for (int u = 0; u < 27; u++) {
            int[] cells = UNITS[u];
            for (int d = 1; d <= 9; d++) {
                int b = bit(d);
                int where = -1;
                int n = 0;
                for (int k = 0; k < 9; k++) {
                    if ((lc[cells[k]] & b) != 0) {
                        n++;
                        where = cells[k];
                    }
                }
                if (n == 1) {
                    place(where, d, u / 9);
                    return true;
                }
            }
        }
        return false;
    }

    boolean lockedCandidates() {
        boolean changed = false;
        for (int d = 1; d <= 9; d++) {
            int b = bit(d);
            for (int box = 0; box < 9; box++) {
                int rows = 0;
                int cols = 0;
                for (int k = 0; k < 9; k++) {
                    int c = UNITS[18 + box][k];
                    if ((lc[c] & b) != 0) {
                        rows |= 1 << ROW[c];
                        cols |= 1 << COL[c];
                    }
                }
                if (rows != 0 && Integer.bitCount(rows) == 1) {
                    changed |= clearLineOutsideBox(UNITS[Integer.numberOfTrailingZeros(rows)], box, b);
                }
                if (cols != 0 && Integer.bitCount(cols) == 1) {
                    changed |= clearLineOutsideBox(UNITS[9 + Integer.numberOfTrailingZeros(cols)], box, b);
                }
            }
            for (int line = 0; line < 18; line++) {
                int boxes = 0;
                for (int k = 0; k < 9; k++) {
                    int c = UNITS[line][k];
                    if ((lc[c] & b) != 0) boxes |= 1 << BOX[c];
                }
                if (boxes != 0 && Integer.bitCount(boxes) == 1) {
                    int box = Integer.numberOfTrailingZeros(boxes);
                    for (int k = 0; k < 9; k++) {
                        int c = UNITS[18 + box][k];
                        boolean inLine = line < 9 ? ROW[c] == line : COL[c] == line - 9;
                        if (!inLine && (lc[c] & b) != 0) {
                            lc[c] &= ~b;
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean clearLineOutsideBox(int[] line, int box, int b) {
        boolean changed = false;
        for (int k = 0; k < 9; k++) {
            int c = line[k];
            if (BOX[c] != box && (lc[c] & b) != 0) {
                lc[c] &= ~b;
                changed = true;
            }
        }
        return changed;
    }
}
