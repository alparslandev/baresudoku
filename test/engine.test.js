import { test, expect } from "bun:test";
const { Sudoku, Game, candidates, bit, PEERS, HINT_WRONG, HINT_PLACE, NONE } = require("../site/engine.js");

const LEVELS = ["Kolay", "Orta", "Zor", "Uzman"];
const count = values => values.filter(v => v !== 0).length;

function verifiedRate(e, puzzle, solution) {
  e.load(puzzle);
  let max = 0;
  while (!e.complete()) {
    expect(e.stuck()).toBe(false);
    const t = e.step();
    expect(t).toBeGreaterThanOrEqual(0);
    if (t > max) max = t;
    for (let i = 0; i < 81; i++) {
      if (e.lv[i]) expect(e.lv[i]).toBe(solution[i]);
      else expect(e.lc[i] & bit(solution[i])).not.toBe(0);
    }
  }
  return max;
}

for (let level = 0; level < 4; level++) {
  test(`${LEVELS[level]}: teklik, derece, eleme dogrulugu, ipucu yuruyusu`, () => {
    const e = new Sudoku();
    const n = 25;
    const start = performance.now();
    const clues = [];
    for (let i = 0; i < n; i++) {
      const puzzle = e.generate(level);
      const solution = e.solution;
      expect(e.countSolutions(puzzle, 2)).toBe(1);
      expect(e.found).toEqual(solution);
      const r = verifiedRate(e, puzzle, solution);
      if (level < 2) expect(r).toBe(0);
      else if (level === 2) expect(r >= 1 && r <= 2).toBe(true);
      else expect(r >= 3 && r <= 6).toBe(true);
      clues.push(count(puzzle));
      const values = puzzle.slice();
      let steps = 0;
      while (count(values) < 81) {
        expect(e.hint(values)).toBe(true);
        expect(values[e.stepCell]).toBe(0);
        expect(solution[e.stepCell]).toBe(e.stepDigit);
        values[e.stepCell] = e.stepDigit;
        expect(++steps).toBeLessThanOrEqual(81);
      }
    }
    const ms = (performance.now() - start) / n;
    console.log(`${LEVELS[level]}: ${n} bulmaca, ort ipucu ${(clues.reduce((a, b) => a + b, 0) / n).toFixed(1)}, ${ms.toFixed(1)} ms/bulmaca`);
  });
}

test("oyun durumu: hamle, not, geri al, not doldurma, ipucu, sure, kayit", () => {
  const e = new Sudoku();
  const puzzle = e.generate(1);
  const g = new Game();
  g.start(puzzle, e.solution, 1);
  const cell = puzzle.indexOf(0);
  const right = e.solution[cell];
  const wrong = right === 9 ? 1 : right + 1;
  g.select(cell);
  expect(g.enter(wrong)).toBe(true);
  expect(g.value[cell]).toBe(wrong);
  expect(g.wrong(cell)).toBe(true);
  expect(g.hint(e)).toBe(true);
  expect(g.hintKind).toBe(HINT_WRONG);
  expect(g.hintCell).toBe(cell);
  expect(g.undo()).toBe(true);
  expect(g.value[cell]).toBe(0);
  expect(g.selected).toBe(cell);
  g.noteMode = true;
  expect(g.enter(right)).toBe(true);
  expect(g.notes[cell]).toBe(bit(right));
  expect(g.enter(right)).toBe(true);
  expect(g.notes[cell]).toBe(0);
  g.noteMode = false;
  expect(g.fillNotes()).toBe(true);
  for (let i = 0; i < 81; i++) if (!g.value[i]) expect(g.notes[i]).toBe(candidates(g.value, i));
  expect(g.fillNotes()).toBe(false);
  expect(g.hint(e)).toBe(true);
  expect(g.hintActive()).toBe(true);
  expect(g.hintKind).toBe(HINT_PLACE);
  const hc = g.hintCell, hd = g.hintDigit;
  expect(hd).toBe(e.solution[hc]);
  expect(g.hint(e)).toBe(true);
  expect(g.value[hc]).toBe(hd);
  for (const p of PEERS[hc]) expect(g.notes[p] & bit(hd)).toBe(0);
  expect(g.undo()).toBe(true);
  expect(g.value[hc]).toBe(0);
  g.resume(0);
  g.pause(500);
  expect(g.time(9999)).toBe(500);
  const saved = JSON.parse(JSON.stringify(g.save(1000)));
  const h = new Game();
  expect(h.load(saved)).toBe(true);
  expect(JSON.stringify(h.save(0))).toBe(JSON.stringify(saved));
  expect(h.history.length).toBe(g.history.length);
  expect(h.undo()).toBe(true);
  expect(new Game().load({ v: 1, active: true, given: "bozuk" })).toBe(false);
  expect(new Game().load(null)).toBe(false);
  const empty = new Game();
  expect(empty.load({ v: 1, active: false, showErrors: false })).toBe(true);
  expect(empty.active).toBe(false);
  expect(empty.showErrors).toBe(false);
  for (let i = 0; i < 81; i++) {
    if (g.value[i]) continue;
    g.select(i);
    expect(g.enter(e.solution[i])).toBe(true);
  }
  expect(g.solved).toBe(true);
  expect(g.selected).toBe(NONE);
  expect(g.enter(1)).toBe(false);
  expect(g.undo()).toBe(false);
});
