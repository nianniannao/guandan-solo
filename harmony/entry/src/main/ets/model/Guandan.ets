// 掼蛋规则内核（与安卓版同一套规则，端口/协议一致）
export class Card {
  id: number = 0;
  suit: number = 0;   // 0 黑桃 1 红桃 2 梅花 3 方块 4 王
  rank: number = 2;   // 2..14 = 2..A，15 小王，16 大王
  wild: boolean = false;

  constructor(suit: number, rank: number) {
    this.suit = suit;
    this.rank = rank;
  }

  isJoker(): boolean { return this.rank >= 15; }
  rankText(): string {
    if (this.rank === 11) return 'J';
    if (this.rank === 12) return 'Q';
    if (this.rank === 13) return 'K';
    if (this.rank === 14) return 'A';
    if (this.rank === 15) return '小';
    if (this.rank === 16) return '大';
    return this.rank.toString();
  }
  suitText(): string {
    if (this.suit === 0) return '♠';
    if (this.suit === 1) return '♥';
    if (this.suit === 2) return '♣';
    if (this.suit === 3) return '♦';
    return '';
  }
  isRed(): boolean { return this.suit === 1 || this.suit === 3 || this.rank === 16; }
}

export class Play {
  type: number = 0;
  key: number = 0;
  size: number = 0;
  power: number = 0;
  cards: Card[] = [];

  typeName(): string {
    if (this.type === 1) return '单张';
    if (this.type === 2) return '对子';
    if (this.type === 3) return '三张';
    if (this.type === 4) return '三带二';
    if (this.type === 5) return '顺子';
    if (this.type === 6) return '三连对';
    if (this.type === 7) return '钢板';
    if (this.type === 8) return '同花顺';
    if (this.type === 9) return this.size + '张炸弹';
    if (this.type === 10) return '天王炸';
    return '—';
  }
}

export class Rules {
  static readonly SINGLE: number = 1;
  static readonly PAIR: number = 2;
  static readonly TRIPLE: number = 3;
  static readonly TRIPLE_PAIR: number = 4;
  static readonly STRAIGHT: number = 5;
  static readonly CONSEC_PAIRS: number = 6;
  static readonly PLATE: number = 7;
  static readonly SF: number = 8;
  static readonly BOMB: number = 9;
  static readonly KING_BOMB: number = 10;

  static weight(rank: number, level: number): number {
    if (rank >= 15) return rank + 1;
    if (rank === level) return 15;
    return rank;
  }

  static evaluateExact(cards: Card[], level: number): Play | null {
    const n: number = cards.length;
    if (n < 1 || n > 12) return null;
    const cnt: number[] = new Array<number>(18).fill(0);
    let distinct: number = 0;
    for (let i = 0; i < n; i++) cnt[cards[i].rank] += 1;
    for (let r = 2; r <= 17; r++) if (cnt[r] > 0) distinct++;

    const p = new Play();
    p.size = n;
    p.cards = cards.slice();

    if (n === 4 && cnt[15] === 2 && cnt[16] === 2) {
      p.type = Rules.KING_BOMB; p.power = 100; p.key = 17; return p;
    }
    if (n >= 4 && distinct === 1) {
      for (let r = 2; r <= 14; r++) {
        if (cnt[r] === n) {
          p.type = Rules.BOMB; p.power = n; p.key = Rules.weight(r, level); return p;
        }
      }
      return null;
    }
    if (n === 1) { p.type = Rules.SINGLE; p.key = Rules.weight(cards[0].rank, level); return p; }
    if (n === 2 && distinct === 1) { p.type = Rules.PAIR; p.key = Rules.weight(cards[0].rank, level); return p; }
    if (n === 3 && distinct === 1) { p.type = Rules.TRIPLE; p.key = Rules.weight(cards[0].rank, level); return p; }
    if (n === 5) {
      let three: number = -1;
      let two: number = -1;
      for (let r = 2; r <= 17; r++) {
        if (cnt[r] === 3) three = r;
        else if (cnt[r] === 2) two = r;
      }
      if (three > 0 && two > 0) { p.type = Rules.TRIPLE_PAIR; p.key = Rules.weight(three, level); return p; }
      if (cnt[15] === 0 && cnt[16] === 0 && distinct === 5) {
        let min: number = 99;
        let max: number = 0;
        for (let r = 2; r <= 14; r++) {
          if (cnt[r] > 0) { if (r < min) min = r; if (r > max) max = r; }
        }
        if (max - min === 4) {
          const s0: number = cards[0].suit;
          let same: boolean = true;
          for (let i = 0; i < n; i++) if (cards[i].suit !== s0) same = false;
          if (same && s0 !== 4) { p.type = Rules.SF; p.power = 5.5; p.key = max; }
          else { p.type = Rules.STRAIGHT; p.key = max; }
          return p;
        }
      }
      return null;
    }
    if (n === 6) {
      let pairs: number = 0;
      let triples: number = 0;
      let okPair: boolean = true;
      let okPlate: boolean = true;
      let min: number = 99;
      let max: number = 0;
      for (let r = 2; r <= 17; r++) {
        if (cnt[r] === 2) pairs++;
        else if (cnt[r] === 3) triples++;
        else if (cnt[r] !== 0) { okPair = false; okPlate = false; }
        if (cnt[r] > 0) { if (r < min) min = r; if (r > max) max = r; }
      }
      if (okPair && pairs === 3 && max - min === 2 && cnt[15] === 0 && cnt[16] === 0) {
        p.type = Rules.CONSEC_PAIRS; p.key = max; return p;
      }
      if (okPlate && triples === 2 && max - min === 1 && cnt[15] === 0 && cnt[16] === 0) {
        p.type = Rules.PLATE; p.key = max; return p;
      }
      return null;
    }
    return null;
  }

  static evaluate(cards: Card[], level: number): Play | null {
    let wilds: number = 0;
    for (let i = 0; i < cards.length; i++) if (cards[i].wild) wilds++;
    if (wilds === 0 || wilds > 2) return Rules.evaluateExact(cards, level);

    const fixed: Card[] = [];
    for (let i = 0; i < cards.length; i++) if (!cards[i].wild) fixed.push(cards[i]);
    let best: Play | null = null;
    for (let s = 0; s < 4; s++) {
      for (let r = 2; r <= 14; r++) {
        const full = fixed.slice();
        full.push(new Card(s, r));
        if (wilds === 2) {
          for (let s2 = 0; s2 < 4; s2++) {
            for (let r2 = 2; r2 <= 14; r2++) {
              const full2 = full.slice();
              full2.push(new Card(s2, r2));
              const p2 = Rules.evaluateExact(full2, level);
              if (p2 !== null && Rules.better(p2, best)) best = p2;
            }
          }
        } else {
          const p = Rules.evaluateExact(full, level);
          if (p !== null && Rules.better(p, best)) best = p;
        }
      }
    }
    if (best !== null) best.cards = cards.slice();
    return best;
  }

  static better(p: Play, best: Play | null): boolean {
    if (best === null) return true;
    if (p.power !== best.power) return p.power > best.power;
    if (p.key !== best.key) return p.key > best.key;
    return p.type > best.type;
  }

  static canBeat(last: Play | null, cur: Play): boolean {
    if (last === null) return true;
    if (cur.power > 0 || last.power > 0) {
      if (cur.power === 0) return false;
      if (last.power === 0) return true;
      if (cur.power !== last.power) return cur.power > last.power;
      return cur.key > last.key;
    }
    if (cur.type !== last.type) return false;
    if (cur.size !== last.size) return false;
    return cur.key > last.key;
  }
}

export class Ai {
  static candidates(hand: Card[], level: number): Play[] {
    const out: Play[] = [];
    const cnt: number[] = new Array<number>(18).fill(0);
    for (let i = 0; i < hand.length; i++) cnt[hand[i].rank] += 1;
    const pick = (rank: number, count: number): Card[] => {
      const res: Card[] = [];
      for (let i = 0; i < hand.length && res.length < count; i++) {
        if (hand[i].rank === rank && !hand[i].wild) res.push(hand[i]);
      }
      for (let i = 0; i < hand.length && res.length < count; i++) {
        if (hand[i].rank === rank && hand[i].wild && res.indexOf(hand[i]) < 0) res.push(hand[i]);
      }
      return res;
    };
    const add = (cards: Card[]): void => {
      if (cards.length === 0) return;
      const p = Rules.evaluateExact(cards, level);
      if (p === null) return;
      for (let i = 0; i < out.length; i++) {
        if (out[i].type === p.type && out[i].key === p.key && out[i].size === p.size) return;
      }
      out.push(p);
    };

    for (let r = 2; r <= 16; r++) {
      if (cnt[r] > 0) add(pick(r, 1));
      if (cnt[r] >= 2) add(pick(r, 2));
      if (cnt[r] >= 3) add(pick(r, 3));
      if (r <= 14 && cnt[r] >= 4) add(pick(r, cnt[r]));
    }
    for (let a = 2; a <= 14; a++) {
      if (cnt[a] < 3) continue;
      for (let b = 2; b <= 16; b++) {
        if (b === a || cnt[b] < 2) continue;
        const cs: Card[] = pick(a, 3);
        for (let i = 0; i < 2; i++) cs.push(pick(b, 2)[i]);
        add(cs);
      }
    }
    for (let s = 2; s <= 10; s++) {
      let ok: boolean = true;
      for (let r = s; r < s + 5; r++) if (cnt[r] < 1) ok = false;
      if (ok) {
        const cs: Card[] = [];
        for (let r = s; r < s + 5; r++) cs.push(pick(r, 1)[0]);
        add(cs);
      }
    }
    for (let s = 2; s <= 12; s++) {
      let ok: boolean = true;
      for (let r = s; r < s + 3; r++) if (cnt[r] < 2) ok = false;
      if (ok) {
        const cs: Card[] = [];
        for (let r = s; r < s + 3; r++) { const p2 = pick(r, 2); cs.push(p2[0]); cs.push(p2[1]); }
        add(cs);
      }
    }
    for (let s = 2; s <= 13; s++) {
      let ok: boolean = true;
      for (let r = s; r < s + 2; r++) if (cnt[r] < 3) ok = false;
      if (ok) {
        const cs: Card[] = [];
        for (let r = s; r < s + 2; r++) { const p3 = pick(r, 3); cs.push(p3[0]); cs.push(p3[1]); cs.push(p3[2]); }
        add(cs);
      }
    }
    return out;
  }

  static thinkLevel: number = 1;

  static handsCount(hand: Card[], level: number): number {
    const cnt: number[] = new Array<number>(18).fill(0);
    for (let i = 0; i < hand.length; i++) {
      if (!hand[i].wild) cnt[hand[i].rank] += 1;
    }
    let wilds: number = 0;
    for (let i = 0; i < hand.length; i++) if (hand[i].wild) wilds++;
    let combos: number = 0;
    // 炸弹单独成手
    for (let r = 2; r <= 14; r++) if (cnt[r] >= 4) { combos++; cnt[r] -= 4; }
    // 顺子：贪心取最长连续段（>=5）
    for (let s0 = 2; s0 <= 10; s0++) {
      let len: number = 0;
      for (let r = s0; r <= 14; r++) {
        if (cnt[r] >= 1) len++;
        else break;
      }
      if (len >= 5) {
        for (let r = s0; r < s0 + len; r++) cnt[r] -= 1;
        combos++;
        s0 = s0 + len - 1;
      }
    }
    // 三张优先配对子成 三带二
    for (let r = 2; r <= 17; r++) {
      if (cnt[r] >= 3) {
        combos++;
        cnt[r] -= 3;
        let paired: boolean = false;
        for (let q = 2; q <= 17 && !paired; q++) {
          if (cnt[q] === 2) { cnt[q] = 0; paired = true; }
        }
        if (!paired && wilds >= 2) { wilds -= 2; paired = true; }
      }
    }
    for (let r = 2; r <= 17; r++) {
      if (cnt[r] === 2) { combos++; cnt[r] = 0; }
      else if (cnt[r] === 1) { combos++; cnt[r] = 0; }
    }
    // 逢人配按散牌估算
    combos += Math.ceil(wilds / 2);
    return combos < 1 ? 1 : combos;
  }

  /** 外面（除自己手牌与已出牌）还剩多少张比 key 大的牌，用于判断控制力 */
  static higherLeft(e: Engine, seat: number, key: number): number {
    let left: number = 0;
    for (let r = 2; r <= 14; r++) {
      const w: number = Rules.weight(r, e.level);
      if (w <= key) continue;
      let total: number = 8;
      let mine: number = 0;
      for (let i = 0; i < e.hands[seat].length; i++) if (e.hands[seat][i].rank === r) mine++;
      const played: number = e.playedCounts[r];
      left += Math.max(0, total - mine - played);
    }
    if (key < 16) left += Math.max(0, 2 - e.playedCounts[15]);
    if (key < 17) left += Math.max(0, 2 - e.playedCounts[16]);
    return left;
  }

  static score(e: Engine, seat: number, p: Play): number {
    const hand: Card[] = e.hands[seat];
    const before: number = Ai.handsCount(hand, e.level);
    const rest: Card[] = hand.slice();
    for (let i = 0; i < p.cards.length; i++) {
      const idx: number = rest.indexOf(p.cards[i]);
      if (idx >= 0) rest.splice(idx, 1);
    }
    const after: number = Ai.handsCount(rest, e.level);
    let run: number = (before - after) * 8 + p.cards.length * 0.5;
    if (rest.length === 0) run += 120;

    let bomb: number = 0;
    if (p.power > 0) {
      bomb = p.power * 7 + (p.type === 10 ? 40 : 0);
      const mo0: number = e.minOpponentCards(seat);
      if (mo0 <= 5) bomb *= 0.35;
      if (rest.length <= 6) bomb *= 0.5;
    }
    const minOpp: number = e.minOpponentCards(seat);
    const mateLeft: number = e.hands[e.teammate(seat)].length;
    let block: number = 0;
    if (e.lastPlay !== null) {
      if (minOpp <= 8) block += 3;
      if (p.key >= 15 && minOpp > 6) block -= 2;
      if (p.key >= 15 && rest.length > 10) block -= 2;
    }
    let mateAdj: number = 0;
    if (e.lastPlay !== null && e.lastPlayer >= 0 && e.lastPlayer === e.teammate(seat)) mateAdj -= 25;
    if (e.lastPlay === null) {
      if (mateLeft <= 4) mateAdj += 5;
      if (mateLeft <= 2) mateAdj += 3;
    }
    let breakCost: number = 0;
    if (p.power === 0) {
      const cnt: number[] = new Array<number>(18).fill(0);
      for (let i = 0; i < hand.length; i++) if (!hand[i].wild) cnt[hand[i].rank] += 1;
      for (let i = 0; i < p.cards.length; i++) {
        const c: Card = p.cards[i];
        if (!c.wild && cnt[c.rank] >= 4 && p.cards.length < 4) breakCost -= 14;
        if (c.wild) breakCost -= 6;
      }
    }
    if (minOpp <= 2 && p.type === 1 && p.key < 14) block -= 3;
    // 记牌：这张牌外面已经没有了 → 拿到牌权，残局里价值高
    if (p.power === 0 && Ai.higherLeft(e, seat, p.key) === 0 && rest.length <= 10) run += 5;
    // 对手只剩一两张时，宁可拆牌也要压低他的出牌空间
    if (minOpp <= 2 && p.cards.length >= 2) run += 4;
    return run + block + mateAdj + breakCost - bomb;
  }

  /** 领出（自由出牌）时的选牌：先走散、保留控制牌与炸弹，尽量多走张数 */
  static chooseLead(e: Engine, seat: number): Play | null {
    const hand: Card[] = e.hands[seat];
    const cands: Play[] = Ai.candidates(hand, e.level);
    if (cands.length === 0) return null;
    // 能一把走完立刻走完
    for (let i = 0; i < cands.length; i++) {
      if (cands[i].cards.length === hand.length) return cands[i];
    }
    const minOpp: number = e.minOpponentCards(seat);
    const mateLeft: number = e.hands[e.teammate(seat)].length;
    let best: Play | null = null;
    let bestScore: number = -100000;
    for (let i = 0; i < cands.length; i++) {
      const p: Play = cands[i];
      let sc: number = Ai.score(e, seat, p);
      sc += p.cards.length * 2.6;
      // 炸弹只在关键局面用（对手快走完或自己能收尾）
      if (p.power > 0) {
        sc -= minOpp <= 6 || hand.length - p.cards.length <= 3 ? 6 : 34;
        if (p.power >= 100) sc -= 20;
      }
      // 手里最大的一张留着控场，别一上来就甩
      if (p.type === 1 && Ai.higherLeft(e, seat, p.key) === 0 && hand.length > 6) sc -= 9;
      // 队友快走完时，多出小牌给队友让路
      if (mateLeft <= 5 && p.cards.length <= 2 && p.key < 13) sc += 3;
      if (sc > bestScore) { bestScore = sc; best = p; }
    }
    return best;
  }

  static chooseBest(e: Engine, seat: number, beat: Play[]): Play | null {
    let best: Play | null = null;
    let bestScore: number = -100000;
    for (let i = 0; i < beat.length; i++) {
      const s: number = Ai.score(e, seat, beat[i]);
      if (s > bestScore) { bestScore = s; best = beat[i]; }
    }
    if (best === null) return null;
    const minOpp: number = e.minOpponentCards(seat);
    if (best.power > 0 && minOpp > 6 && e.hands[seat].length > 8 && bestScore < 5) return null;
    if (e.lastPlay !== null && bestScore < 0 && minOpp > 4) return null;
    return best;
  }

  static choose(hand: Card[], last: Play | null, diff: number, minOppLeft: number, lastIsMate: boolean): Play | null {
    const cands = Ai.candidates(hand, 0);
    const beat: Play[] = [];
    for (let i = 0; i < cands.length; i++) if (Rules.canBeat(last, cands[i])) beat.push(cands[i]);
    if (beat.length === 0) return null;

    let cheapest: Play | null = null;
    for (let i = 0; i < beat.length; i++) {
      if (beat[i].power > 0) continue;
      if (cheapest === null || beat[i].key < cheapest.key) cheapest = beat[i];
    }
    if (last !== null && lastIsMate) {
      for (let i = 0; i < beat.length; i++) if (beat[i].cards.length === hand.length) return beat[i];
      return null;
    }
    if (last === null) {
      for (let i = 0; i < beat.length; i++) if (beat[i].cards.length === hand.length) return beat[i];
      if (diff === 0) return cheapest !== null ? cheapest : beat[0];
      let best: Play | null = null;
      for (let i = 0; i < beat.length; i++) {
        if (beat[i].power > 0) continue;
        if (best === null) { best = beat[i]; continue; }
        if (beat[i].cards.length > best.cards.length) best = beat[i];
        else if (beat[i].cards.length === best.cards.length && beat[i].key < best.key) best = beat[i];
      }
      return best !== null ? best : beat[0];
    }
    if (diff === 0) return cheapest !== null ? cheapest : beat[0];
    if (cheapest !== null) {
      if (cheapest.key >= 15 && minOppLeft > 8 && hand.length > 10) return null;
      if (diff === 1 && cheapest.key > 12 && minOppLeft > 6) return null;
      return cheapest;
    }
    if (minOppLeft <= 7) return beat[0];
    return null;
  }
}

export class Engine {
  hands: Card[][] = [[], [], [], []];
  level: number = 2;
  difficulty: number = 1;
  turn: number = 0;
  lastPlayer: number = -1;
  passes: number = 0;
  lastPlay: Play | null = null;
  finished: number[] = [];
  roundOver: boolean = false;
  teamLevel: number[] = [2, 2];
  playingTeam: number = 0;
  roundNo: number = 0;
  seatTag: string[] = ['—', '—', '—', '—'];
  seatPassed: boolean[] = [false, false, false, false];
  isAi: boolean[] = [false, true, true, true];
  resultText: string = '';
  level_1: string = '';
  logs: string[] = [];
  /** 已经打出去的牌按点数计数（记牌用） */
  playedCounts: number[] = new Array<number>(18).fill(0);

  newMatch(diff: number): void {
    this.difficulty = diff;
    this.teamLevel = [2, 2];
    this.playingTeam = 0;
    this.roundNo = 0;
    this.deal();
  }

  deal(): void {
    this.level = this.teamLevel[this.playingTeam];
    this.roundNo += 1;
    const deck: Card[] = Engine.buildDeck(this.level);
    for (let i = deck.length - 1; i > 0; i--) {
      const j: number = Math.floor(Math.random() * (i + 1));
      const t: Card = deck[i]; deck[i] = deck[j]; deck[j] = t;
    }
    for (let i = 0; i < 4; i++) this.hands[i] = [];
    for (let i = 0; i < deck.length; i++) this.hands[i % 4].push(deck[i]);
    for (let i = 0; i < 4; i++) {
      const lv: number = this.level;
      this.hands[i].sort((a: Card, b: Card): number => {
        const wa: number = Rules.weight(a.rank, lv);
        const wb: number = Rules.weight(b.rank, lv);
        return wa > wb ? -1 : (wa < wb ? 1 : 0);
      });
    }
    this.finished = [];
    this.roundOver = false;
    this.lastPlay = null;
    this.lastPlayer = -1;
    this.passes = 0;
    this.turn = this.roundNo === 1 ? 0 : this.playingTeam * 2;
    for (let i = 0; i < 4; i++) { this.seatTag[i] = '—'; this.seatPassed[i] = false; }
    for (let r = 0; r < 18; r++) this.playedCounts[r] = 0;
    this.logs = ['开局，打 ' + Engine.levelText(this.level)];
  }

  static buildDeck(level: number): Card[] {
    const deck: Card[] = [];
    for (let d = 0; d < 2; d++) {
      for (let s = 0; s < 4; s++) for (let r = 2; r <= 14; r++) deck.push(new Card(s, r));
      deck.push(new Card(4, 15));
      deck.push(new Card(4, 16));
    }
    for (let i = 0; i < deck.length; i++) {
      const c: Card = deck[i];
      c.id = i;
      c.wild = c.suit === 1 && c.rank === level;
    }
    return deck;
  }

  static levelText(lv: number): string {
    if (lv === 11) return 'J';
    if (lv === 12) return 'Q';
    if (lv === 13) return 'K';
    if (lv === 14) return 'A';
    return lv.toString();
  }

  static seatName(seat: number): string {
    if (seat === 0) return '你';
    if (seat === 1) return '右家';
    if (seat === 2) return '对家';
    return '左家';
  }

  teammate(seat: number): number { return (seat + 2) % 4; }
  isFinished(seat: number): boolean { return this.finished.indexOf(seat) >= 0; }

  minOpponentCards(seat: number): number {
    let min: number = 99;
    for (let i = 0; i < 4; i++) {
      if (i === seat || i === this.teammate(seat) || this.isFinished(i)) continue;
      if (this.hands[i].length < min) min = this.hands[i].length;
    }
    return min === 99 ? 0 : min;
  }

  playCards(seat: number, cards: Card[]): string {
    if (cards.length === 0) return '请先选择牌';
    const p = Rules.evaluate(cards, this.level);
    if (p === null) return '牌型不合法';
    if (!Rules.canBeat(this.lastPlay, p)) return '压不过上家';
    this.applyPlay(seat, p);
    return '';
  }

  applyPlay(seat: number, p: Play): void {
    const h: Card[] = this.hands[seat];
    for (let i = 0; i < p.cards.length; i++) {
      const idx: number = h.indexOf(p.cards[i]);
      if (idx >= 0) h.splice(idx, 1);
      this.playedCounts[p.cards[i].rank] += 1;
    }
    this.seatTag[seat] = p.typeName();
    this.seatPassed[seat] = false;
    this.lastPlay = p;
    this.lastPlayer = seat;
    this.passes = 0;
    this.logs.unshift(Engine.seatName(seat) + ' 出 ' + p.typeName());
    if (this.logs.length > 3) this.logs.pop();
    if (this.hands[seat].length === 0) this.finished.push(seat);
    if (this.checkRoundEnd()) return;
    this.nextTurn(seat);
  }

  /** 当前还需要几家过牌才算一轮结束：除领出者以外的所有未出完玩家 */
  needPasses(): number {
    let active: number = 0;
    for (let i = 0; i < 4; i++) if (!this.isFinished(i)) active++;
    let leaderActive: boolean = this.lastPlayer >= 0 && !this.isFinished(this.lastPlayer);
    let need: number = active - (leaderActive ? 1 : 0);
    return need < 1 ? 1 : need;
  }

  doPass(seat: number): void {
    this.seatPassed[seat] = true;
    this.seatTag[seat] = '不要';
    this.passes += 1;
    if (this.passes >= this.needPasses()) {
      // 一轮结束：领出者重新出牌；若领出者已出完，由队友接风，队友也出完则顺延下一位
      let leader: number = this.lastPlayer >= 0 ? this.lastPlayer : seat;
      if (this.isFinished(leader)) {
        const mate: number = this.teammate(leader);
        if (!this.isFinished(mate)) leader = mate;
        else leader = this.nextActiveAfter(seat);
      }
      this.lastPlay = null;
      this.lastPlayer = -1;
      this.passes = 0;
      for (let i = 0; i < 4; i++) { this.seatTag[i] = '—'; this.seatPassed[i] = false; }
      this.turn = leader;
    } else {
      this.nextTurn(seat);
    }
  }

  nextActiveAfter(seat: number): number {
    for (let k = 1; k <= 4; k++) {
      const s: number = (seat + k) % 4;
      if (!this.isFinished(s)) return s;
    }
    return seat;
  }

  nextTurn(from: number): void { this.turn = this.nextActiveAfter(from); }

  checkRoundEnd(): boolean {
    if (this.finished.length >= 3) { this.finishRound(); return true; }
    const t0: number = this.finished[0] % 2;
    let cnt: number = 0;
    for (let i = 0; i < this.finished.length; i++) if (this.finished[i] % 2 === t0) cnt++;
    if (cnt >= 2) { this.finishRound(); return true; }
    return false;
  }

  finishRound(): void {
    this.roundOver = true;
    const team: number = this.finished[0] % 2;
    let matePos: number = 4;
    for (let i = 1; i < this.finished.length; i++) {
      if (this.finished[i] % 2 === team) matePos = i + 1;
    }
    const gain: number = matePos === 2 ? 3 : (matePos === 3 ? 2 : 1);
    const before: number = this.teamLevel[team];
    const after: number = Math.min(14, before + gain);
    this.teamLevel[team] = after;
    this.playingTeam = team;
    this.level = after;
    this.resultText = (team === 0 ? '我方' : '对方') + (gain === 3 ? '双上' : (gain === 2 ? '一、三' : '一、四'))
      + '，升 ' + gain + ' 级（' + Engine.levelText(before) + ' → ' + Engine.levelText(after) + '）';
  }

  aiStep(): void {
    if (this.roundOver) return;
    const seat: number = this.turn;
    if (this.isFinished(seat) || !this.isAi[seat]) return;
    const hand: Card[] = this.hands[seat];
    if (hand.length === 0) { this.nextTurn(seat); return; }

    // 首家：自由出牌（不在旧逻辑里固定甩最小的单张）
    if (this.lastPlay === null) {
      let lead: Play | null = Ai.chooseLead(this, seat);
      if (lead === null) lead = Rules.evaluateExact([hand[hand.length - 1]], this.level);
      if (lead !== null) this.applyPlay(seat, lead);
      return;
    }

    const cands: Play[] = Ai.candidates(hand, this.level);
    const beat: Play[] = [];
    for (let i = 0; i < cands.length; i++) {
      if (Rules.canBeat(this.lastPlay, cands[i])) beat.push(cands[i]);
    }
    // 能一把走完就走完
    for (let i = 0; i < beat.length; i++) {
      if (beat[i].cards.length === hand.length) { this.applyPlay(seat, beat[i]); return; }
    }
    const mate: boolean = this.lastPlayer >= 0 && this.lastPlayer !== seat && (this.lastPlayer % 2) === (seat % 2);
    if (mate) {
      // 队友的牌：难度高时不再无脑顶，除非自己快走完或对手已逼近
      if (this.difficulty === 0) { this.doPass(seat); return; }
      if (hand.length > 4 && this.minOpponentCards(seat) > 4) { this.doPass(seat); return; }
    } else if (this.difficulty === 1 && hand.length > 12 && this.minOpponentCards(seat) > 8) {
      // 高级难度：前半局不为小牌浪费大牌
      let onlyBig: boolean = true;
      for (let i = 0; i < beat.length; i++) if (beat[i].key <= 11) onlyBig = false;
      if (onlyBig) { this.doPass(seat); return; }
    }

    let chosen: Play | null = null;
    if (beat.length > 0 && this.difficulty === 0) {
      let cheapest: Play | null = null;
      for (let i = 0; i < beat.length; i++) {
        if (beat[i].power > 0) continue;
        if (cheapest === null || beat[i].key < cheapest.key) cheapest = beat[i];
      }
      chosen = cheapest;
    } else if (beat.length > 0) {
      chosen = Ai.chooseBest(this, seat, beat);
    }
    if (chosen === null) { this.doPass(seat); return; }
    this.applyPlay(seat, chosen);
  }
}
