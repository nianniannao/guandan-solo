package com.guandan.solo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Engine {

    public static final int DIFF_PRIMARY = 0;
    public static final int DIFF_ADVANCED = 1;
    public static final int DIFF_ULTIMATE = 2;

    public List<Card>[] hands;
    public int level = 2;
    public int difficulty = DIFF_PRIMARY;
    /** 按座位单独指定难度（用于强度对照，null 表示统一用 difficulty） */
    public int[] seatDiff = null;
    /** 三档思考强度，对应 初级/高级/终极：0 快 1 标准 2 深度 */
    public int[] thinkLevels = new int[]{1, 1, 1};

    public int diffFor(int seat) { return seatDiff == null ? difficulty : seatDiff[seat]; }

    public int thinkFor(int seat) {
        int d = diffFor(seat);
        if (d < 0) d = 0;
        if (d > 2) d = 2;
        return thinkLevels[d];
    }

    public int turn = 0;
    public int lastPlayer = -1;
    public int passes = 0;
    public Play lastPlay = null;
    /** 上一手是否为新一轮的首家领出（用于语音播报口径） */
    public boolean lastLead = false;

    public List<Integer> finished = new ArrayList<Integer>();
    public boolean roundOver = false;
    public boolean autoHuman = false;
    public boolean[] isAi = new boolean[]{true, true, true, true};
    public String[] seatTag = new String[4];
    public String message = "";
    public List<String> log = new ArrayList<String>();

    public int[] teamLevel = new int[]{2, 2};
    public int playingTeam = 0;
    public int roundNo = 0;

    public Play[] seatLast = new Play[4];
    public boolean[] seatPassed = new boolean[4];
    /** 本局已经打出的所有牌，供 AI 记牌 */
    public List<Card> played = new ArrayList<Card>();

    public int lastGain = 0;
    public String resultText = "";

    private Random rnd = new Random();

    public Engine() {
        hands = new List[4];
        for (int i = 0; i < 4; i++) hands[i] = new ArrayList<Card>();
    }

    public void newMatch(int diff) {
        difficulty = diff;
        teamLevel[0] = 2;
        teamLevel[1] = 2;
        playingTeam = 0;
        roundNo = 0;
        deal();
    }

    public void deal() {
        level = teamLevel[playingTeam];
        roundNo++;
        List<Card> deck = buildDeck();
        Collections.shuffle(deck, rnd);
        for (int i = 0; i < 4; i++) hands[i].clear();
        for (int i = 0; i < deck.size(); i++) hands[i % 4].add(deck.get(i));
        for (int i = 0; i < 4; i++) {
            hands[i].sort(new java.util.Comparator<Card>() {
                public int compare(Card a, Card b) {
                    int wa = Rules.weight(a.rank, level);
                    int wb = Rules.weight(b.rank, level);
                    if (wa == wb) return a.suit - b.suit;
                    return wa > wb ? -1 : 1;
                }
            });
        }
        finished.clear();
        roundOver = false;
        lastPlay = null;
        lastLead = false;
        lastPlayer = -1;
        passes = 0;
        turn = (roundNo == 1) ? 0 : playingTeam * 2;
        for (int i = 0; i < 4; i++) { seatLast[i] = null; seatPassed[i] = false; seatTag[i] = null; }
        played.clear();
        message = "第 " + roundNo + " 局 · 打 " + levelText(level);
        log.clear();
        addLog("开局，打 " + levelText(level) + "，红桃" + levelText(level) + "为逢人配");
        tribute();
    }

    private void tribute() {
        if (finished.size() > 0) return;
    }

    List<Card> buildDeck() {
        List<Card> deck = new ArrayList<Card>();
        for (int d = 0; d < 2; d++) {
            for (int s = 0; s < 4; s++) for (int r = 2; r <= 14; r++) deck.add(new Card(s, r));
            deck.add(new Card(Card.JOKER, 15));
            deck.add(new Card(Card.JOKER, 16));
        }
        for (int i = 0; i < deck.size(); i++) {
            Card c = deck.get(i);
            c.id = i;
            c.wild = (c.suit == Card.HEART && c.rank == level);
        }
        return deck;
    }

    public static String levelText(int lv) {
        switch (lv) {
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            case 14: return "A";
            default: return String.valueOf(lv);
        }
    }

    public void addLog(String s) {
        log.add(0, s);
        while (log.size() > 3) log.remove(log.size() - 1);
    }

    public int teammate(int seat) {
        return (seat + 2) % 4;
    }

    public boolean isFinished(int seat) {
        return finished.contains(seat);
    }

    public int activeCount() {
        int n = 0;
        for (int i = 0; i < 4; i++) if (!isFinished(i)) n++;
        return n;
    }

    public int minOpponentCards(int seat) {
        int min = 99;
        for (int i = 0; i < 4; i++) {
            if (i == seat || i == teammate(seat) || isFinished(i)) continue;
            min = Math.min(min, hands[i].size());
        }
        return min == 99 ? 0 : min;
    }

    /** 玩家请求出牌 */
    public String playCards(int seat, List<Card> cards) {
        if (cards == null || cards.isEmpty()) return "请先选择牌";
        Play p = Rules.evaluate(cards, level);
        if (p == null) return "牌型不合法";
        if (!Rules.canBeat(lastPlay, p)) return "牌太小，压不过上家";
        applyPlay(seat, p);
        return null;
    }

    private void applyPlay(int seat, Play p) {
        hands[seat].removeAll(p.cards);
        for (Card c : p.cards) played.add(c);
        seatLast[seat] = p;
        seatTag[seat] = p.typeName();
        seatPassed[seat] = false;
        for (int i = 0; i < 4; i++) if (i != seat) seatPassed[i] = false;
        lastLead = (lastPlay == null);
        lastPlay = p;
        lastPlayer = seat;
        passes = 0;
        addLog(seatName(seat) + " 出 " + p.typeName());
        if (hands[seat].isEmpty()) {
            finished.add(seat);
            addLog(seatName(seat) + " 出完了");
        }
        if (checkRoundEnd()) return;
        nextTurn(seat);
    }

    public void doPass(int seat) {
        seatPassed[seat] = true;
        seatLast[seat] = null;
        seatTag[seat] = "不要";
        passes++;
        addLog(seatName(seat) + " 不要");
        // 有人出完后，剩几家都不要就算这一轮结束：按在局人数算，而不是固定 3 次
        int activeN = 0;
        for (int i = 0; i < 4; i++) if (!isFinished(i)) activeN++;
        boolean leaderFinished = lastPlayer >= 0 && isFinished(lastPlayer);
        int need = leaderFinished ? activeN : Math.max(1, activeN - 1);
        if (passes >= need) {
            int leader;
            if (lastPlayer >= 0 && !isFinished(lastPlayer)) {
                leader = lastPlayer;
            } else if (lastPlayer >= 0 && !isFinished(teammate(lastPlayer))) {
                leader = teammate(lastPlayer);
                addLog(seatName(leader) + " 接风");
            } else {
                leader = nextActiveAfter(lastPlayer >= 0 ? lastPlayer : seat);
            }
            lastPlay = null;
            lastLead = false;
            lastPlayer = -1;
            passes = 0;
            for (int i = 0; i < 4; i++) { seatLast[i] = null; seatPassed[i] = false; seatTag[i] = null; }
            turn = leader;
        } else {
            nextTurn(seat);
        }
    }

    public int nextActiveSeat(int seat) { return nextActiveAfter(seat); }

    private int nextActiveAfter(int seat) {
        for (int k = 1; k <= 4; k++) {
            int s = (seat + k) % 4;
            if (!isFinished(s)) return s;
        }
        return seat;
    }

    private void nextTurn(int from) {
        turn = nextActiveAfter(from);
    }

    private boolean checkRoundEnd() {
        if (finished.isEmpty()) return false;
        if (finished.size() >= 3) { finishRound(); return true; }
        int t0 = finished.get(0) % 2;
        int cnt = 0;
        for (int s : finished) if (s % 2 == t0) cnt++;
        if (cnt >= 2) { finishRound(); return true; }
        return false;
    }

    private void finishRound() {
        roundOver = true;
        int team = finished.get(0) % 2;
        int matePos = 4;
        for (int i = 0; i < finished.size(); i++) {
            if (finished.get(i) % 2 == team && i > 0) matePos = i + 1;
        }
        int gain;
        if (matePos == 2) gain = 3;
        else if (matePos == 3) gain = 2;
        else gain = 1;
        lastGain = gain;
        int before = teamLevel[team];
        int after = Math.min(14, before + gain);
        teamLevel[team] = after;
        playingTeam = team;
        level = after;
        String[] tag = {"一游", "二游", "三游", "四游"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < finished.size(); i++) {
            sb.append(tag[i]).append("：").append(seatName(finished.get(i))).append("  ");
        }
        resultText = (team == 0 ? "我方" : "对方") + (gain == 3 ? "双上" : gain == 2 ? "一、三" : "一、四")
                + "，升 " + gain + " 级（" + levelText(before) + " → " + levelText(after) + "）";
        addLog(resultText);
        if (after >= 14 && before >= 14) resultText = "打 A 成功，本场结束！我方获胜";
    }

    public static String seatName(int seat) {
        switch (seat) {
            case 0: return "你";
            case 1: return "右家";
            case 2: return "对家";
            case 3: return "左家";
        }
        return "?";
    }

    /** AI 走一步，返回是否有动作 */
    public boolean aiStep() {
        if (roundOver || isFinished(turn)) return false;
        if (turn == 0 && !autoHuman) return false;
        if (!isAi[turn] && !autoHuman) return false;
        int seat = turn;
        List<Play> cands = AI.candidates(hands[seat], level);
        Play p = AI.choose(this, seat, cands, lastPlay);
        if (p == null) {
            if (lastPlay != null && canPass(seat)) {
                doPass(seat);
                return true;
            }
            // 自由出牌不能过：兜底出最小单张
            List<Card> h = hands[seat];
            if (h.isEmpty()) return false;
            Play s = Rules.evaluateExact(Collections.singletonList(h.get(h.size() - 1)), level);
            if (s != null) applyPlay(seat, s);
            return true;
        }
        applyPlay(seat, p);
        return true;
    }

    private boolean canPass(int seat) {
        return lastPlay != null;
    }

    /** 客户端用：把服务端快照灌进本地视图 */
    public void applySnapshot(int lvl, int t0, int t1, int round, int turnSeat, int lastSeat,
                              boolean over, String result, int gain, List<Card> my, int mySeat,
                              int[] counts, String[] tags, boolean[] finFlags, Play last,
                              List<Integer> finList, List<String> logsIn) {
        level = lvl;
        teamLevel[0] = t0;
        teamLevel[1] = t1;
        roundNo = round;
        turn = turnSeat;
        lastPlayer = lastSeat;
        roundOver = over;
        resultText = result == null ? "" : result;
        lastGain = gain;
        lastPlay = last;
        lastLead = false;
        for (int i = 0; i < 4; i++) {
            hands[i].clear();
            seatLast[i] = null;
            seatTag[i] = tags != null && tags.length > i ? tags[i] : null;
            seatPassed[i] = tags != null && tags.length > i && "不要".equals(tags[i]);
        }
        for (Card c : my) c.wild = (c.suit == Card.HEART && c.rank == level);
        hands[mySeat].addAll(my);
        for (int i = 0; i < 4; i++) {
            if (i == mySeat) continue;
            int n = counts != null && counts.length > i ? counts[i] : 0;
            for (int k = 0; k < n; k++) hands[i].add(new Card(Card.SPADE, 2));
        }
        finished.clear();
        if (finList != null) finished.addAll(finList);
        log.clear();
        if (logsIn != null) log.addAll(logsIn);
        if (over) seatLast[lastSeat >= 0 ? lastSeat : 0] = last;
    }
}
