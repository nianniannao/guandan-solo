package com.guandan.solo;

import java.util.ArrayList;
import java.util.List;

public class Play {
    public static final int NONE = 0;
    public static final int SINGLE = 1;
    public static final int PAIR = 2;
    public static final int TRIPLE = 3;
    public static final int TRIPLE_PAIR = 4;
    public static final int STRAIGHT = 5;
    public static final int CONSEC_PAIRS = 6;
    public static final int PLATE = 7;
    public static final int STRAIGHT_FLUSH = 8;
    public static final int BOMB = 9;
    public static final int KING_BOMB = 10;

    public int type = NONE;
    public int key = 0;
    public int size = 0;
    public double power = 0;   // 0 = 普通牌型，炸弹按等级
    public List<Card> cards = new ArrayList<Card>();

    public String typeName() {
        switch (type) {
            case SINGLE: return "单张";
            case PAIR: return "对子";
            case TRIPLE: return "三张";
            case TRIPLE_PAIR: return "三带二";
            case STRAIGHT: return "顺子";
            case CONSEC_PAIRS: return "三连对";
            case PLATE: return "钢板";
            case STRAIGHT_FLUSH: return "同花顺";
            case BOMB: return size + "张炸弹";
            case KING_BOMB: return "天王炸";
            default: return "无";
        }
    }
}
