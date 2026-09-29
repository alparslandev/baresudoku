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
}
