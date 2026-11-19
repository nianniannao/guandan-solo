package com.guandan.solo;

import java.util.List;

/**
 * AI 统一入口：默认走 v7.2 基线实现，可按开关组合新版模块做离线对照。
 * variant 位掩码：2 新手数评估、4 新候选牌型、16 新决策。
 */
public class AI {

    public static int thinkLevel = 1;

    public static int variant = Integer.getInteger("ai.variant", 36);

    public static List<Play> candidates(List<Card> hand, int level) {
        if ((variant & 4) != 0) return AINew.candidates(hand, level);
        return AIOld.candidates(hand, level);
    }

    public static int handsCount(List<Card> hand, int level) {
        if ((variant & 2) != 0) return AINew.handsCount(hand, level);
        return AIOld.handsCount(hand, level);
    }

    public static Play choose(Engine e, int seat, List<Play> cands, Play last) {
        if ((variant & 16) != 0) return AINew.choose(e, seat, cands, last);
        return AIOld.choose(e, seat, cands, last);
    }
}
