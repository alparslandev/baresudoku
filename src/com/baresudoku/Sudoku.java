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
}
