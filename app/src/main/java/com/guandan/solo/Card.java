package com.guandan.solo;

public class Card {
    public static final int SPADE = 0, HEART = 1, CLUB = 2, DIAMOND = 3, JOKER = 4;

    public int id;
    public int suit;
    public int rank;      // 2..14 = 2..A, 15 = 小王, 16 = 大王
    public boolean wild;  // 红桃级牌 / 逢人配

    public Card(int suit, int rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public boolean isJoker() {
        return rank >= 15;
    }

    public String rankText() {
        switch (rank) {
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            case 14: return "A";
            case 15: return "小";
            case 16: return "大";
            default: return String.valueOf(rank);
        }
    }

    public String suitText() {
        switch (suit) {
            case SPADE: return "\u2660";
            case HEART: return "\u2665";
            case CLUB: return "\u2663";
            case DIAMOND: return "\u2666";
            default: return "";
        }
    }

    public boolean isRed() {
        return suit == HEART || suit == DIAMOND || rank == 16;
    }

    public String name() {
        if (isJoker()) return rank == 15 ? "小王" : "大王";
        return suitText() + rankText();
    }

    public Card copy() {
        Card c = new Card(suit, rank);
        c.wild = wild;
        c.id = id;
        return c;
    }
}
