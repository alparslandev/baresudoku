'use strict';
const fs = require('fs');
const path = require('path');
const { Sudoku } = require(path.resolve(process.argv[2]));
const count = +process.argv[3] || 1000;
const file = process.argv[4];

const e = new Sudoku();
const lines = [];
for (let i = 0; i < count; i++) {
  const puzzle = e.fullGrid();
  const order = [];
  for (let c = 0; c < 81; c++) order.push(c);
  e.shuffle(order);
  for (const c of order) {
    const v = puzzle[c];
    puzzle[c] = 0;
    if (e.countSolutions(puzzle, 2) !== 1) puzzle[c] = v;
  }
  lines.push(puzzle.join(''));
}
fs.writeFileSync(file, lines.join('\n') + '\n');

let h1 = 0, h2 = 0;
const mix = x => {
  h1 = Math.imul(h1 ^ x, 16777619) >>> 0;
  h2 = (Math.imul(h2, 31) + x) >>> 0;
};
const out = [];
lines.forEach((line, index) => {
  const puzzle = Array.from(line, ch => ch.charCodeAt(0) - 48);
  h1 = 0x811c9dc5;
  h2 = 0;
  e.load(puzzle);
  let steps = 0;
  while (!e.complete() && !e.stuck()) {
    const t = e.step();
    mix(t);
    if (t < 0) break;
    steps++;
    mix(e.stepRating);
    if (t === 0) {
      mix(e.stepCell);
      mix(e.stepDigit);
    }
    for (let c = 0; c < 81; c++) mix(e.lc[c]);
  }
  const rating = e.rate(puzzle);
  const hinted = e.hint(puzzle, puzzle) ? e.hintTech + '/' + e.stepCell + '/' + e.stepDigit : 'x';
  out.push(index + ' ' + steps + ' ' + rating + ' ' + e.rateOrder + ' ' + e.rateTech + ' ' + hinted + ' ' + h1.toString(16) + ' ' + h2.toString(16));
});
process.stdout.write(out.join('\n') + '\n');
