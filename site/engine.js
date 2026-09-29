'use strict';
const ALL = 0x1ff;
const ROW = new Uint8Array(81);
const COL = new Uint8Array(81);
const BOX = new Uint8Array(81);
const UNITS = [];
const PEERS = [];
for (let i = 0; i < 81; i++) {
  ROW[i] = (i / 9) | 0;
  COL[i] = i % 9;
  BOX[i] = ((i / 27) | 0) * 3 + (((i % 9) / 3) | 0);
}
for (let u = 0; u < 9; u++) {
  const r = [], c = [], b = [];
  for (let k = 0; k < 9; k++) {
    r.push(u * 9 + k);
    c.push(k * 9 + u);
    b.push(((u / 3) | 0) * 27 + (u % 3) * 3 + ((k / 3) | 0) * 9 + (k % 3));
  }
  UNITS[u] = r;
  UNITS[9 + u] = c;
  UNITS[18 + u] = b;
}
const sees = (a, b) => a !== b && (ROW[a] === ROW[b] || COL[a] === COL[b] || BOX[a] === BOX[b]);
for (let i = 0; i < 81; i++) {
  const p = [];
  for (let j = 0; j < 81; j++) if (sees(i, j)) p.push(j);
  PEERS[i] = p;
}
const POP = new Uint8Array(512);
for (let m = 1; m < 512; m++) POP[m] = POP[m >> 1] + (m & 1);
const bit = d => 1 << (d - 1);
const digit = m => 32 - Math.clz32(m & -m);
const low = m => 31 - Math.clz32(m & -m);
function candidates(values, cell) {
  let used = 0;
  const peers = PEERS[cell];
  for (let k = 0; k < 20; k++) {
    const v = values[peers[k]];
    if (v) used |= bit(v);
  }
  return ALL & ~used;
}

class Sudoku {
  constructor() {
    this.work = new Uint8Array(81);
    this.lv = new Uint8Array(81);
    this.lc = new Uint16Array(81);
    this.positions = new Uint16Array(10);
    this.lineMasks = new Uint16Array(9);
    this.count = 0;
    this.limit = 0;
    this.found = null;
    this.stepCell = 0;
    this.stepDigit = 0;
    this.stepUnit = 0;
    this.hintTech = 0;
    this.allowed = 0;
    this.solution = null;
  }

  countSolutions(puzzle, max) {
    this.work.set(puzzle);
    this.count = 0;
    this.limit = max;
    this.found = null;
    this.search();
    return this.count;
  }

  search() {
    const w = this.work;
    let best = -1, bestMask = 0, bestSize = 10;
    for (let i = 0; i < 81; i++) {
      if (w[i]) continue;
      const m = candidates(w, i);
      const n = POP[m];
      if (n === 0) return;
      if (n < bestSize) {
        bestSize = n;
        best = i;
        bestMask = m;
        if (n === 1) break;
      }
    }
    if (best < 0) {
      this.count++;
      if (!this.found) this.found = Array.from(w);
      return;
    }
    for (let m = bestMask; m && this.count < this.limit; m &= m - 1) {
      w[best] = digit(m & -m);
      this.search();
    }
    w[best] = 0;
  }

  fullGrid() {
    this.work.fill(0);
    this.fillRandom();
    return Array.from(this.work);
  }

  fillRandom() {
    const w = this.work;
    let best = -1, bestMask = 0, bestSize = 10;
    for (let i = 0; i < 81; i++) {
      if (w[i]) continue;
      const m = candidates(w, i);
      const n = POP[m];
      if (n === 0) return false;
      if (n < bestSize) {
        bestSize = n;
        best = i;
        bestMask = m;
        if (n === 1) break;
      }
    }
    if (best < 0) return true;
    const digits = [];
    for (let m = bestMask; m; m &= m - 1) digits.push(digit(m & -m));
    this.shuffle(digits);
    for (const d of digits) {
      w[best] = d;
      if (this.fillRandom()) return true;
    }
    w[best] = 0;
    return false;
  }

  shuffle(a) {
    for (let k = a.length - 1; k > 0; k--) {
      const j = (Math.random() * (k + 1)) | 0;
      const t = a[k];
      a[k] = a[j];
      a[j] = t;
    }
  }

  dig(full, minClues) {
    const puzzle = full.slice();
    const order = [];
    for (let i = 0; i < 41; i++) order.push(i);
    this.shuffle(order);
    let clues = 81;
    for (let k = 0; k < 41 && clues > minClues; k++) {
      const a = order[k], b = 80 - a;
      const va = puzzle[a], vb = puzzle[b];
      puzzle[a] = 0;
      puzzle[b] = 0;
      if (this.keeps(puzzle)) clues -= a === b ? 1 : 2;
      else {
        puzzle[a] = va;
        puzzle[b] = vb;
      }
    }
    return puzzle;
  }

  keeps(puzzle) {
    return this.countSolutions(puzzle, 2) === 1 && this.withinAllowed(puzzle);
  }

  load(values) {
    this.lv.set(values);
    for (let i = 0; i < 81; i++) this.lc[i] = this.lv[i] ? 0 : candidates(this.lv, i);
  }

  complete() {
    for (let i = 0; i < 81; i++) if (!this.lv[i]) return false;
    return true;
  }

  stuck() {
    for (let i = 0; i < 81; i++) if (!this.lv[i] && !this.lc[i]) return true;
    return false;
  }

  place(cell, d, unit) {
    this.lv[cell] = d;
    this.lc[cell] = 0;
    const keep = ~bit(d);
    const peers = PEERS[cell];
    for (let k = 0; k < 20; k++) this.lc[peers[k]] &= keep;
    this.stepCell = cell;
    this.stepDigit = d;
    this.stepUnit = unit;
  }

  singles() {
    const lc = this.lc;
    for (let i = 0; i < 81; i++) {
      if (!this.lv[i] && POP[lc[i]] === 1) {
        this.place(i, digit(lc[i]), 3);
        return true;
      }
    }
    for (let u = 0; u < 27; u++) {
      const cells = UNITS[u];
      for (let d = 1; d <= 9; d++) {
        const b = bit(d);
        let where = -1, n = 0;
        for (let k = 0; k < 9; k++) {
          if (lc[cells[k]] & b) {
            n++;
            where = cells[k];
          }
        }
        if (n === 1) {
          this.place(where, d, (u / 9) | 0);
          return true;
        }
      }
    }
    return false;
  }

  lockedCandidates() {
    const lc = this.lc;
    let changed = false;
    for (let d = 1; d <= 9; d++) {
      const b = bit(d);
      for (let box = 0; box < 9; box++) {
        let rows = 0, cols = 0;
        const cells = UNITS[18 + box];
        for (let k = 0; k < 9; k++) {
          const c = cells[k];
          if (lc[c] & b) {
            rows |= 1 << ROW[c];
            cols |= 1 << COL[c];
          }
        }
        if (rows && POP[rows] === 1) changed = this.clearLineOutsideBox(UNITS[low(rows)], box, b) || changed;
        if (cols && POP[cols] === 1) changed = this.clearLineOutsideBox(UNITS[9 + low(cols)], box, b) || changed;
      }
      for (let line = 0; line < 18; line++) {
        let boxes = 0;
        const cells = UNITS[line];
        for (let k = 0; k < 9; k++) if (lc[cells[k]] & b) boxes |= 1 << BOX[cells[k]];
        if (boxes && POP[boxes] === 1) {
          const box = low(boxes);
          const bc = UNITS[18 + box];
          for (let k = 0; k < 9; k++) {
            const c = bc[k];
            const inLine = line < 9 ? ROW[c] === line : COL[c] === line - 9;
            if (!inLine && (lc[c] & b)) {
              lc[c] &= ~b;
              changed = true;
            }
          }
        }
      }
    }
    return changed;
  }

  clearLineOutsideBox(line, box, b) {
    let changed = false;
    for (let k = 0; k < 9; k++) {
      const c = line[k];
      if (BOX[c] !== box && (this.lc[c] & b)) {
        this.lc[c] &= ~b;
        changed = true;
      }
    }
    return changed;
  }

  subsets() {
    const lc = this.lc, positions = this.positions;
    let changed = false;
    for (let u = 0; u < 27; u++) {
      const cells = UNITS[u];
      for (let a = 0; a < 9; a++) {
        const ma = lc[cells[a]];
        if (!ma || POP[ma] > 3) continue;
        for (let b = a + 1; b < 9; b++) {
          const mb = lc[cells[b]];
          if (!mb || POP[mb] > 3) continue;
          const m2 = ma | mb;
          if (POP[m2] === 2) changed = this.clearOthers(cells, m2, (1 << a) | (1 << b)) || changed;
          else if (POP[m2] === 3) {
            for (let c = b + 1; c < 9; c++) {
              const mc = lc[cells[c]];
              if (mc && (mc | m2) === m2) changed = this.clearOthers(cells, m2, (1 << a) | (1 << b) | (1 << c)) || changed;
            }
          }
        }
      }
      for (let d = 1; d <= 9; d++) {
        const b = bit(d);
        let m = 0;
        for (let k = 0; k < 9; k++) if (lc[cells[k]] & b) m |= 1 << k;
        positions[d] = m;
      }
      for (let d1 = 1; d1 <= 9; d1++) {
        const p1 = positions[d1];
        if (!p1 || POP[p1] > 3) continue;
        for (let d2 = d1 + 1; d2 <= 9; d2++) {
          const p2 = positions[d2];
          if (!p2 || POP[p2] > 3) continue;
          const u2 = p1 | p2;
          if (POP[u2] === 2) changed = this.keepOnly(cells, u2, bit(d1) | bit(d2)) || changed;
          else if (POP[u2] === 3) {
            for (let d3 = d2 + 1; d3 <= 9; d3++) {
              const p3 = positions[d3];
              if (p3 && (p3 | u2) === u2) changed = this.keepOnly(cells, u2, bit(d1) | bit(d2) | bit(d3)) || changed;
            }
          }
        }
      }
    }
    return changed;
  }

  clearOthers(cells, digits, members) {
    let changed = false;
    for (let k = 0; k < 9; k++) {
      if (!(members & (1 << k)) && (this.lc[cells[k]] & digits)) {
        this.lc[cells[k]] &= ~digits;
        changed = true;
      }
    }
    return changed;
  }

  keepOnly(cells, members, digits) {
    let changed = false;
    for (let k = 0; k < 9; k++) {
      if ((members & (1 << k)) && (this.lc[cells[k]] & ~digits & ALL)) {
        this.lc[cells[k]] &= digits;
        changed = true;
      }
    }
    return changed;
  }

  fish(size) {
    const lc = this.lc, masks = this.lineMasks;
    let changed = false;
    for (let d = 1; d <= 9; d++) {
      const b = bit(d);
      for (let t = 0; t < 2; t++) {
        for (let line = 0; line < 9; line++) {
          let m = 0;
          const cells = UNITS[t * 9 + line];
          for (let k = 0; k < 9; k++) if (lc[cells[k]] & b) m |= 1 << k;
          masks[line] = m;
        }
        for (let l1 = 0; l1 < 9; l1++) {
          const m1 = masks[l1];
          if (!m1 || POP[m1] > size) continue;
          for (let l2 = l1 + 1; l2 < 9; l2++) {
            const m2 = masks[l2];
            if (!m2 || POP[m2] > size) continue;
            const u2 = m1 | m2;
            if (size === 2) {
              if (POP[u2] === 2) changed = this.fishClear(b, t, u2, (1 << l1) | (1 << l2)) || changed;
            } else if (POP[u2] <= 3) {
              for (let l3 = l2 + 1; l3 < 9; l3++) {
                const m3 = masks[l3];
                if (!m3 || POP[m3] > 3) continue;
                const u3 = u2 | m3;
                if (POP[u3] === 3) changed = this.fishClear(b, t, u3, (1 << l1) | (1 << l2) | (1 << l3)) || changed;
              }
            }
          }
        }
      }
    }
    return changed;
  }

  fishClear(b, t, coverMask, baseMask) {
    let changed = false;
    for (let c = 0; c < 81; c++) {
      const base = t === 0 ? ROW[c] : COL[c];
      const cover = t === 0 ? COL[c] : ROW[c];
      if ((coverMask & (1 << cover)) && !(baseMask & (1 << base)) && (this.lc[c] & b)) {
        this.lc[c] &= ~b;
        changed = true;
      }
    }
    return changed;
  }

  yWing() {
    const lc = this.lc;
    for (let p = 0; p < 81; p++) {
      const pm = lc[p];
      if (POP[pm] !== 2) continue;
      const peers = PEERS[p];
      for (let a = 0; a < 20; a++) {
        const am = lc[peers[a]];
        if (POP[am] !== 2 || am === pm || POP[am & pm] !== 1) continue;
        for (let b = a + 1; b < 20; b++) {
          const bm = lc[peers[b]];
          if (POP[bm] !== 2 || bm === pm || POP[bm & pm] !== 1) continue;
          if ((am & pm) === (bm & pm)) continue;
          const z = am & bm & ~pm;
          if (POP[z] !== 1) continue;
          if (this.clearSeeing(z, peers[a], peers[b], -1)) return true;
        }
      }
    }
    return false;
  }

  clearSeeing(z, c1, c2, c3) {
    let changed = false;
    for (let c = 0; c < 81; c++) {
      if (c === c1 || c === c2 || c === c3 || !(this.lc[c] & z)) continue;
      if (!sees(c, c1) || !sees(c, c2) || (c3 >= 0 && !sees(c, c3))) continue;
      this.lc[c] &= ~z;
      changed = true;
    }
    return changed;
  }

  xyzWing() {
    const lc = this.lc;
    for (let p = 0; p < 81; p++) {
      const pm = lc[p];
      if (POP[pm] !== 3) continue;
      const peers = PEERS[p];
      for (let a = 0; a < 20; a++) {
        const am = lc[peers[a]];
        if (POP[am] !== 2 || (am & pm) !== am) continue;
        for (let b = a + 1; b < 20; b++) {
          const bm = lc[peers[b]];
          if (POP[bm] !== 2 || (bm & pm) !== bm || bm === am) continue;
          const z = am & bm;
          if (POP[z] !== 1) continue;
          if (this.clearSeeing(z, peers[a], peers[b], p)) return true;
        }
      }
    }
    return false;
  }

  step() {
    if (this.singles()) return 0;
    if (this.lockedCandidates()) return 1;
    if (this.subsets()) return 2;
    if (this.fish(2)) return 3;
    if (this.yWing()) return 4;
    if (this.fish(3)) return 5;
    if (this.xyzWing()) return 6;
    return -1;
  }

  rate(puzzle) {
    this.load(puzzle);
    let max = 0;
    while (!this.complete()) {
      if (this.stuck()) return -1;
      const t = this.step();
      if (t < 0) return -1;
      if (t > max) max = t;
    }
    return max;
  }

  withinAllowed(puzzle) {
    const r = this.rate(puzzle);
    return r >= 0 && r <= this.allowed;
  }

  hint(values) {
    this.load(values);
    this.hintTech = 0;
    while (!this.complete() && !this.stuck()) {
      const t = this.step();
      if (t < 0) return false;
      if (t === 0) return true;
      if (t > this.hintTech) this.hintTech = t;
    }
    return false;
  }

  generate(level) {
    this.allowed = level < 2 ? 0 : level === 2 ? 2 : 6;
    const minClues = level === 0 ? 38 : 0;
    for (;;) {
      const full = this.fullGrid();
      const puzzle = this.dig(full, minClues);
      const r = this.rate(puzzle);
      const ok = level === 2 ? r >= 1 : level === 3 ? r >= 3 : r === 0;
      if (ok) {
        this.solution = full;
        return puzzle;
      }
    }
  }
}

const NONE = -1;
const HINT_NONE = 0, HINT_WRONG = 1, HINT_PLACE = 2;

class Game {
  constructor() {
    this.given = new Array(81).fill(0);
    this.solution = new Array(81).fill(0);
    this.value = new Array(81).fill(0);
    this.notes = new Array(81).fill(0);
    this.history = [];
    this.record = [];
    this.active = false;
    this.solved = false;
    this.noteMode = false;
    this.showErrors = true;
    this.level = 0;
    this.selected = NONE;
    this.elapsed = 0;
    this.running = false;
    this.runningSince = 0;
    this.hintKind = HINT_NONE;
    this.hintCell = NONE;
    this.hintDigit = 0;
    this.hintTech = 0;
    this.hintUnit = 0;
    this.hintMoves = 0;
  }

  start(puzzle, full, level) {
    for (let i = 0; i < 81; i++) {
      this.given[i] = puzzle[i];
      this.solution[i] = full[i];
      this.value[i] = puzzle[i];
      this.notes[i] = 0;
    }
    this.history = [];
    this.record = [];
    this.active = true;
    this.solved = false;
    this.noteMode = false;
    this.level = level;
    this.selected = NONE;
    this.elapsed = 0;
    this.running = false;
    this.hintKind = HINT_NONE;
  }

  canEdit() {
    return this.active && !this.solved && this.selected >= 0 && this.given[this.selected] === 0;
  }

  select(cell) {
    this.selected = cell;
  }

  enter(d) {
    if (!this.canEdit()) return false;
    const c = this.selected;
    if (this.noteMode) {
      if (this.value[c]) return false;
      this.touch(c);
      this.notes[c] ^= bit(d);
      return this.commit();
    }
    this.touch(c);
    if (this.value[c] === d) {
      this.value[c] = 0;
      return this.commit();
    }
    this.value[c] = d;
    this.notes[c] = 0;
    const b = bit(d);
    for (const p of PEERS[c]) {
      if (this.notes[p] & b) {
        this.touch(p);
        this.notes[p] &= ~b;
      }
    }
    this.commit();
    this.checkSolved();
    return true;
  }

  erase() {
    if (!this.canEdit() || (!this.value[this.selected] && !this.notes[this.selected])) return false;
    this.touch(this.selected);
    this.value[this.selected] = 0;
    this.notes[this.selected] = 0;
    return this.commit();
  }

  touch(cell) {
    for (let i = 0; i < this.record.length; i += 3) if (this.record[i] === cell) return;
    this.record.push(cell, this.value[cell], this.notes[cell]);
  }

  commit() {
    if (!this.record.length) return false;
    this.history.push(this.record);
    this.record = [];
    return true;
  }

  checkSolved() {
    for (let i = 0; i < 81; i++) if (this.value[i] !== this.solution[i]) return;
    this.solved = true;
    this.selected = NONE;
  }

  conflict(cell) {
    const v = this.value[cell];
    if (!v) return false;
    for (const p of PEERS[cell]) if (this.value[p] === v) return true;
    return false;
  }

  wrong(cell) {
    return this.showErrors && !this.given[cell] && this.value[cell] !== 0 && this.value[cell] !== this.solution[cell];
  }

  remaining(d) {
    let n = 9;
    for (const v of this.value) if (v === d) n--;
    return n;
  }

  resume(now) {
    if (this.active && !this.solved && !this.running) {
      this.running = true;
      this.runningSince = now;
    }
  }

  pause(now) {
    if (this.running) {
      this.elapsed += now - this.runningSince;
      this.running = false;
    }
  }

  time(now) {
    return this.elapsed + (this.running ? now - this.runningSince : 0);
  }

  undo() {
    if (!this.active || this.solved || !this.history.length) return false;
    const r = this.history.pop();
    for (let i = 0; i < r.length; i += 3) {
      this.value[r[i]] = r[i + 1];
      this.notes[r[i]] = r[i + 2];
    }
    this.selected = r[0];
    return true;
  }

  fillNotes() {
    if (!this.active || this.solved) return false;
    for (let i = 0; i < 81; i++) {
      if (this.value[i]) continue;
      const m = candidates(this.value, i);
      if (this.notes[i] !== m) {
        this.touch(i);
        this.notes[i] = m;
      }
    }
    return this.commit();
  }

  hintActive() {
    return this.hintKind !== HINT_NONE && this.active && !this.solved && this.history.length === this.hintMoves && this.selected === this.hintCell;
  }

  hint(engine) {
    if (!this.active || this.solved) return false;
    if (this.hintActive() && this.hintKind === HINT_PLACE && !this.value[this.hintCell]) {
      this.noteMode = false;
      this.hintKind = HINT_NONE;
      return this.enter(this.hintDigit);
    }
    this.hintKind = HINT_NONE;
    for (let i = 0; i < 81; i++) {
      if (!this.given[i] && this.value[i] && this.value[i] !== this.solution[i]) {
        this.hintKind = HINT_WRONG;
        this.hintCell = i;
        this.selected = i;
        this.hintMoves = this.history.length;
        return true;
      }
    }
    if (!engine.hint(this.value)) return false;
    this.hintKind = HINT_PLACE;
    this.hintCell = engine.stepCell;
    this.hintDigit = engine.stepDigit;
    this.hintTech = engine.hintTech;
    this.hintUnit = engine.stepUnit;
    this.selected = this.hintCell;
    this.hintMoves = this.history.length;
    return true;
  }

  save(now) {
    return {
      v: 1,
      active: this.active,
      level: this.level,
      solved: this.solved,
      showErrors: this.showErrors,
      noteMode: this.noteMode,
      selected: this.selected,
      elapsed: this.time(now),
      given: this.given.join(''),
      solution: this.solution.join(''),
      value: this.value.join(''),
      notes: this.notes,
      history: this.history
    };
  }

  load(s) {
    this.active = false;
    try {
      if (!s || s.v !== 1) return false;
      this.showErrors = !!s.showErrors;
      if (!s.active) return true;
      const digits = str => {
        if (typeof str !== 'string' || str.length !== 81) throw new Error('bad');
        return Array.from(str, ch => {
          const v = ch.charCodeAt(0) - 48;
          if (v < 0 || v > 9) throw new Error('bad');
          return v;
        });
      };
      const given = digits(s.given), solution = digits(s.solution), value = digits(s.value);
      if (!Array.isArray(s.notes) || s.notes.length !== 81 || !Array.isArray(s.history)) return false;
      const level = s.level | 0, selected = s.selected | 0, elapsed = +s.elapsed;
      if (level < 0 || level > 3 || selected < NONE || selected > 80 || !(elapsed >= 0)) return false;
      for (const r of s.history) if (!Array.isArray(r) || r.length % 3 !== 0) return false;
      this.given = given;
      this.solution = solution;
      this.value = value;
      this.notes = s.notes.map(n => (n | 0) & ALL);
      this.history = s.history.map(r => r.map(x => x | 0));
      this.record = [];
      this.level = level;
      this.solved = !!s.solved;
      this.noteMode = !!s.noteMode;
      this.selected = selected;
      this.elapsed = elapsed;
      this.running = false;
      this.hintKind = HINT_NONE;
      this.active = true;
      return true;
    } catch (e) {
      this.active = false;
      return false;
    }
  }
}

if (typeof module !== 'undefined') module.exports = { Sudoku, Game, candidates, bit, PEERS, ROW, COL, BOX, HINT_WRONG, HINT_PLACE, NONE };
