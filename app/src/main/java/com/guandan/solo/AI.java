package com.guandan.solo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 掼蛋 AI：规则打分 + 记牌推断 + 残局模拟。
 * 纯代码实现，不做神经网络推理，手机端零负担。
 */
public class AI {

    /** 思考强度：0 快、1 标准、2 深（由界面设置，只影响终极难度） */
    public static int thinkLevel = 1;

    private static final Random RND = new Random();

    // ===================== 候选牌型 =====================

    public static List<Play> candidates(List<Card> hand, int level) {
        List<Play> out = new ArrayList<Play>();
        int[] cnt = new int[18];
        for (Card c : hand) cnt[c.rank]++;
        int wild = 0;
        for (Card c : hand) if (c.wild) wild++;

        for (int r = 2; r <= 16; r++) if (cnt[r] > 0) add(out, pick(hand, r, 1), level);
        for (int r = 2; r <= 17; r++) {
            if (cnt[r] < 2) continue;
            add(out, pick(hand, r, 2), level);
            if (cnt[r] >= 3) add(out, pick(hand, r, 3), level);
            if (r >= 2 && r <= 14 && cnt[r] >= 4) add(out, pick(hand, r, cnt[r]), level);
        }
        int[] realCnt = new int[18];
        for (Card c : hand) if (!c.wild) realCnt[c.rank]++;
        for (int r = 2; r <= 14; r++) {
            if (realCnt[r] + (cnt[r] - realCnt[r]) < 4) continue;
            if (cnt[r] >= 4) add(out, pick(hand, r, cnt[r]), level);
        }

        for (int a = 2; a <= 14; a++) {
            if (cnt[a] < 3) continue;
            for (int b = 2; b <= 16; b++) {
                if (b == a || cnt[b] < 2) continue;
                List<Card> cs = pick(hand, a, 3);
                List<Card> two = pick(hand, b, 2);
                if (two.size() < 2) continue;
                cs.addAll(two);
                add(out, cs, level);
            }
        }
        for (int s = 2; s <= 10; s++) {
            List<Card> cs = new ArrayList<Card>();
            boolean ok = true;
            for (int r = s; r < s + 5; r++) {
                List<Card> one = pick(hand, r, 1);
                if (one.isEmpty()) { ok = false; break; }
                cs.add(one.get(0));
            }
            if (ok) add(out, cs, level);
            for (int su = 0; su < 4; su++) {
                List<Card> sf = new ArrayList<Card>();
                boolean okSf = true;
                for (int r = s; r < s + 5; r++) {
                    Card c = findSuit(hand, su, r);
                    if (c == null) { okSf = false; break; }
                    sf.add(c);
                }
                if (okSf) add(out, sf, level);
            }
        }
        for (int s = 2; s <= 12; s++) {
            List<Card> cs = new ArrayList<Card>();
            boolean ok = true;
            for (int r = s; r < s + 3; r++) {
                List<Card> two = pick(hand, r, 2);
                if (two.size() < 2) { ok = false; break; }
                cs.addAll(two);
            }
            if (ok) add(out, cs, level);
        }
        for (int s = 2; s <= 13; s++) {
            List<Card> cs = new ArrayList<Card>();
            boolean ok = true;
            for (int r = s; r < s + 2; r++) {
                List<Card> three = pick(hand, r, 3);
                if (three.size() < 3) { ok = false; break; }
                cs.addAll(three);
            }
            if (ok) add(out, cs, level);
        }
        if (cnt[15] >= 2 && cnt[16] >= 2) {
            List<Card> cs = new ArrayList<Card>();
            cs.addAll(pick(hand, 15, 2));
            cs.addAll(pick(hand, 16, 2));
            add(out, cs, level);
        }
        return out;
    }

    private static Card findSuit(List<Card> hand, int suit, int rank) {
        for (Card c : hand) if (c.suit == suit && c.rank == rank) return c;
        return null;
    }

    private static List<Card> pick(List<Card> hand, int rank, int count) {
        List<Card> res = new ArrayList<Card>();
        for (Card c : hand) if (c.rank == rank && !c.wild && res.size() < count) res.add(c);
        for (Card c : hand) if (c.rank == rank && c.wild && res.size() < count && !res.contains(c)) res.add(c);
        return res;
    }

    private static void add(List<Play> out, List<Card> cards, int level) {
        if (cards == null || cards.isEmpty()) return;
        Play p = Rules.evaluateExact(cards, level);
        if (p == null) return;
        for (Play q : out) if (q.type == p.type && q.key == p.key && q.size == p.size) return;
        out.add(p);
    }

    // ===================== 手牌质量 =====================

    /** 手牌能拆成几手牌（越少越容易跑完） */
    public static int handsCount(List<Card> hand, int level) {
        int[] cnt = new int[18];
        for (Card c : hand) cnt[c.rank]++;
        int combos = 0;
        for (int r = 2; r <= 14; r++) {
            if (cnt[r] >= 4) { combos++; cnt[r] = 0; }
        }
        for (int r = 2; r <= 17; r++) {
            if (cnt[r] >= 3) { combos++; cnt[r] -= 3; }
            else if (cnt[r] == 2) { combos++; cnt[r] = 0; }
            else if (cnt[r] == 1) { combos++; cnt[r] = 0; }
        }
        return Math.max(1, combos);
    }

    // ===================== 动作打分 =====================

    private static double scoreAction(Engine e, int seat, Play p) {
        List<Card> hand = e.hands[seat];
        int before = handsCount(hand, e.level);
        List<Card> rest = new ArrayList<Card>(hand);
        rest.removeAll(p.cards);
        if (rest.size() != hand.size() - p.cards.size()) {
            // 逢人配的虚拟牌不在手上，按张数近似
            rest = new ArrayList<Card>(hand);
            for (Card c : p.cards) if (rest.contains(c)) rest.remove(c);
        }
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
        // 队友领出时不抢
        double mateAdj = 0;
        if (e.lastPlay != null && e.lastPlayer >= 0 && e.lastPlayer == e.teammate(seat)) mateAdj -= 25.0;
        if (e.lastPlay == null) {
            if (mateLeft <= 4) mateAdj += 5.0;
            if (mateLeft <= 2) mateAdj += 3.0;
        }
        // 拆炸弹/拆逢人配的惩罚
        double breakCost = 0;
        if (p.power == 0) {
            int[] cnt = new int[18];
            for (Card c : hand) if (!c.wild) cnt[c.rank]++;
            for (Card c : p.cards) {
                if (!c.wild && cnt[c.rank] >= 4 && p.cards.size() < 4) breakCost -= 14.0;
            }
            for (Card c : p.cards) if (c.wild) breakCost -= 6.0;
        }
        // 对手只剩一两张时不轻易放单张
        if (minOpp <= 2 && p.type == Play.SINGLE && p.key < 14) block -= 3.0;
        return run + block + mateAdj + breakCost - bomb;
    }

    // ===================== 主决策 =====================

    public static Play choose(Engine e, int seat, List<Play> cands, Play last) {
        int diff = e.diffFor(seat);
        if (diff == Engine.DIFF_PRIMARY) return choosePrimary(e, seat, cands, last);
        if (diff == Engine.DIFF_ADVANCED) {
            Play p = chooseAdvanced(e, seat, cands, last);
            if (e.thinkFor(seat) == 2 && p != null && e.lastPlay != null) {
                int min = e.minOpponentCards(seat);
                if (min <= 12 && e.hands[seat].size() > 0 && p.cards.size() < e.hands[seat].size()) {
                    List<Play> beat = new ArrayList<Play>();
                    for (Play c : cands) if (Rules.canBeat(last, c)) beat.add(c);
                    if (beat.size() > 1) {
                        List<Play> ranked = rankByScore(e, seat, beat);
                        Play deep = monteCarloLimited(e, seat, ranked, 6);
                        if (deep != null) return deep;
                    }
                }
            }
            return p;
        }
        return chooseUltimate(e, seat, cands, last);
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

    // 初级：能小压就压，炸弹保守
    private static Play choosePrimary(Engine e, int seat, List<Play> cands, Play last) {
        List<Play> beat = new ArrayList<Play>();
        for (Play p : cands) if (Rules.canBeat(last, p)) beat.add(p);
        if (beat.isEmpty()) return null;
        List<Card> hand = e.hands[seat];
        for (Play p : beat) if (p.cards.size() == hand.size()) return p;
        if (mateLeading(e, seat)) return null;
        Play cheap = cheapestBeat(beat, false);
        if (cheap != null) return cheap;
        return null;
    }

    // 高级：打分选优，压队友时让牌，炸弹按价值用
    private static Play chooseAdvanced(Engine e, int seat, List<Play> cands, Play last) {
        List<Play> beat = new ArrayList<Play>();
        for (Play p : cands) if (Rules.canBeat(last, p)) beat.add(p);
        if (beat.isEmpty()) return null;
        List<Card> hand = e.hands[seat];
        for (Play p : beat) if (p.cards.size() == hand.size()) return p;
        if (mateLeading(e, seat)) {
            // 队友领出，除非能一把走完，否则让牌
            return null;
        }
        Play best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Play p : beat) {
            double s = scoreAction(e, seat, p);
            if (s > bestScore) { bestScore = s; best = p; }
        }
        if (best == null) return null;
        int minOpp = e.minOpponentCards(seat);
        if (best.power > 0) {
            // 炸弹：对手没危险就留着，除非能收尾
            if (minOpp > 6 && hand.size() > 8 && bestScore < 5) return null;
        }
        if (e.lastPlay != null && bestScore < 0 && minOpp > 4) return null;
        return best;
    }

    // 终极：打分 + 残局蒙特卡洛
    private static Play chooseUltimate(Engine e, int seat, List<Play> cands, Play last) {
        List<Play> beat = new ArrayList<Play>();
        for (Play p : cands) if (Rules.canBeat(last, p)) beat.add(p);
        if (beat.isEmpty()) return null;
        List<Card> hand = e.hands[seat];
        for (Play p : beat) if (p.cards.size() == hand.size()) return p;
        if (mateLeading(e, seat)) {
            return null;
        }

        List<Play> ranked = rankByScore(e, seat, beat);
        if (ranked.isEmpty()) return null;

        int minActive = 99;
        for (int i = 0; i < 4; i++) {
            if (e.isFinished(i)) continue;
            if (e.hands[i].size() < minActive) minActive = e.hands[i].size();
        }
        boolean endgame = minActive <= 16 && ranked.size() > 1;
        if (!endgame) {
            Play top = ranked.get(0);
            if (top.power > 0 && e.minOpponentCards(seat) > 6 && hand.size() > 8
                    && scoreAction(e, seat, top) < 5) return null;
            if (e.lastPlay != null && scoreAction(e, seat, top) < -2 && e.minOpponentCards(seat) > 3) return null;
            return top;
        }
        return monteCarlo(e, seat, ranked, last);
    }

    private static List<Play> rankByScore(Engine e, int seat, List<Play> beat) {
        List<Play> copy = new ArrayList<Play>(beat);
        final Engine ee = e;
        final int ss = seat;
        Collections.sort(copy, new java.util.Comparator<Play>() {
            public int compare(Play a, Play b) {
                double sa = scoreAction(ee, ss, a);
                double sb = scoreAction(ee, ss, b);
                return sa > sb ? -1 : (sa < sb ? 1 : 0);
            }
        });
        return copy;
    }

    // ===================== 残局蒙特卡洛 =====================

    private static Play monteCarloLimited(Engine e, int seat, List<Play> ranked, int rollouts) {
        Play best = ranked.get(0);
        double bestVal = -1e9;
        int n = Math.min(ranked.size(), 5);
        for (int i = 0; i < n; i++) {
            Play p = ranked.get(i);
            double total = 0;
            for (int k = 0; k < rollouts; k++) total += rollout(e, seat, p);
            double val = total / rollouts;
            if (val > bestVal) { bestVal = val; best = p; }
        }
        return best;
    }

    private static Play monteCarlo(Engine e, int seat, List<Play> ranked, Play last) {
        int n = Math.min(ranked.size(), 5);
        int tl = e.thinkFor(seat);
        int rollouts = (tl == 0) ? 3 : (tl == 1 ? 8 : 14);

        Play best = ranked.get(0);
        double bestVal = -1e9;
        for (int i = 0; i < n; i++) {
            Play cand = ranked.get(i);
            double total = 0;
            for (int k = 0; k < rollouts; k++) {
                total += rollout(e, seat, cand);
            }
            double val = total / rollouts;
            val += scoreAction(e, seat, cand) * 0.15;
            if (val > bestVal) { bestVal = val; best = cand; }
        }
        return best;
    }

    /** 模拟一次：本队按当前候选走，其余按贪心策略走到底，返回本队收益 */
    private static double rollout(Engine e, int seat, Play first) {
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
        Play axis = copyPlay(first);
        List<Card> h0 = hands[seat];
        removeCards(h0, first.cards);
        hands[seat] = h0;
        List<Integer> fin = new ArrayList<Integer>(e.finished);
        int lvl = e.level;
        int[] tlev = new int[]{e.teamLevel[0], e.teamLevel[1]};
        int turn = e.nextActiveSeat(seat);
        int lastSeat = e.teammate(seat);
        int passes = 0;
        boolean lead = false;

        int steps = 0;
        while (steps++ < 220) {
            if (fin.size() >= 3) break;
            int team = fin.isEmpty() ? -1 : fin.get(0) % 2;
            if (team >= 0) {
                int cntSame = 0;
                for (int f : fin) if (f % 2 == team) cntSame++;
                if (cntSame >= 2) break;
            }
            if (hands[turn].isEmpty()) { turn = nextActive(turn, hands, fin); continue; }
            Play move = null;
            if (!lead) {
                List<Play> cands = candidates(hands[turn], lvl);
                List<Play> beatList = new ArrayList<Play>();
                for (Play p : cands) if (Rules.canBeat(axis, p)) beatList.add(p);
                if (!beatList.isEmpty() && (RND.nextInt(100) < 82 || turn == e.teammate(seat))) {
                    move = cheapestBeat(beatList, false);
                }
                if (move == null && !beatList.isEmpty() && RND.nextInt(100) < 45) {
                    move = smallestBomb(beatList);
                    if (move != null && hands[turn].size() > 8 && axis.size <= 2) move = null;
                }
                if (move == null) {
                    // 过牌
                    passes++;
                    lastSeat = lastSeat;
                    if (passes >= 3) { axis = null; lead = true; passes = 0; turn = lastSeat >= 0 ? (fin.contains(lastSeat) ? nextActive(lastSeat, hands, fin) : lastSeat) : nextActive(turn, hands, fin); }
                    else turn = nextActive(turn, hands, fin);
                    continue;
                }
            } else {
                List<Play> cands = candidates(hands[turn], lvl);
                move = biggestCombo(cands);
            }
            if (move == null) { turn = nextActive(turn, hands, fin); continue; }
            removeCards(hands[turn], move.cards);
            if (hands[turn].isEmpty() && !fin.contains(turn)) fin.add(turn);
            lastSeat = turn;
            axis = move;
            lead = false;
            passes = 0;
            turn = nextActive(turn, hands, fin);
        }

        if (fin.isEmpty()) return 0.4;
        int team = fin.get(0) % 2;
        int myTeam = seat % 2;
        if (team != myTeam) return 0.0;
        int pos = 4;
        for (int i = 1; i < fin.size(); i++) if (fin.get(i) % 2 == team) pos = i + 1;
        int gain = pos == 2 ? 3 : (pos == 3 ? 2 : 1);
        return gain + 0.8;
    }

    private static int nextActive(int from, List<Card>[] hands, List<Integer> fin) {
        for (int k = 1; k <= 4; k++) {
            int s = (from + k) % 4;
            if (fin.contains(s)) continue;
            return s;
        }
        return from;
    }

    private static Play biggestCombo(List<Play> cands) {
        Play best = null;
        for (Play p : cands) {
            if (p.power > 0) continue;
            if (best == null || p.cards.size() > best.cards.size()) best = p;
        }
        if (best == null) best = cands.isEmpty() ? null : cands.get(0);
        return best;
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
