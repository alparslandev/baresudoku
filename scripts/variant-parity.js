const [, , enginePath, count] = process.argv;
const { Variant, VARIANT_LEVELS } = require(require("path").resolve(enginePath));
const n = parseInt(count, 10);
const lines = [];
for (let kind = 0; kind < 3; kind++) {
  for (let level = 0; level < VARIANT_LEVELS; level++) {
    for (let seed = 1; seed <= n; seed++) {
      const v = new Variant(kind);
      v.seed(seed * 7919 + level);
      const puzzle = v.generate(level);
      const size = v.shape.size;
      const hints = [];
      const values = puzzle.slice();
      for (let step = 0; step < size && v.hint(values, puzzle); step++) {
        hints.push(v.stepCell + ":" + v.stepDigit + ":" + v.stepUnit);
        values[v.stepCell] = v.stepDigit;
      }
      lines.push([kind, level, seed, puzzle.join(""), Array.from(v.shape.cageOf.subarray(0, size)).join(","), Array.from(v.shape.cageSum.subarray(0, v.shape.cageCount)).join(","), hints.join(",")].join(" "));
    }
  }
}
process.stdout.write(lines.join("\n") + "\n");
