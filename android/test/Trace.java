package com.baresudoku;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

final class Trace {
    static int h1;
    static int h2;

    static void mix(int x) {
        h1 = (h1 ^ x) * 16777619;
        h2 = h2 * 31 + x;
    }

    public static void main(String[] args) throws IOException {
        Sudoku e = new Sudoku(1);
        StringBuilder out = new StringBuilder();
        int index = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(args[0]))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.length() < 81) continue;
                int[] puzzle = new int[81];
                for (int c = 0; c < 81; c++) puzzle[c] = line.charAt(c) - '0';
                h1 = 0x811c9dc5;
                h2 = 0;
                e.load(puzzle);
                int steps = 0;
                while (!e.complete() && !e.stuck()) {
                    int t = e.step();
                    mix(t);
                    if (t < 0) break;
                    steps++;
                    mix(e.stepRating);
                    if (t == 0) {
                        mix(e.stepCell);
                        mix(e.stepDigit);
                    }
                    for (int c = 0; c < 81; c++) mix(e.candidatesAt(c));
                }
                int rating = e.rate(puzzle);
                String hinted = e.hint(puzzle, puzzle) ? e.hintTech + "/" + e.stepCell + "/" + e.stepDigit : "x";
                out.append(index).append(' ').append(steps).append(' ').append(rating).append(' ').append(e.rateOrder).append(' ').append(e.rateTech)
                    .append(' ').append(hinted).append(' ').append(Integer.toHexString(h1)).append(' ').append(Integer.toHexString(h2)).append('\n');
                index++;
            }
        }
        System.out.print(out);
    }
}
