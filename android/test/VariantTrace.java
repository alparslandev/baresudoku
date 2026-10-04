package com.baresudoku;

final class VariantTrace {
    public static void main(String[] args) {
        int n = Integer.parseInt(args[0]);
        StringBuilder out = new StringBuilder();
        for (int kind = 0; kind < 3; kind++) {
            for (int level = 0; level < Variant.LEVELS; level++) {
                for (int seed = 1; seed <= n; seed++) {
                    Variant v = new Variant(kind, seed * 7919 + level);
                    int[] puzzle = v.generate(level);
                    int size = v.shape.size;
                    out.append(kind).append(' ').append(level).append(' ').append(seed).append(' ');
                    for (int c = 0; c < size; c++) out.append(puzzle[c]);
                    out.append(' ');
                    for (int c = 0; c < size; c++) out.append(c > 0 ? "," : "").append(v.shape.cageOf[c]);
                    out.append(' ');
                    for (int k = 0; k < v.shape.cageCount; k++) out.append(k > 0 ? "," : "").append(v.shape.cageSum[k]);
                    out.append(' ');
                    int[] values = puzzle.clone();
                    for (int step = 0; step < size && v.hint(values, puzzle); step++) {
                        out.append(step > 0 ? "," : "").append(v.stepCell).append(':').append(v.stepDigit).append(':').append(v.stepUnit);
                        values[v.stepCell] = v.stepDigit;
                    }
                    out.append('\n');
                }
            }
        }
        System.out.print(out);
    }
}
