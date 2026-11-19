package com.guandan.solo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 掼蛋 AI v8：候选牌型（含逢人配展开）+ 记牌 + 手数规划 + 残局推演。
 * 纯代码实现，不引入神经网络，手机端单步控制在百毫秒级。
 */
public class AINew {

    /** 思考强度：0 快、1 标准、2 深（由界面设置，只影响高级/终极难度） */
    public static int thinkLevel = 1;

    private static final Random RND = new Random();

    // ===================== 候选牌型 =====================

    public static List<Play> candidates(List<Card> hand, int level) {
        List<Play> out = new ArrayList<Play>();
        int[] nat = new int[18];
        int wild = 0;
        List<Card> wilds = new ArrayList<Card>();
        for (Card c : hand) {
            if (c.wild) { wild++; wilds.add(c); } else nat[c.rank]++;
        }

        // 单张：普通牌 + 逢人配本身
        for (int r = 2; r <= 16; r++) {
            if (nat[r] == 0) continue;
            List<Card> cs = new ArrayList<Card>();
            for (Card c : hand) if (!c.wild && c.rank == r) { cs.add(c); break; }
            if (!cs.isEmpty()) add(out, cs, level);
        }
        if (wild > 0) {
            List<Card> cs = new ArrayList<Card>();
            cs.add(wilds.get(0));
            add(out, cs, level);
        }
        // 王的对子
        for (int r = 15; r <= 16; r++) {
            if (nat[r] < 2) continue;
            List<Card> cs = new ArrayList<Card>();
            for (Card c : hand) if (!c.wild && c.rank == r && cs.size() < 2) cs.add(c);
            if (cs.size() == 2) add(out, cs, level);
        }

        // 同点数：对子 / 三张 / 炸弹（逢人配可补）
        for (int r = 2; r <= 14; r++) {
            int total = nat[r] + wild;
            if (total < 2) continue;
            for (int size = 2; size <= Math.min(8, total); size++) {
                List<Card> orig = new ArrayList<Card>();
                List<Card> virt = new ArrayList<Card>();
                int useNat = Math.min(size, nat[r]);
                int k = 0;
                for (Card c : hand) if (!c.wild && c.rank == r && k < useNat) { orig.add(c); virt.add(c); k++; }
                int need = size - useNat;
                for (int i = 0; i < need; i++) {
                    Card w = wilds.get(i);
                    orig.add(w);
                    virt.add(r == level ? w : new Card(i % 4, r));
                }
                addV(out, orig, virt, level);
            }
        }

        // 三带二（每个三张只配两个最低的对子，避免候选爆炸）
        int[] pairRanks = new int[2];
        for (int t = 2; t <= 14; t++) {
            int needT = Math.max(0, 3 - nat[t]);
            if (needT > wild) continue;
            int pn = 0;
            for (int q = 2; q <= 14 && pn < 2; q++) {
                if (q == t || nat[q] < 1) continue;
                int needQ = Math.max(0, 2 - nat[q]);
                if (needT + needQ > wild) continue;
                pairRanks[pn++] = q;
            }
            for (int pi = 0; pi < pn; pi++) {
                int q = pairRanks[pi];
                int needQ = Math.max(0, 2 - nat[q]);
                List<Card> orig = new ArrayList<Card>();
                List<Card> virt = new ArrayList<Card>();
                int k = 0;
                for (Card c : hand) if (!c.wild && c.rank == t && k < Math.min(3, nat[t])) { orig.add(c); virt.add(c); k++; }
                for (int i = 0; i < needT; i++) { Card w = wilds.get(i); orig.add(w); virt.add(t == level ? w : new Card(i % 4, t)); }
                k = 0;
                for (Card c : hand) if (!c.wild && c.rank == q && k < Math.min(2, nat[q])) { orig.add(c); virt.add(c); k++; }
                for (int i = 0; i < needQ; i++) { Card w = wilds.get(needT + i); orig.add(w); virt.add(q == level ? w : new Card(i % 4, q)); }
                addV(out, orig, virt, level);
            }
        }

        // 顺子
        for (int s = 2; s <= 10; s++) {
            List<Card> orig = new ArrayList<Card>();
            List<Card> virt = new ArrayList<Card>();
            List<Integer> gaps = new ArrayList<Integer>();
            for (int r = s; r <= s + 4; r++) {
                Card c = null;
                for (Card x : hand) if (!x.wild && x.rank == r) { c = x; break; }
                if (c != null) { orig.add(c); virt.add(c); } else gaps.add(r);
            }
            if (gaps.size() > wild) continue;
            for (int i = 0; i < gaps.size(); i++) {
                Card w = wilds.get(i);
                orig.add(w);
                virt.add(new Card((s + i) % 4, gaps.get(i)));
            }
            addV(out, orig, virt, level);
        }

        // 同花顺（逢人配可代花色）
        for (int su = 0; su < 4; su++) {
            for (int s = 2; s <= 10; s++) {
                List<Card> orig = new ArrayList<Card>();
                List<Card> virt = new ArrayList<Card>();
                List<Integer> gaps = new ArrayList<Integer>();
                for (int r = s; r <= s + 4; r++) {
                    Card c = null;
                    for (Card x : hand) if (!x.wild && x.suit == su && x.rank == r) { c = x; break; }
                    if (c != null) { orig.add(c); virt.add(c); } else gaps.add(r);
                }
                if (gaps.size() > wild) continue;
                for (int i = 0; i < gaps.size(); i++) {
                    Card w = wilds.get(i);
                    orig.add(w);
                    virt.add(new Card(su, gaps.get(i)));
                }
                addV(out, orig, virt, level);
            }
        }

        // 三连对
        for (int s = 2; s <= 12; s++) {
            List<Card> orig = new ArrayList<Card>();
            List<Card> virt = new ArrayList<Card>();
            int need = 0;
            boolean ok = true;
            for (int r = s; r <= s + 2; r++) {
                int take = Math.min(2, nat[r]);
                int k = 0;
                for (Card c : hand) if (!c.wild && c.rank == r && k < take) { orig.add(c); virt.add(c); k++; }
                need += 2 - take;
            }
            if (need > wild) ok = false;
            if (ok) {
                int gi = 0;
                for (int r = s; r <= s + 2; r++) {
                    int take = Math.min(2, nat[r]);
                    for (int i = take; i < 2; i++) {
                        Card w = wilds.get(gi++);
                        orig.add(w);
                        virt.add(r == level ? w : new Card(i % 4, r));
                    }
                }
                addV(out, orig, virt, level);
            }
        }

        // 钢板
        for (int s = 2; s <= 13; s++) {
            List<Card> orig = new ArrayList<Card>();
            List<Card> virt = new ArrayList<Card>();
            int need = 0;
            for (int r = s; r <= s + 1; r++) need += Math.max(0, 3 - nat[r]);
            if (need > wild) continue;
            int gi = 0;
            for (int r = s; r <= s + 1; r++) {
                int take = Math.min(3, nat[r]);
                int k = 0;
                for (Card c : hand) if (!c.wild && c.rank == r && k < take) { orig.add(c); virt.add(c); k++; }
                for (int i = take; i < 3; i++) {
                    Card w = wilds.get(gi++);
                    orig.add(w);
                    virt.add(r == level ? w : new Card(i % 4, r));
                }
            }
            addV(out, orig, virt, level);
        }

        // 天王炸
        if (nat[15] >= 2 && nat[16] >= 2) {
            List<Card> cs = new ArrayList<Card>();
            for (Card c : hand) if (!c.wild && c.rank == 15 && cs.size() < 2) cs.add(c);
            for (Card c : hand) if (!c.wild && c.rank == 16 && cs.size() < 4) cs.add(c);
            if (cs.size() == 4) add(out, cs, level);
        }
        return out;
    }

    private static void add(List<Play> out, List<Card> cards, int level) {
        if (cards == null || cards.isEmpty()) return;
        Play p = Rules.evaluateExact(cards, level);
        if (p == null) return;
        p.cards = new ArrayList<Card>(cards);
        p.size = cards.size();
        for (Play q : out) if (q.type == p.type && q.key == p.key && q.size == p.size) return;
        out.add(p);
    }

    /** 用虚拟牌解析出目标牌型，再挂回真实牌（逢人配由此可代点数/花色） */
    private static void addV(List<Play> out, List<Card> orig, List<Card> virt, int level) {
        if (orig.isEmpty()) return;
        Play p = Rules.evaluateExact(virt, level);
        if (p == null) return;
        p.cards = new ArrayList<Card>(orig);
        p.size = orig.size();
        for (Play q : out) if (q.type == p.type && q.key == p.key && q.size == p.size) return;
        out.add(p);
    }

    // ===================== 记牌 =====================

    private static int[] deckCounts() {
        int[] c = new int[18];
        for (int r = 2; r <= 14; r++) c[r] = 2;
        c[15] = 2;
        c[16] = 2;
        return c;
    }

    private static Engine cacheEngine;
    private static int cacheSeat = -1;
    private static int cacheSig = -1;
    private static int[] cacheUnseen;

    /** 别人手里可能还剩的每种点数张数（108 张减去自己手牌与已出的牌） */
    private static int[] unseenCounts(Engine e, int seat) {
        int sig = e.played.size() * 512 + e.hands[seat].size();
        if (cacheEngine == e && cacheSeat == seat && cacheSig == sig && cacheUnseen != null) return cacheUnseen;
        int[] c = deckCounts();
        if (e.hands != null && e.hands[seat] != null) for (Card x : e.hands[seat]) c[Math.min(17, x.rank)]--;
        if (e.played != null) for (Card x : e.played) c[Math.min(17, x.rank)]--;
        for (int i = 2; i <= 17; i++) if (c[i] < 0) c[i] = 0;
        cacheEngine = e;
        cacheSeat = seat;
        cacheSig = sig;
        cacheUnseen = c;
        return c;
    }

    private static int rankOfWeight(int w, int level) {
        if (w >= 16) return w - 1;   // 16 小王(15) 17 大王(16)
        if (w == 15) return level;   // 级牌
        return w;
    }

    /** 这张牌打出去后，别人还能不能组出更大的同类牌 */
    private static boolean likelySafe(Engine e, int seat, Play p) {
        if (p.power > 0) return true;
        int need = p.type == Play.SINGLE ? 1 : (p.type == Play.PAIR ? 2 : (p.type == Play.TRIPLE ? 3 : 0));
        if (need == 0) return false;
        int[] un = unseenCounts(e, seat);
        for (int w = p.key + 1; w <= 17; w++) {
            int r = rankOfWeight(w, e.level);
            int cnt = un[Math.min(17, r)];
            if (cnt >= need) return false;
        }
        return true;
    }

    /** 别人手里还可能有几个炸弹（按同点数 4 张估算，含逢人配则更乐观） */
    private static int bombDanger(Engine e, int seat) {
        int[] un = unseenCounts(e, seat);
        int n = 0;
        for (int r = 2; r <= 14; r++) if (un[r] >= 4) n++;
        return n;
    }

    // ===================== 手牌质量 =====================

    /** 手牌能拆成几手牌（越少越容易跑完）：炸弹、钢板、三连对、顺子、三张、对子、单张 */
    public static int handsCount(List<Card> hand, int level) {
        int[] cnt = new int[18];
        int wild = 0;
        for (Card c : hand) {
            if (c.wild) wild++;
            else cnt[c.rank]++;
        }
        int combos = 0;
        // 炸弹
        for (int r = 2; r <= 14; r++) {
            if (cnt[r] >= 4) { combos++; cnt[r] = 0; }
        }
        // 钢板（两组连续三张）
        for (int s = 2; s <= 13; s++) {
            int need = Math.max(0, 3 - cnt[s]) + Math.max(0, 3 - cnt[s + 1]);
            if (need <= wild && cnt[s] + cnt[s + 1] >= 2) {
                wild -= need;
                cnt[s] = 0;
                cnt[s + 1] = 0;
                combos++;
            }
        }
        // 三连对
        for (int s = 2; s <= 12; s++) {
            int need = 0;
            for (int r = s; r <= s + 2; r++) need += Math.max(0, 2 - cnt[r]);
            if (need <= wild && cnt[s] + cnt[s + 1] + cnt[s + 2] >= 3) {
                wild -= need;
                for (int r = s; r <= s + 2; r++) cnt[r] = 0;
                combos++;
            }
        }
        // 顺子
        for (int s = 2; s <= 10; s++) {
            int need = 0;
            for (int r = s; r <= s + 4; r++) if (cnt[r] == 0) need++;
            if (need <= wild) {
                wild -= need;
                for (int r = s; r <= s + 4; r++) cnt[r] = 0;
                combos++;
            }
        }
        // 三张 / 对子 / 单张
        for (int r = 2; r <= 17; r++) {
            int v = cnt[r];
            while (v >= 3 || (v >= 2 && wild > 0)) {
                if (v >= 3) { v -= 3; } else { v -= 2; wild--; }
                combos++;
            }
            while (v >= 2) { v -= 2; combos++; }
            if (v == 1 && wild > 0) { v = 0; wild--; }
            if (v > 0) combos++;
        }
        combos += wild;
        return Math.max(1, combos);
    }

    // ===================== 动作打分 =====================

    private static double scoreAction(Engine e, int seat, Play p) {
        List<Card> hand = e.hands[seat];
        int before = handsCount(hand, e.level);
        List<Card> rest = new ArrayList<Card>(hand);
        for (Card c : p.cards) if (rest.contains(c)) rest.remove(c);
        int after = handsCount(rest, e.level);

        double score = (before - after) * 10.0 + p.cards.size() * 0.6;
        if (rest.isEmpty()) score += 140;

        // 控场：这手牌别人大概率压不住
        if (likelySafe(e, seat, p)) {
            if (p.type == Play.SINGLE) score += 7;
            else if (p.type == Play.PAIR) score += 6;
            else if (p.type == Play.TRIPLE) score += 5;
            else score += 3;
        }

        int minOpp = e.minOpponentCards(seat);
        int mateLeft = e.hands[e.teammate(seat)].size();

        // 炸弹代价
        if (p.power > 0) {
            double cost = p.power * 7.0 + (p.type == Play.KING_BOMB ? 45 : 0);
            if (minOpp <= 3) cost *= 0.20;
            else if (minOpp <= 6) cost *= 0.55;
            if (e.lastPlay != null && e.lastPlay.size >= 4) cost *= 0.7;
            if (rest.size() <= 4) cost *= 0.5;
            score -= cost;
        }

        // 队友领出时不抢
        if (e.lastPlay != null && e.lastPlayer >= 0 && e.lastPlayer == e.teammate(seat)) score -= 30;
        // 队友快走，能帮就帮
        if (e.lastPlay == null && mateLeft <= 4) score += 6;
        if (e.lastPlay == null && mateLeft <= 2) score += 4;

        // 对手快走：压住比省牌重要
        if (e.lastPlay != null && minOpp <= 3) score += 8;

        // 拆炸弹 / 拆逢人配的惩罚
        if (p.power == 0) {
            int[] cnt = new int[18];
            for (Card c : hand) if (!c.wild) cnt[c.rank]++;
            for (Card c : p.cards) {
                if (!c.wild && cnt[c.rank] >= 4 && p.cards.size() < 4) score -= 16.0;
            }
            for (Card c : p.cards) if (c.wild) score -= 7.0;
        }

        // 领出时尽量留大牌在后手，也不要在中局先甩王/级牌
        if (e.lastPlay == null) {
            score -= p.key * 0.10;
            if (hand.size() > 8 && p.key >= 15 && p.cards.size() <= 2) score -= 10;
        }
        return score;
    }

    /** v7.2 经典打分，用于离线对照 */
    private static double scoreClassic(Engine e, int seat, Play p) {
        List<Card> hand = e.hands[seat];
        int before = handsCount(hand, e.level);
        List<Card> rest = new ArrayList<Card>(hand);
        for (Card c : p.cards) if (rest.contains(c)) rest.remove(c);
        int after = handsCount(rest, e.level);
        double run = (before - after) * 8.0 + p.cards.size() * 0.5;
        if (rest.isEmpty()) run += 120;

        double bomb = 0;
        if (p.power > 0) {
            bomb = p.power * 7.0 + (p.type == Play.KING_BOMB ? 40 : 0);
            int minOpp0 = e.minOpponentCards(seat);
            if (minOpp0 <= 5) bomb *= 0.35;
            if (rest.size() <= 6) bomb *= 0.5;
        }

        int minOpp = e.minOpponentCards(seat);
        int mateLeft = e.hands[e.teammate(seat)].size();
        double block = 0;
        if (e.lastPlay != null) {
            if (minOpp <= 8) block += 3.0;
            if (p.key >= 15 && minOpp > 6) block -= 2.0;
            if (p.key >= 15 && rest.size() > 10) block -= 2.0;
        }
        double mateAdj = 0;
        if (e.lastPlay != null && e.lastPlayer >= 0 && e.lastPlayer == e.teammate(seat)) mateAdj -= 25.0;
        if (e.lastPlay == null) {
            if (mateLeft <= 4) mateAdj += 5.0;
            if (mateLeft <= 2) mateAdj += 3.0;
        }
        double breakCost = 0;
        if (p.power == 0) {
            int[] cnt = new int[18];
            for (Card c : hand) if (!c.wild) cnt[c.rank]++;
            for (Card c : p.cards) {
                if (!c.wild && cnt[c.rank] >= 4 && p.cards.size() < 4) breakCost -= 14.0;
            }
            for (Card c : p.cards) if (c.wild) breakCost -= 6.0;
        }
        if (minOpp <= 2 && p.type == Play.SINGLE && p.key < 14) block -= 3.0;
        return run + block + mateAdj + breakCost - bomb;
    }

    // ===================== 主决策 =====================

    public static Play choose(Engine e, int seat, List<Play> cands, Play last) {
        int diff = e.diffFor(seat);
        if (diff == Engine.DIFF_PRIMARY) return choosePrimary(e, seat, cands, last);
        List<Play> beat = new ArrayList<Play>();
        for (Play p : cands) if (Rules.canBeat(last, p)) beat.add(p);
        if (beat.isEmpty()) return null;

        List<Card> hand = e.hands[seat];
        // 能一把走完就走完
        for (Play p : beat) if (p.cards.size() == hand.size()) return p;

        if (last == null) return chooseLead(e, seat, beat);
        return chooseFollow(e, seat, beat, last, diff);
    }

    private static boolean mateLeading(Engine e, int seat) {
        return e.lastPlayer >= 0 && e.lastPlayer != seat && (e.lastPlayer % 2) == (seat % 2);
    }

    private static Play cheapestBeat(List<Play> beat, boolean allowBomb) {
        Play best = null;
        for (Play p : beat) {
            if (!allowBomb && p.power > 0) continue;
            if (best == null || p.key < best.key || (p.key == best.key && p.cards.size() < best.cards.size())) best = p;
        }
        return best;
    }

    private static Play smallestBomb(List<Play> beat) {
        Play best = null;
        for (Play p : beat) {
            if (p.power <= 0) continue;
            if (best == null || p.power < best.power) best = p;
        }
        return best;
    }

    private static Play highestSingle(List<Play> beat) {
        Play best = null;
        for (Play p : beat) {
            if (p.type != Play.SINGLE) continue;
            if (best == null || p.key > best.key) best = p;
        }
        return best;
    }

    // 初级：能小压就压，炸弹保守，也不拆逢人配
    private static Play choosePrimary(Engine e, int seat, List<Play> cands, Play last) {
        List<Play> beat = new ArrayList<Play>();
        for (Play p : cands) if (Rules.canBeat(last, p)) beat.add(p);
        if (beat.isEmpty()) return null;
        List<Card> hand = e.hands[seat];
        for (Play p : beat) if (p.cards.size() == hand.size()) return p;
        List<Play> plain = new ArrayList<Play>();
        for (Play p : beat) if (!usesWildMulti(p)) plain.add(p);
        List<Play> pool = plain.isEmpty() ? beat : plain;
        if (last != null && mateLeading(e, seat)) return null;
        if (last == null && e.minOpponentCards(seat) <= 1) {
            Play hi = highestSingle(beat);
            List<Play> notSingle = new ArrayList<Play>();
            for (Play p : pool) if (p.type != Play.SINGLE) notSingle.add(p);
            if (!notSingle.isEmpty()) return cheapestBeat(notSingle, true);
            if (hi != null) return hi;
        }
        Play cheap = cheapestBeat(pool, false);
        if (cheap != null) return cheap;
        return null;
    }

    // 领出
    private static Play chooseLead(Engine e, int seat, List<Play> beat) {
        int minOpp = e.minOpponentCards(seat);
        List<Play> pool = beat;
        if (minOpp == 1) {
            // 对手只剩一张，别送单张
            List<Play> notSingle = new ArrayList<Play>();
            for (Play p : beat) if (p.type != Play.SINGLE) notSingle.add(p);
            if (!notSingle.isEmpty()) pool = notSingle;
            else return highestSingle(beat);
        } else if (minOpp == 2) {
            List<Play> notPair = new ArrayList<Play>();
            for (Play p : beat) if (!(p.type == Play.PAIR && p.key < 14)) notPair.add(p);
            if (!notPair.isEmpty()) pool = notPair;
        }
        Play best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Play p : pool) {
            double s = scoreAction(e, seat, p);
            // 领出时更在意"出完这手还剩几手"
            s += (beat.size() > 0 ? 0 : 0);
            if (s > bestScore) { bestScore = s; best = p; }
        }
        return best;
    }

    // 跟牌
    private static Play chooseFollow(Engine e, int seat, List<Play> beat, Play last, int diff) {
        List<Card> hand = e.hands[seat];
        int minOpp = e.minOpponentCards(seat);
        int mateLeft = e.hands[e.teammate(seat)].size();

        // 队友领出：让牌，除非自己能走完（已被上面处理）
        if (e.lastPlayer == e.teammate(seat)) return null;

        // 对手就要走完，必须压：先便宜牌，压不住就炸
        if (minOpp <= 2) {
            Play cheap = cheapestPlain(beat);
            if (cheap == null) cheap = cheapestBeat(beat, false);
            if (cheap != null) return cheap;
            Play bomb = smallestBomb(beat);
            if (bomb != null) return bomb;
            return null;
        }

        // 对手剩 3~4 张且是较大的组合，适当动炸弹
        if (minOpp <= 4 && last.size >= 3) {
            Play cheap = cheapestPlain(beat);
            if (cheap == null) cheap = cheapestBeat(beat, false);
            if (cheap != null && scoreAction(e, seat, cheap) > -4) return cheap;
            Play bomb = smallestBomb(beat);
            if (bomb != null) return bomb;
            if (cheap != null) return cheap;
            return null;
        }

        List<Play> ranked = rankByScore(e, seat, beat);
        if (ranked.isEmpty()) return null;
        Play top = ranked.get(0);
        double topScore = scoreAction(e, seat, top);

        // 残局：场上有人快走完或自己手牌不多时做推演
        int minActive = 99;
        for (int i = 0; i < 4; i++) {
            if (e.isFinished(i)) continue;
            minActive = Math.min(minActive, e.hands[i].size());
        }
        boolean endgame = (minActive <= 12 || hand.size() <= 12) && ranked.size() > 1;
        if (endgame && (diff == Engine.DIFF_ULTIMATE || e.thinkFor(seat) == 2)) {
            Play mc = monteCarlo(e, seat, ranked);
            if (mc != null) return mc;
        }

        if (top.power > 0) {
            if (minOpp > 6 && hand.size() > 8 && topScore < 4) return null;
        }
        if (topScore < -2 && minOpp > 4) return null;
        return top;
    }

    private static List<Play> rankByScore(Engine e, int seat, List<Play> beat) {
        List<Play> copy = new ArrayList<Play>(beat);
        final double[] sc = new double[copy.size()];
        for (int i = 0; i < copy.size(); i++) sc[i] = scoreAction(e, seat, copy.get(i));
        final List<Play> src = new ArrayList<Play>(copy);
        Integer[] idx = new Integer[src.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        final double[] fs = sc;
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                return fs[a] > fs[b] ? -1 : (fs[a] < fs[b] ? 1 : 0);
            }
        });
        copy.clear();
        for (int i = 0; i < idx.length; i++) copy.add(src.get(idx[i]));
        return copy;
    }

    /** 是否用了逢人配做替代（多张牌里带配） */
    private static boolean usesWildMulti(Play p) {
        if (p.cards.size() <= 1) return false;
        for (Card c : p.cards) if (c.wild) return true;
        return false;
    }

    private static Play cheapestPlain(List<Play> beat) {
        Play best = null;
        for (Play p : beat) {
            if (p.power > 0) continue;
            if (usesWildMulti(p)) continue;
            if (best == null || p.key < best.key || (p.key == best.key && p.cards.size() < best.cards.size())) best = p;
        }
        return best;
    }

    // ===================== 残局推演 =====================

    private static Play monteCarlo(Engine e, int seat, List<Play> ranked) {
        int n = Math.min(ranked.size(), 4);
        int tl = e.thinkFor(seat);
        int rollouts = (tl == 0) ? 3 : (tl == 1 ? 5 : 8);
        Play best = ranked.get(0);
        double bestVal = -1e9;
        for (int i = 0; i < n; i++) {
            Play cand = ranked.get(i);
            double total = 0;
            for (int k = 0; k < rollouts; k++) total += rollout(e, seat, cand);
            double val = total / rollouts + scoreAction(e, seat, cand) * 0.12;
            if (val > bestVal) { bestVal = val; best = cand; }
        }
        return best;
    }

    /** 模拟一次：各家按简化策略走到底，返回本队收益 */
    public static double rollout(Engine e, int seat, Play first) {
        List<Card>[] hands = new List[4];
        for (int i = 0; i < 4; i++) {
            hands[i] = new ArrayList<Card>();
            for (Card c : e.hands[i]) {
                Card nc = new Card(c.suit, c.rank);
                nc.id = c.id;
                nc.wild = c.wild;
                hands[i].add(nc);
            }
        }
        List<Integer> fin = new ArrayList<Integer>(e.finished);
        int lvl = e.level;

        removeCards(hands[seat], first.cards);
        if (hands[seat].isEmpty() && !fin.contains(seat)) fin.add(seat);

        Play axis = copyPlay(first);
        int leader = seat;
        int turn = nextActive(seat, hands, fin);
        int passes = 0;
        int steps = 0;

        while (steps++ < 260) {
            if (fin.size() >= 3) break;
            if (teamFinished(fin) >= 2) break;
            if (turn < 0) break;
            if (hands[turn].isEmpty()) { turn = nextActive(turn, hands, fin); continue; }

            Play move;
            if (axis == null) {
                move = simLead(hands[turn], lvl, turn, hands, fin);
            } else {
                move = simFollow(hands[turn], lvl, axis, turn, hands, fin);
            }

            if (move == null) {
                if (axis == null) { turn = nextActive(turn, hands, fin); continue; }
                passes++;
                if (passes >= 3) {
                    axis = null;
                    passes = 0;
                    int ld = leader;
                    if (fin.contains(ld)) {
                        int mate = (ld + 2) % 4;
                        if (!fin.contains(mate)) ld = mate;
                    }
                    turn = fin.contains(ld) ? nextActive(ld, hands, fin) : ld;
                } else {
                    turn = nextActive(turn, hands, fin);
                }
                continue;
            }
            removeCards(hands[turn], move.cards);
            leader = turn;
            axis = move;
            passes = 0;
            if (hands[turn].isEmpty() && !fin.contains(turn)) fin.add(turn);
            turn = nextActive(turn, hands, fin);
        }

        return evalRollout(fin, seat);
    }

    private static int teamFinished(List<Integer> fin) {
        if (fin.isEmpty()) return 0;
        int t = fin.get(0) % 2;
        int n = 0;
        for (int f : fin) if (f % 2 == t) n++;
        return n;
    }

    private static double evalRollout(List<Integer> fin, int seat) {
        if (fin.isEmpty()) return 0.35;
        int team = fin.get(0) % 2;
        if (team != seat % 2) return 0.0;
        int pos = 4;
        for (int i = 1; i < fin.size(); i++) if (fin.get(i) % 2 == team) pos = i + 1;
        int gain = pos == 2 ? 3 : (pos == 3 ? 2 : 1);
        return gain + (pos == 2 ? 0.6 : 0);
    }

    /** 模拟中的领出：优先能减少手数、且一次多甩牌 */
    private static Play simLead(List<Card> hand, int lvl, int turn, List<Card>[] hands, List<Integer> fin) {
        List<Play> cands = candidates(hand, lvl);
        if (cands.isEmpty()) return null;
        int before = handsCount(hand, lvl);
        Play best = null;
        double bestS = -1e9;
        int limit = Math.min(cands.size(), 60);
        for (int i = 0; i < limit; i++) {
            Play p = cands.get(i);
            if (p.power > 0 && hand.size() > 8 && RND.nextInt(100) < 80) continue;
            List<Card> rest = new ArrayList<Card>(hand);
            removeCards(rest, p.cards);
            int after = handsCount(rest, lvl);
            double s = (before - after) * 5 + p.cards.size() * 0.8 - p.key * 0.05;
            if (s > bestS) { bestS = s; best = p; }
        }
        return best;
    }

    /** 模拟中的跟牌：便宜压、能少手数、偶尔让牌 */
    private static Play simFollow(List<Card> hand, int lvl, Play axis, int turn,
                                  List<Card>[] hands, List<Integer> fin) {
        List<Play> cands = candidates(hand, lvl);
        int before = handsCount(hand, lvl);
        Play best = null;
        double bestS = -1e9;
        int limit = Math.min(cands.size(), 80);
        for (int i = 0; i < limit; i++) {
            Play p = cands.get(i);
            if (!Rules.canBeat(axis, p)) continue;
            if (p.power > 0) {
                int minOpp = 99;
                for (int s = 0; s < 4; s++) if (s != turn && s != (turn + 2) % 4 && !fin.contains(s)) minOpp = Math.min(minOpp, hands[s].size());
                if (minOpp > 5 && axis.size < 3 && RND.nextInt(100) < 75) continue;
            }
            List<Card> rest = new ArrayList<Card>(hand);
            removeCards(rest, p.cards);
            int after = handsCount(rest, lvl);
            double s = (before - after) * 4 - p.key * 0.35;
            if (p.power > 0) s -= p.power * 2 + 3;
            if (s > bestS) { bestS = s; best = p; }
        }
        if (best == null) return null;
        if (!Rules.canBeat(axis, best)) return null;
        // 偶尔选择不出，模拟真实牌手的保留
        if (bestS < -3 && RND.nextInt(100) < 45) return null;
        return best;
    }

    private static int nextActive(int from, List<Card>[] hands, List<Integer> fin) {
        for (int k = 1; k <= 4; k++) {
            int s = (from + k) % 4;
            if (fin.contains(s)) continue;
            return s;
        }
        return -1;
    }

    private static void removeCards(List<Card> hand, List<Card> cards) {
        for (Card c : cards) {
            for (int i = 0; i < hand.size(); i++) {
                if (hand.get(i).id == c.id) { hand.remove(i); break; }
            }
        }
    }

    private static Play copyPlay(Play src) {
        Play p = new Play();
        p.type = src.type;
        p.key = src.key;
        p.power = src.power;
        p.size = src.size;
        p.cards = new ArrayList<Card>(src.cards);
        return p;
    }
}
