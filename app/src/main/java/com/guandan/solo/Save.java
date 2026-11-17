package com.guandan.solo;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 单机进度存档：退出后再次进入可继续上一局 */
public class Save {

    private static final String PREFS = "guandan";
    private static final String KEY = "progress_v1";

    public static boolean has(Context ctx) {
        try {
            String s = ctx.getSharedPreferences(PREFS, 0).getString(KEY, "");
            return s != null && s.length() > 10;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void clear(Context ctx) {
        try {
            ctx.getSharedPreferences(PREFS, 0).edit().remove(KEY).commit();
        } catch (Throwable e) { }
    }

    public static void save(Context ctx, Engine e, int mySeat, boolean sortAsc) {
        if (e == null || e.roundOver) return;
        try {
            ctx.getSharedPreferences(PREFS, 0).edit()
                    .putString(KEY, encode(e, mySeat, sortAsc)).commit();
        } catch (Throwable t) { }
    }

    /** 纯逻辑编码，便于桌面测试 */
    public static String encode(Engine e, int mySeat, boolean sortAsc) {
        if (e == null) return "";
        try {
            Map<String, Object> m = new LinkedHashMap3();
            m.put("lvl", e.level);
            m.put("diff", e.difficulty);
            m.put("turn", e.turn);
            m.put("lastSeat", e.lastPlayer);
            m.put("passes", e.passes);
            m.put("round", e.roundNo);
            m.put("t0", e.teamLevel[0]);
            m.put("t1", e.teamLevel[1]);
            m.put("pt", e.playingTeam);
            m.put("gain", e.lastGain);
            m.put("result", e.resultText);
            m.put("mySeat", mySeat);
            m.put("sortAsc", sortAsc);

            List<Object> hands = new ArrayList<Object>();
            for (int i = 0; i < 4; i++) {
                List<Object> h = new ArrayList<Object>();
                for (Card c : e.hands[i]) h.add(c.id);
                hands.add(h);
            }
            m.put("hands", hands);

            List<Object> fin = new ArrayList<Object>();
            for (int s : e.finished) fin.add(s);
            m.put("fin", fin);

            List<Object> ai = new ArrayList<Object>();
            for (int i = 0; i < 4; i++) ai.add(e.isAi[i]);
            m.put("ai", ai);

            List<Object> tags = new ArrayList<Object>();
            for (int i = 0; i < 4; i++) tags.add(e.seatTag[i] == null ? "" : e.seatTag[i]);
            m.put("tags", tags);

            List<Object> logs = new ArrayList<Object>();
            for (String s : e.log) logs.add(s);
            m.put("logs", logs);

            if (e.lastPlay != null) {
                Map<String, Object> lm = new LinkedHashMap3();
                lm.put("type", e.lastPlay.type);
                lm.put("key", e.lastPlay.key);
                lm.put("power", e.lastPlay.power);
                lm.put("size", e.lastPlay.size);
                List<Object> ids = new ArrayList<Object>();
                for (Card c : e.lastPlay.cards) ids.add(c.id);
                lm.put("cards", ids);
                m.put("last", lm);
            }
            return Json.str(m);
        } catch (Throwable t) {
            return "";
        }
    }

    /** meta[0]=难度，meta[1]=排序方向(0降序 1升序) */
    public static Engine load(Context ctx, int[] meta) {
        try {
            String raw = ctx.getSharedPreferences(PREFS, 0).getString(KEY, "");
            return decode(raw, meta);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Engine decode(String raw, int[] meta) {
        try {
            if (raw == null || raw.length() < 10) return null;
            Map<String, Object> m = Json.m(Json.parse(raw));
            Engine e = new Engine();
            e.level = Json.i(m.get("lvl"));
            e.difficulty = Json.i(m.get("diff"));
            e.turn = Json.i(m.get("turn"));
            e.lastPlayer = Json.i(m.get("lastSeat"));
            e.passes = Json.i(m.get("passes"));
            e.roundNo = Json.i(m.get("round"));
            e.teamLevel[0] = Json.i(m.get("t0"));
            e.teamLevel[1] = Json.i(m.get("t1"));
            e.playingTeam = Json.i(m.get("pt"));
            e.lastGain = Json.i(m.get("gain"));
            e.resultText = Json.s(m.get("result"));
            e.roundOver = false;

            List<Card> deck = e.buildDeck();
            Map<Integer, Card> byId = new HashMap<Integer, Card>();
            for (Card c : deck) byId.put(Integer.valueOf(c.id), c);

            List<Object> hands = Json.l(m.get("hands"));
            for (int i = 0; i < 4; i++) {
                e.hands[i].clear();
                if (i >= hands.size()) continue;
                for (Object o : Json.l(hands.get(i))) {
                    Card c = byId.get(Integer.valueOf(Json.i(o)));
                    if (c != null) e.hands[i].add(c);
                }
            }

            e.finished.clear();
            for (Object o : Json.l(m.get("fin"))) e.finished.add(Integer.valueOf(Json.i(o)));

            List<Object> ai = Json.l(m.get("ai"));
            for (int i = 0; i < 4 && i < ai.size(); i++) e.isAi[i] = Json.b(ai.get(i));

            List<Object> tags = Json.l(m.get("tags"));
            for (int i = 0; i < 4 && i < tags.size(); i++) {
                String t = Json.s(tags.get(i));
                e.seatTag[i] = t.length() == 0 ? null : t;
            }

            e.log.clear();
            for (Object o : Json.l(m.get("logs"))) e.log.add(Json.s(o));

            Map<String, Object> lm = Json.m(m.get("last"));
            if (!lm.isEmpty()) {
                Play p = new Play();
                p.type = Json.i(lm.get("type"));
                p.key = Json.i(lm.get("key"));
                p.power = ((Number) (lm.get("power") == null ? 0 : lm.get("power"))).doubleValue();
                p.size = Json.i(lm.get("size"));
                p.cards = new ArrayList<Card>();
                for (Object o : Json.l(lm.get("cards"))) {
                    Card c = byId.get(Integer.valueOf(Json.i(o)));
                    if (c != null) p.cards.add(c);
                }
                e.lastPlay = p;
                if (e.lastPlayer >= 0) e.seatLast[e.lastPlayer] = p;
            } else {
                e.lastPlay = null;
            }

            if (meta != null && meta.length >= 2) {
                meta[0] = e.difficulty;
                meta[1] = Json.i(m.get("sortAsc"));
            }
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 存档里给卡片/牌型用的有序 Map */
    private static class LinkedHashMap3 extends java.util.LinkedHashMap<String, Object> { }

    public static int[] quickInfo(Context ctx) {
        try {
            String raw = ctx.getSharedPreferences(PREFS, 0).getString(KEY, "");
            Map<String, Object> m = Json.m(Json.parse(raw));
            return new int[]{Json.i(m.get("round")), Json.i(m.get("lvl")), Json.i(m.get("diff"))};
        } catch (Throwable t) {
            return new int[]{0, 0, 0};
        }
    }
}
