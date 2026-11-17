package com.guandan.solo;

import java.util.ArrayList;
import java.util.List;

public class Rules {

    /** 牌力：2..A 为自然值，级牌升到 15，小王 16，大王 17 */
    public static int weight(int rank, int level) {
        if (rank >= 15) return rank + 1;
        if (rank == level) return 15;
        return rank;
    }

    /** 带逢人配展开的解析，返回最优牌型 */
    public static Play evaluate(List<Card> cards, int level) {
        if (cards == null || cards.isEmpty() || cards.size() > 12) return null;
        int wildCount = 0;
        for (Card c : cards) if (c.wild) wildCount++;
        if (wildCount == 0) return evaluateExact(cards, level);
        if (wildCount > 2) return evaluateExact(cards, level);

        List<Card> fixed = new ArrayList<Card>();
        for (Card c : cards) if (!c.wild) fixed.add(c);

        List<int[]> cands = new ArrayList<int[]>();
        for (int s = 0; s < 4; s++) for (int r = 2; r <= 14; r++) cands.add(new int[]{s, r});

        Play best = null;
        if (wildCount == 1) {
            for (int[] cd : cands) {
                List<Card> full = new ArrayList<Card>(fixed);
                Card wc = new Card(cd[0], cd[1]);
                full.add(wc);
                Play p = evaluateExact(full, level);
                if (p != null && isBetter(p, best)) best = p;
            }
        } else {
            for (int i = 0; i < cands.size(); i++) {
                for (int j = i; j < cands.size(); j++) {
                    List<Card> full = new ArrayList<Card>(fixed);
                    int[] a = cands.get(i);
                    int[] b = cands.get(j);
                    full.add(new Card(a[0], a[1]));
                    full.add(new Card(b[0], b[1]));
                    Play p = evaluateExact(full, level);
                    if (p != null && isBetter(p, best)) best = p;
                }
            }
        }
        if (best != null) best.cards = new ArrayList<Card>(cards);
        return best;
    }

    private static boolean isBetter(Play p, Play best) {
        if (best == null) return true;
        if (p.power != best.power) return p.power > best.power;
        if (p.key != best.key) return p.key > best.key;
        return p.type > best.type;
    }

    /** 严格解析（逢人配按自身面值算） */
    public static Play evaluateExact(List<Card> cards, int level) {
        if (cards == null || cards.isEmpty() || cards.size() > 12) return null;
        int n = cards.size();
        int[] cnt = new int[18];
        for (Card c : cards) cnt[c.rank]++;
        int distinct = 0;
        for (int r = 2; r <= 17; r++) if (cnt[r] > 0) distinct++;

        Play p = new Play();
        p.size = n;
        p.cards = new ArrayList<Card>(cards);

        // 天王炸
        if (n == 4 && cnt[15] == 2 && cnt[16] == 2) {
            p.type = Play.KING_BOMB;
            p.power = 100;
            p.key = 17;
            return p;
        }

        // 炸弹
        if (n >= 4 && distinct == 1) {
            for (int r = 2; r <= 14; r++) {
                if (cnt[r] == n) {
                    p.type = Play.BOMB;
                    p.power = n;
                    p.key = weight(r, level);
                    return p;
                }
            }
            return null;
        }

        if (n == 1) {
            p.type = Play.SINGLE;
            p.key = weight(cards.get(0).rank, level);
            return p;
        }
        if (n == 2 && distinct == 1 && cnt[15] == 0 && cnt[16] == 0) {
            p.type = Play.PAIR;
            p.key = weight(cards.get(0).rank, level);
            return p;
        }
        if (n == 2 && distinct == 1) {
            p.type = Play.PAIR;
            p.key = weight(cards.get(0).rank, level);
            return p;
        }
        if (n == 3 && distinct == 1) {
            p.type = Play.TRIPLE;
            p.key = weight(cards.get(0).rank, level);
            return p;
        }
        if (n == 5) {
            int three = -1, two = -1;
            for (int r = 2; r <= 17; r++) {
                if (cnt[r] == 3) three = r;
                else if (cnt[r] == 2) two = r;
            }
            if (three > 0 && two > 0) {
                p.type = Play.TRIPLE_PAIR;
                p.key = weight(three, level);
                return p;
            }
            // 顺子 / 同花顺
            if (cnt[15] == 0 && cnt[16] == 0 && distinct == 5) {
                int min = 99, max = 0;
                for (int r = 2; r <= 14; r++) if (cnt[r] > 0) { if (r < min) min = r; if (r > max) max = r; }
                if (max - min == 4) {
                    int s0 = cards.get(0).suit;
                    boolean sameSuit = true;
                    for (Card c : cards) if (c.suit != s0) sameSuit = false;
                    if (sameSuit && s0 != Card.JOKER) {
                        p.type = Play.STRAIGHT_FLUSH;
                        p.power = 5.5;
                        p.key = max;
                    } else {
                        p.type = Play.STRAIGHT;
                        p.key = max;
                    }
                    return p;
                }
            }
            return null;
        }
        if (n == 6) {
            // 三连对
            boolean consec = true;
            int min = 99, max = 0;
            int pairs = 0;
            for (int r = 2; r <= 17; r++) {
                if (cnt[r] == 2) pairs++;
                else if (cnt[r] != 0) { consec = false; }
                if (cnt[r] > 0) { if (r < min) min = r; if (r > max) max = r; }
            }
            if (consec && pairs == 3 && max - min == 2 && cnt[15] == 0 && cnt[16] == 0) {
                p.type = Play.CONSEC_PAIRS;
                p.key = max;
                return p;
            }
            // 钢板
            int triples = 0;
            boolean ok = true;
            min = 99; max = 0;
            for (int r = 2; r <= 17; r++) {
                if (cnt[r] == 3) triples++;
                else if (cnt[r] != 0) ok = false;
                if (cnt[r] > 0) { if (r < min) min = r; if (r > max) max = r; }
            }
            if (ok && triples == 2 && max - min == 1 && cnt[15] == 0 && cnt[16] == 0) {
                p.type = Play.PLATE;
                p.key = max;
                return p;
            }
            return null;
        }
        return null;
    }

    /** cur 能否压过 last（last 为 null 表示自由出牌） */
    public static boolean canBeat(Play last, Play cur) {
        if (cur == null) return false;
        if (last == null) return true;
        if (cur.power > 0 || last.power > 0) {
            if (cur.power == 0) return false;
            if (last.power == 0) return true;
            if (cur.power != last.power) return cur.power > last.power;
            return cur.key > last.key;
        }
        if (cur.type != last.type) return false;
        if (cur.size != last.size) return false;
        return cur.key > last.key;
    }
}
