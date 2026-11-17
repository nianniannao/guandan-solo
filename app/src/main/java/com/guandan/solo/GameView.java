package com.guandan.solo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class GameView extends View {

    private static final int MENU = 0;
    private static final int GAME = 1;

    private int screen = MENU;
    private float d;

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCardBg = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int C_BG = 0xFF0A0C0F;
    private static final int C_BG2 = 0xFF14181E;
    private static final int C_PANEL = 0xFF141A20;
    private static final int C_TEXT = 0xFFE9E4D8;
    private static final int C_SUB = 0xFF8A939E;
    private static final int C_RED = 0xFFB4473E;
    private static final int C_BLACK = 0xFF23282F;
    private static final int C_CARD = 0xFFF3F0E8;
    // 五行：木 火 土 金 水
    private static final int E_WOOD = 0xFF4E8E6A;
    private static final int E_FIRE = 0xFFB4473E;
    private static final int E_EARTH = 0xFFC8A96A;
    private static final int E_METAL = 0xFFD6D2C6;
    private static final int E_WATER = 0xFF4C6E96;

    private int accent = E_EARTH;

    private Engine engine = new Engine();
    private final List<Card> myHand = new ArrayList<Card>();
    private final List<Card> selected = new ArrayList<Card>();

    private float cardW, cardH, handTop, handBottom;
    private float[] cardX = new float[0];
    private float cardStep = 0;

    private final RectF btnPlay = new RectF();
    private final RectF btnPass = new RectF();
    private final RectF btnHint = new RectF();
    private final RectF btnSort = new RectF();
    private final RectF btnExit = new RectF();
    private final RectF btnContinue = new RectF();
    private final RectF[] btnMenu = new RectF[3];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean sortAsc = false;
    private String toast = "";
    private boolean matchOver = false;

    private int mySeat = 0;
    private boolean netMode = false;
    private boolean isHost = false;
    private Net.Host netHost;
    private Net.Client netClient;
    private final List<Integer> clientSeats = new ArrayList<Integer>();
    private String netInfo = "";
    private final RectF btnLanHost = new RectF();
    private final RectF btnLanJoin = new RectF();
    private final RectF btnLanScan = new RectF();
    private final RectF btnResume = new RectF();
    private String roomId = "";
    private String lastIp = "";
    private String lastRoom = "";
    private int lastSeat = 0;
    private boolean rejoining = false;
    private int retryLeft = 0;
    private long turnDeadline = 0;
    private static final long TURN_MS = 22000L;
    private final RectF btnVoice = new RectF();
    private Voice voice;
    private boolean voiceOn = true;
    private String voiceSig = "";
    private String turnPromptSig = "";
    private final String[] lastSeatTags = new String[]{"", "", "", ""};
    private boolean saveChecked = false;
    private final int[] thinkLevels = new int[]{1, 1, 1};
    private boolean aiThinking = false;
    private final RectF btnThinkSet = new RectF();

    private String[] names = new String[]{"震·雷动", "坤·厚德", "兑·泽悦"};

    public GameView(Context ctx) {
        super(ctx);
        try {
            android.content.SharedPreferences sp = ctx.getSharedPreferences("guandan", 0);
            for (int i = 0; i < 3; i++) thinkLevels[i] = sp.getInt("think_" + i, 1);
            AI.thinkLevel = thinkLevels[2];
            voice = new Voice(ctx);
            voiceOn = ctx.getSharedPreferences("guandan", 0).getBoolean("voice", true);
            voice.setEnabled(voiceOn);
        } catch (Throwable e) {
            voiceOn = false;
        }
        d = ctx.getResources().getDisplayMetrics().density;
        cardW = dp(62);
        cardH = dp(96);
        setFocusable(true);
        pCardBg.setStyle(Paint.Style.FILL);
        pText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    private float dp(float v) { return v * d; }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!saveChecked) {
            saveChecked = true;
            handler.postDelayed(promptResume, 400);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        persist();
        super.onDetachedFromWindow();
        try { if (voice != null) voice.shutdown(); } catch (Throwable e) { }
    }

    private void persist() {
        try {
            if (netMode) return;
            if (engine.roundOver) { Save.clear(getContext()); return; }
            Save.save(getContext(), engine, mySeat, sortAsc);
        } catch (Throwable e) { }
    }

    private final Runnable promptResume = new Runnable() {
        public void run() {
            if (screen != MENU || netMode) return;
            if (!Save.has(getContext())) return;
            int[] info = Save.quickInfo(getContext());
            try {
                Activity act = (Activity) getContext();
                new AlertDialog.Builder(act)
                        .setTitle("继续上一局？")
                        .setMessage("检测到未打完的牌局：第 " + info[0] + " 局 · 打 "
                                + Engine.levelText(info[1]))
                        .setPositiveButton("继续", new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) { resumeGame(); }
                        })
                        .setNegativeButton("重新开始", new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                Save.clear(getContext());
                                showToast("已清空上一局进度");
                            }
                        })
                        .setCancelable(false)
                        .show();
            } catch (Exception e) {
                // 弹窗失败就留在首页
            }
        }
    };

    private void resumeGame() {
        int[] meta = new int[2];
        Engine e = Save.load(getContext(), meta);
        if (e == null) {
            Save.clear(getContext());
            showToast("存档已损坏，已清除");
            return;
        }
        stopNet();
        engine = e;
        screen = GAME;
        netMode = false;
        isHost = false;
        mySeat = 0;
        sortAsc = meta[1] == 1;
        int diff = meta[0];
        accent = diff == 0 ? E_WOOD : (diff == 1 ? E_EARTH : E_WATER);
        names = diff == 0
                ? new String[]{"震·雷动", "坤·厚德", "兑·泽悦"}
                : (diff == 1 ? new String[]{"离·火明", "艮·山止", "坎·水渊"}
                : new String[]{"巽·风行", "乾·天行", "坎·水渊"});
        selected.clear();
        sortHand();
        voiceSig = "";
        invalidate();
        scheduleAi();
        showToast("已回到第 " + engine.roundNo + " 局");
    }

    private void saveVoice() {
        try { getContext().getSharedPreferences("guandan", 0).edit().putBoolean("voice", voiceOn).commit(); } catch (Throwable e) { }
    }

    public boolean handleBack() {
        if (screen == GAME) {
            handler.removeCallbacks(aiRunnable);
            persist();
            screen = MENU;
            selected.clear();
            invalidate();
            return true;
        }
        return false;
    }

    // ===================== 绘制入口 =====================

    @Override
    protected void onDraw(Canvas cv) {
        drawBg(cv);
        if (screen == MENU) drawMenu(cv);
        else drawGame(cv);
    }

    private void drawBg(Canvas cv) {
        LinearGradient lg = new LinearGradient(0, 0, getWidth() * 0.35f, getHeight(),
                new int[]{C_BG, C_BG2, 0xFF0B0D11}, null, Shader.TileMode.CLAMP);
        pFill.setShader(lg);
        cv.drawRect(0, 0, getWidth(), getHeight(), pFill);
        pFill.setShader(null);

        pStroke.setStyle(Paint.Style.FILL);
        pStroke.setColor(0x14FFFFFF);
        drawCornerTrigrams(cv);
    }

    private void drawCornerTrigrams(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float bw = dp(15), bh = dp(3.5f), gap = dp(4);
        drawTrigram(cv, dp(14), dp(14), bw, bh, gap, new boolean[]{true, false, false});
        drawTrigram(cv, w - dp(14) - bw, dp(14), bw, bh, gap, new boolean[]{false, true, true});
        drawTrigram(cv, dp(14), h - dp(34), bw, bh, gap, new boolean[]{true, true, false});
        drawTrigram(cv, w - dp(14) - bw, h - dp(34), bw, bh, gap, new boolean[]{false, false, true});
    }

    private void drawTrigram(Canvas cv, float x, float y, float bw, float bh, float gap, boolean[] solid) {
        for (int i = 0; i < 3; i++) {
            float top = y + i * (bh + gap);
            if (solid[i]) {
                cv.drawRect(x, top, x + bw, top + bh, pStroke);
            } else {
                float half = bw / 2 - dp(2.5f);
                cv.drawRect(x, top, x + half, top + bh, pStroke);
                cv.drawRect(x + bw - half, top, x + bw, top + bh, pStroke);
            }
        }
    }

    // ===================== 首页 =====================

    private void drawMenu(Canvas cv) {
        float w = getWidth(), h = getHeight();

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(dp(1));
        pStroke.setColor(0x33C8A96A);
        cv.drawLine(dp(34), dp(52), w - dp(34), dp(52), pStroke);

        pText.setTextAlign(Paint.Align.CENTER);
        pText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        pText.setColor(E_EARTH);
        pText.setTextSize(dp(30));
        pText.setLetterSpacing(0.30f);
        cv.drawText("掼蛋", w / 2 + dp(8), h * 0.28f, pText);

        pText.setLetterSpacing(0.20f);
        pText.setColor(C_SUB);
        pText.setTextSize(dp(9));
        cv.drawText("G U A N D A N   ·   S O L O", w / 2 + dp(4), h * 0.28f + dp(18), pText);

        String[] namesT = {"初 级", "高 级", "终 极"};
        String[] elements = {"木 · 生发", "土 · 承载", "水 · 通玄"};
        String[] desc = {"雷动泽悦，节奏明快", "山止火明，控场配合", "风行天行，记牌推演"};
        int[] colors = {E_WOOD, E_EARTH, E_WATER};

        float top = h * 0.40f;
        float bh = h * 0.36f;
        float gap = dp(16);
        float bw = (w - dp(56) - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            RectF r = new RectF(dp(28) + i * (bw + gap), top, dp(28) + i * (bw + gap) + bw, top + bh);
            btnMenu[i] = r;
            pFill.setStyle(Paint.Style.FILL);
            pFill.setColor(C_PANEL);
            cv.drawRoundRect(r, dp(6), dp(6), pFill);
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setStrokeWidth(dp(1));
            pStroke.setColor(colors[i]);
            pStroke.setAlpha(120);
            cv.drawRoundRect(r, dp(6), dp(6), pStroke);

            pText.setTextAlign(Paint.Align.CENTER);
            pText.setColor(colors[i]);
            pText.setTextSize(dp(16));
            pText.setLetterSpacing(0.10f);
            cv.drawText(namesT[i], r.centerX(), r.top + dp(32), pText);

            pText.setLetterSpacing(0f);
            pText.setColor(0xAAFFFFFF);
            pText.setTextSize(dp(10));
            cv.drawText(elements[i], r.centerX(), r.top + dp(50), pText);

            pText.setColor(C_SUB);
            pText.setTextSize(dp(9.5f));
            cv.drawText(desc[i], r.centerX(), r.bottom - dp(14), pText);
        }

        float ly = top + bh + dp(12);
        float lgap = dp(12);
        float lw2 = (w - dp(56) - lgap * 2) / 3;
        btnLanHost.set(dp(28), ly, dp(28) + lw2, ly + dp(34));
        btnLanScan.set(dp(28) + lw2 + lgap, ly, dp(28) + lw2 * 2 + lgap, ly + dp(34));
        btnLanJoin.set(w - dp(28) - lw2, ly, w - dp(28), ly + dp(34));
        drawBtn(cv, btnLanHost, "创建房间", false, true);
        drawBtn(cv, btnLanScan, "搜索房间", false, true);
        drawBtn(cv, btnLanJoin, "手动加入", false, true);

        if (Save.has(getContext())) {
            int[] info = Save.quickInfo(getContext());
            btnResume.set(w / 2 - dp(120), ly + dp(42), w / 2 + dp(120), ly + dp(72));
            pText.setTextAlign(Paint.Align.CENTER);
            pText.setColor(E_EARTH);
            pText.setTextSize(dp(10.5f));
            cv.drawText("继续上一局 · 第 " + info[0] + " 局 打 " + Engine.levelText(info[1]),
                    btnResume.centerX(), btnResume.centerY() + dp(4), pText);
        } else {
            btnResume.setEmpty();
        }

        String[] thinkNames = {"快", "标准", "深度"};
        float footY = h - dp(14);
        btnThinkSet.set(dp(16), footY - dp(14), dp(150), footY + dp(4));
        pText.setTextAlign(Paint.Align.LEFT);
        pText.setColor(0xCC9FB8C8);
        pText.setTextSize(dp(9.5f));
        cv.drawText("AI 思考 " + thinkNames[Math.max(0, Math.min(2, thinkLevels[2]))]
                + "/" + thinkNames[Math.max(0, Math.min(2, thinkLevels[1]))]
                + "/" + thinkNames[Math.max(0, Math.min(2, thinkLevels[0]))] + "  ▸",
                dp(20), footY, pText);

        pText.setTextAlign(Paint.Align.RIGHT);
        pText.setColor(0x55FFFFFF);
        cv.drawText(netInfo.isEmpty() ? "局域网互联 · 48620" : netInfo, w - dp(20), footY, pText);
    }

    // ===================== 牌桌 =====================

    private void drawGame(Canvas cv) {
        float w = getWidth(), h = getHeight();

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(dp(1));
        pStroke.setColor(0x22FFFFFF);
        cv.drawLine(0, dp(40), w, dp(40), pStroke);

        pText.setTextAlign(Paint.Align.LEFT);
        pText.setColor(accent);
        pText.setTextSize(dp(13));
        pText.setLetterSpacing(0.12f);
        cv.drawText("掼 蛋", dp(16), dp(26), pText);
        pText.setLetterSpacing(0f);

        pText.setColor(C_TEXT);
        pText.setTextSize(dp(11));
        cv.drawText("第 " + engine.roundNo + " 局 · 打 " + Engine.levelText(engine.level), dp(70), dp(26), pText);

        pText.setColor(C_SUB);
        pText.setTextSize(dp(10));
        cv.drawText("我方 " + Engine.levelText(engine.teamLevel[0]) + "   对方 " + Engine.levelText(engine.teamLevel[1]),
                dp(186), dp(26), pText);

        btnExit.set(w - dp(58), dp(8), w - dp(14), dp(32));
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setColor(C_SUB);
        pText.setTextSize(dp(10));
        cv.drawText("退出", btnExit.centerX(), btnExit.centerY() + dp(3.5f), pText);

        btnVoice.set(w - dp(120), dp(8), w - dp(64), dp(32));
        pText.setColor(voiceOn ? accent : C_SUB);
        pText.setTextSize(dp(10));
        cv.drawText(voiceOn ? "声音 开" : "声音 关", btnVoice.centerX(), btnVoice.centerY() + dp(3.5f), pText);

        drawBaguaRing(cv, w / 2, h * 0.47f, Math.min(w, h) * 0.165f);

        drawSeat(cv, 2, w / 2 - dp(82), dp(46), dp(164), dp(48));
        drawSeat(cv, 3, dp(14), dp(100), dp(124), dp(50));
        drawSeat(cv, 1, w - dp(138), dp(100), dp(124), dp(50));

        Play last = engine.lastPlay;
        float cy = h * 0.47f;
        if (last != null) {
            drawPlayCards(cv, last.cards, w / 2, cy, 0.50f);
            pText.setTextAlign(Paint.Align.CENTER);
            pText.setColor(0x77FFFFFF);
            pText.setTextSize(dp(9));
            cv.drawText(last.typeName() + " · " + Engine.seatName(engine.lastPlayer), w / 2, cy - dp(32), pText);
        } else if (!engine.roundOver) {
            pText.setTextAlign(Paint.Align.CENTER);
            pText.setColor(0x44FFFFFF);
            pText.setTextSize(dp(10.5f));
            String wait = engine.turn == mySeat ? "你的出牌权" : "等待出牌…";
            if (engine.turn != mySeat && turnDeadline > 0) {
                int left = (int) Math.max(0, (turnDeadline - System.currentTimeMillis() + 999) / 1000);
                wait = seatLabel(engine.turn) + "  剩 " + left + "s";
            }
            cv.drawText(wait, w / 2, cy + dp(2), pText);
        }

        drawHand(cv);
        drawButtons(cv);
        drawLogs(cv);

        if (!toast.isEmpty()) {
            pText.setTextAlign(Paint.Align.CENTER);
            pText.setColor(E_EARTH);
            pText.setTextSize(dp(11.5f));
            cv.drawText(toast, w / 2, handTop - dp(56), pText);
        }

        if (engine.roundOver) drawResult(cv);
    }

    private void drawBaguaRing(Canvas cv, float cx, float cy, float r) {
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(dp(1));
        pStroke.setColor(0x1EFFFFFF);
        cv.drawCircle(cx, cy, r, pStroke);
        pStroke.setColor(0x10FFFFFF);
        cv.drawCircle(cx, cy, r * 0.62f, pStroke);

        float bw = dp(12), bh = dp(2.2f), gap = dp(3);
        boolean[][] pats = {
                {true, true, true}, {false, true, true}, {true, false, true}, {false, false, true},
                {true, true, false}, {false, true, false}, {true, false, false}, {false, false, false}
        };
        for (int i = 0; i < 8; i++) {
            double ang = Math.toRadians(i * 45 - 90);
            float x = cx + (float) Math.cos(ang) * (r + dp(10)) - bw / 2;
            float y = cy + (float) Math.sin(ang) * (r + dp(10)) - dp(5);
            pStroke.setColor(0x14FFFFFF);
            drawTrigram(cv, x, y, bw, bh, gap, pats[i]);
        }
    }

    private void drawSeat(Canvas cv, int seat, float x, float y, float ww, float hh) {
        RectF r = new RectF(x, y, x + ww, y + hh);
        boolean isTurn = engine.turn == seat && !engine.roundOver;
        boolean mate = seat == 2;
        int color = mate ? E_EARTH : (seat == 3 ? E_WOOD : E_METAL);

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(isTurn ? 0xFF1B242C : C_PANEL);
        cv.drawRoundRect(r, dp(4), dp(4), pFill);
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(dp(1));
        pStroke.setColor(isTurn ? accent : 0x22FFFFFF);
        cv.drawRoundRect(r, dp(4), dp(4), pStroke);

        String nm = seat == 2 ? names[1] : (seat == 3 ? names[0] : names[2]);

        pText.setTextAlign(Paint.Align.LEFT);
        pText.setColor(color);
        pText.setTextSize(dp(10.5f));
        cv.drawText(nm, r.left + dp(8), r.top + dp(15), pText);

        pText.setColor(C_SUB);
        pText.setTextSize(dp(8.5f));
        cv.drawText(mate ? "坤位 · 队友" : (seat == 3 ? "震位 · 左家" : "兑位 · 右家"),
                r.left + dp(8), r.top + dp(27), pText);

        pText.setColor(C_TEXT);
        pText.setTextSize(dp(11));
        String cnt = engine.isFinished(seat) ? "已出完" : (engine.hands[seat].size() + " 张");
        cv.drawText(cnt, r.left + dp(8), r.top + dp(41), pText);

        String tag = engine.seatTag[seat];
        if (tag == null) tag = engine.seatPassed[seat] ? "不要" : "—";
        pText.setTextAlign(Paint.Align.RIGHT);
        pText.setColor(isTurn && aiThinking ? accent : 0x88FFFFFF);
        pText.setTextSize(dp(9));
        cv.drawText(isTurn && aiThinking ? "思考中…" : tag, r.right - dp(8), r.top + dp(41), pText);
    }

    private void drawPlayCards(Canvas cv, List<Card> cards, float cx, float cy, float scale) {
        float cw = cardW * scale, chh = cardH * scale;
        int n = cards.size();
        float step = cw * 0.7f;
        if (n > 1) step = Math.min(step, (cw * 2.6f) / (n - 1));
        float total = step * (n - 1) + cw;
        float x0 = cx - total / 2;
        float y0 = cy - chh / 2;
        for (int i = 0; i < n; i++) {
            RectF r = new RectF(x0 + i * step, y0, x0 + i * step + cw, y0 + chh);
            drawCard(cv, r, cards.get(i), false, scale);
        }
    }

    private void drawCard(Canvas cv, RectF r, Card c, boolean sel, float scale) {
        float rad = dp(5) * Math.max(0.6f, scale);
        pCardBg.setColor(C_CARD);
        cv.drawRoundRect(r, rad, rad, pCardBg);

        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(dp(1));
        pStroke.setColor(c.wild ? E_EARTH : 0x22000000);
        if (sel) {
            pStroke.setStrokeWidth(dp(2));
            pStroke.setColor(E_EARTH);
        }
        cv.drawRoundRect(r, rad, rad, pStroke);

        float ts = cardH * 0.235f * scale;
        pText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        if (c.isJoker()) {
            pText.setTextAlign(Paint.Align.LEFT);
            pText.setColor(c.rank == 16 ? E_EARTH : C_BLACK);
            pText.setTextSize(ts * 0.95f);
            cv.drawText(c.rank == 15 ? "小" : "大", r.left + dp(4) * scale, r.top + ts * 1.05f, pText);
            cv.drawText("王", r.left + dp(4) * scale, r.top + ts * 1.98f, pText);
        } else {
            pText.setTextAlign(Paint.Align.LEFT);
            pText.setColor(c.isRed() ? C_RED : C_BLACK);
            pText.setTextSize(ts);
            cv.drawText(c.rankText(), r.left + dp(4) * scale, r.top + ts * 1.0f, pText);
            pText.setTextSize(ts * 0.82f);
            cv.drawText(c.suitText(), r.left + dp(4) * scale, r.top + ts * 1.88f, pText);
        }

        if (c.wild) {
            pText.setTextAlign(Paint.Align.RIGHT);
            pText.setColor(E_EARTH);
            pText.setTextSize(dp(8) * Math.max(0.7f, scale));
            cv.drawText("配", r.right - dp(3), r.bottom - dp(3), pText);
        }
        pText.setTextAlign(Paint.Align.LEFT);
    }

    private void drawHand(Canvas cv) {
        myHand.clear();
        myHand.addAll(engine.hands[mySeat]);
        int n = myHand.size();
        float w = getWidth(), h = getHeight();

        handBottom = h - dp(8);
        handTop = handBottom - cardH;
        float x0 = dp(10);
        float avail = w - dp(20);
        cardStep = n > 1 ? Math.min(cardW * 0.50f, (avail - cardW) / (n - 1)) : 0;
        if (n > 1 && cardStep * (n - 1) + cardW > avail) cardStep = (avail - cardW) / (n - 1);
        cardX = new float[n];
        for (int i = 0; i < n; i++) cardX[i] = x0 + i * cardStep;

        for (int i = 0; i < n; i++) {
            boolean sel = selected.contains(myHand.get(i));
            float top = sel ? handTop - dp(18) : handTop;
            RectF r = new RectF(cardX[i], top, cardX[i] + cardW, top + cardH);
            drawCard(cv, r, myHand.get(i), sel, 1f);
        }

        pText.setTextAlign(Paint.Align.LEFT);
        pText.setColor(C_SUB);
        pText.setTextSize(dp(10));
        cv.drawText(seatLabel(mySeat) + " · " + n + " 张", dp(12), handTop - dp(6), pText);
    }

    private void drawButtons(Canvas cv) {
        float w = getWidth();
        float bh = dp(36);
        float gap = dp(8);
        float bw2 = dp(72);
        float y0 = handTop - dp(46);
        float right = w - dp(14);

        btnSort.set(right - bw2, y0, right, y0 + bh);
        btnHint.set(btnSort.left - gap - bw2, y0, btnSort.left - gap, y0 + bh);
        btnPass.set(btnHint.left - gap - bw2, y0, btnHint.left - gap, y0 + bh);
        btnPlay.set(btnPass.left - gap - dp(84), y0, btnPass.left - gap, y0 + bh);

        boolean myTurn = engine.turn == mySeat && !engine.roundOver;
        drawBtn(cv, btnPlay, "出 牌", true, myTurn);
        drawBtn(cv, btnPass, "不 出", false, myTurn && engine.lastPlay != null);
        drawBtn(cv, btnHint, "提 示", false, myTurn);
        drawBtn(cv, btnSort, sortAsc ? "升 序" : "降 序", false, true);
    }

    private void drawBtn(Canvas cv, RectF r, String text, boolean primary, boolean enabled) {
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(primary ? (enabled ? accent : 0x33FFFFFF) : C_PANEL);
        cv.drawRoundRect(r, dp(4), dp(4), pFill);
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(dp(1));
        pStroke.setColor(primary ? accent : 0x33FFFFFF);
        cv.drawRoundRect(r, dp(4), dp(4), pStroke);

        pText.setTextAlign(Paint.Align.CENTER);
        pText.setTextSize(dp(11.5f));
        pText.setColor(primary ? (enabled ? 0xFF101315 : 0x66FFFFFF) : (enabled ? C_TEXT : 0x55FFFFFF));
        cv.drawText(text, r.centerX(), r.centerY() + dp(4f), pText);
    }

    private void drawLogs(Canvas cv) {
        float x = dp(14);
        float y = handTop - dp(58);
        pText.setTextAlign(Paint.Align.LEFT);
        pText.setTextSize(dp(9.5f));
        for (int i = 0; i < Math.min(2, engine.log.size()); i++) {
            pText.setColor(i == 0 ? 0x99FFFFFF : 0x55FFFFFF);
            cv.drawText(engine.log.get(i), x, y + i * dp(14), pText);
        }
        if (!netInfo.isEmpty()) {
            pText.setColor(0x55FFFFFF);
            pText.setTextSize(dp(9));
            cv.drawText(netInfo, x, y + 2 * dp(14), pText);
            pText.setTextSize(dp(9.5f));
        }
    }

    private void drawResult(Canvas cv) {
        float w = getWidth(), h = getHeight();
        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(0xC0080A0D);
        cv.drawRect(0, 0, w, h, pFill);

        float pw = Math.min(w * 0.60f, dp(460));
        RectF panel = new RectF(w / 2 - pw / 2, h * 0.20f, w / 2 + pw / 2, h * 0.80f);
        pFill.setColor(C_PANEL);
        cv.drawRoundRect(panel, dp(8), dp(8), pFill);
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(dp(1));
        pStroke.setColor(accent);
        pStroke.setAlpha(140);
        cv.drawRoundRect(panel, dp(8), dp(8), pStroke);

        pText.setTextAlign(Paint.Align.CENTER);
        pText.setColor(accent);
        pText.setTextSize(dp(13));
        pText.setLetterSpacing(0.20f);
        cv.drawText("本 局 结 束", panel.centerX(), panel.top + dp(30), pText);
        pText.setLetterSpacing(0f);

        pText.setColor(C_TEXT);
        pText.setTextSize(dp(11));
        wrapText(cv, engine.resultText, panel.centerX(), panel.top + dp(54), panel.width() - dp(44), dp(17));

        btnContinue.set(panel.centerX() - dp(80), panel.bottom - dp(48), panel.centerX() + dp(80), panel.bottom - dp(14));
        pFill.setColor(accent);
        cv.drawRoundRect(btnContinue, dp(4), dp(4), pFill);
        pText.setColor(0xFF101315);
        pText.setTextSize(dp(11.5f));
        cv.drawText((matchOver || engine.resultText.startsWith("打 A 成功")) ? "返回首页" : "继续下一局",
                btnContinue.centerX(), btnContinue.centerY() + dp(4f), pText);
    }

    private void wrapText(Canvas cv, String s, float cx, float y, float maxW, float lh) {
        if (s == null) return;
        List<String> lines = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            cur.append(s.charAt(i));
            if (pText.measureText(cur.toString()) > maxW) {
                cur.deleteCharAt(cur.length() - 1);
                if (cur.length() > 0) lines.add(cur.toString());
                cur = new StringBuilder();
                cur.append(s.charAt(i));
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        for (int i = 0; i < lines.size(); i++) cv.drawText(lines.get(i), cx, y + i * lh, pText);
    }

    // ===================== 交互 =====================

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            float x = e.getX(), y = e.getY();
            if (screen == MENU) {
                for (int i = 0; i < 3; i++) {
                    if (btnMenu[i] != null && btnMenu[i].contains(x, y)) {
                        startGame(i);
                        return true;
                    }
                }
                if (btnLanHost.contains(x, y)) { startHostRoom(); return true; }
                if (btnThinkSet.contains(x, y)) { askThinkSpeed(); return true; }
                if (!btnResume.isEmpty() && btnResume.contains(x, y)) { resumeGame(); return true; }
                if (btnLanScan.contains(x, y)) { startScan(); return true; }
                if (btnLanJoin.contains(x, y)) { askIp(); return true; }
                return true;
            }
            if (engine.roundOver) {
                if (btnContinue.contains(x, y)) continueGame();
                return true;
            }
            if (btnVoice.contains(x, y)) {
                voiceOn = !voiceOn;
                if (voice != null) voice.setEnabled(voiceOn);
                saveVoice();
                if (voiceOn && voice != null && (!voice.isReady() || !voice.hasChinese())) {
                    askVoiceEngine();
                } else {
                    showToast(voiceOn ? "语音播报已开启" : "语音播报已关闭");
                }
                invalidate();
                return true;
            }
            if (btnExit.contains(x, y)) {
                handler.removeCallbacks(aiRunnable);
                screen = MENU;
                invalidate();
                return true;
            }
            if (btnPlay.contains(x, y)) { humanPlay(); return true; }
            if (btnPass.contains(x, y)) { humanPass(); return true; }
            if (btnHint.contains(x, y)) { hint(); return true; }
            if (btnSort.contains(x, y)) {
                sortAsc = !sortAsc;
                sortHand();
                invalidate();
                return true;
            }
            if (engine.turn == mySeat) pickCard(x, y);
            return true;
        }
        return true;
    }

    private void pickCard(float x, float y) {
        int n = myHand.size();
        if (n == 0) return;
        for (int i = n - 1; i >= 0; i--) {
            float left = cardX[i];
            float right = (i == n - 1) ? left + cardW : cardX[i + 1] + dp(1);
            float top = handTop - dp(24);
            if (x >= left && x <= right && y >= top && y <= handBottom) {
                Card c = myHand.get(i);
                if (selected.contains(c)) selected.remove(c);
                else selected.add(c);
                invalidate();
                return;
            }
        }
    }

    // ===================== 流程 =====================

    private void startGame(int diff) {
        Save.clear(getContext());
        stopNet();
        handler.removeCallbacks(aiRunnable);
        screen = GAME;
        netMode = false;
        isHost = false;
        mySeat = 0;
        netInfo = "";
        selected.clear();
        matchOver = false;
        engine = new Engine();
        engine.thinkLevels = thinkLevels;
        engine.newMatch(diff);
        accent = diff == 0 ? E_WOOD : (diff == 1 ? E_EARTH : E_WATER);
        names = diff == 0
                ? new String[]{"震·雷动", "坤·厚德", "兑·泽悦"}
                : (diff == 1 ? new String[]{"离·火明", "艮·山止", "坎·水渊"}
                : new String[]{"巽·风行", "乾·天行", "坎·水渊"});
        sortHand();
        promptTurn();
        invalidate();
        scheduleAi();
    }

    private void continueGame() {
        if (matchOver || engine.resultText.startsWith("打 A 成功")) {
            screen = MENU;
            matchOver = false;
            invalidate();
            return;
        }
        engine.deal();
        selected.clear();
        sortHand();
        invalidate();
        afterChange();
    }

    private void humanPlay() {
        if (selected.isEmpty()) { showToast("请先选择要出的牌"); return; }
        List<Card> cards = new ArrayList<Card>(selected);
        String err = engine.playCards(mySeat, cards);
        if (err != null) { showToast(err); return; }
        List<Object> ids = new ArrayList<Object>();
        for (Card c : cards) ids.add(c.id);
        selected.clear();
        if (netMode && !isHost && netClient != null) {
            netClient.send(Json.obj("t", "play", "cards", ids));
        }
        invalidate();
        afterChange();
    }

    private void humanPass() {
        if (engine.lastPlay == null) { showToast("你是首家，必须出牌"); return; }
        selected.clear();
        engine.doPass(mySeat);
        speakPass(mySeat);
        if (netMode && !isHost && netClient != null) netClient.send(Json.obj("t", "pass"));
        invalidate();
        afterChange();
    }

    private void hint() {
        if (engine.turn != mySeat) return;
        List<Play> cands = AI.candidates(engine.hands[mySeat], engine.level);
        Play best = null;
        for (Play p : cands) {
            if (!Rules.canBeat(engine.lastPlay, p)) continue;
            if (p.power > 0) continue;
            if (best == null || p.key < best.key) best = p;
        }
        if (best == null) {
            for (Play p : cands) {
                if (!Rules.canBeat(engine.lastPlay, p)) continue;
                if (best == null || p.power < best.power) best = p;
            }
        }
        if (best == null) { showToast("没有能压过的牌，只能不出"); return; }
        selected.clear();
        selected.addAll(best.cards);
        invalidate();
    }

    private void sortHand() {
        final int level = engine.level;
        Collections.sort(engine.hands[mySeat], new Comparator<Card>() {
            public int compare(Card a, Card b) {
                int wa = Rules.weight(a.rank, level);
                int wb = Rules.weight(b.rank, level);
                int r = wa > wb ? -1 : (wa < wb ? 1 : 0);
                return sortAsc ? -r : r;
            }
        });
    }

    private void showToast(String s) {
        toast = s;
        invalidate();
        handler.removeCallbacks(clearToast);
        handler.postDelayed(clearToast, 1800);
    }

    private final Runnable clearToast = new Runnable() {
        public void run() { toast = ""; invalidate(); }
    };

    private void bindThink() {
        if (engine == null) return;
        engine.thinkLevels = thinkLevels;
    }

    private void afterChange() {
        if (netMode && isHost) broadcastState();
        speakLastPlay();
        promptTurn();
        persist();
        armTurnTimer();
        scheduleAi();
    }

    private void speakLastPlay() {
        if (!voiceOn || voice == null) return;
        Play p = engine.lastPlay;
        if (p == null) { voiceSig = ""; return; }
        StringBuilder sb = new StringBuilder();
        sb.append(engine.lastPlayer).append('#').append(p.type).append('#')
          .append(p.key).append('#').append(p.size).append('#');
        for (Card c : p.cards) sb.append(c.id).append(',');
        String sig = sb.toString();
        if (sig.equals(voiceSig)) return;
        voiceSig = sig;
        String who = engine.lastPlayer == mySeat ? "你" : Engine.seatName(engine.lastPlayer);
        voice.speak(who + "，出" + playText(p));
    }

    private void speakPass(int seat) {
        if (!voiceOn || voice == null) return;
        voice.speak((seat == mySeat ? "你" : Engine.seatName(seat)) + "，不要");
    }

    private void promptTurn() {
        if (!voiceOn || voice == null || engine.roundOver) return;
        if (engine.turn != mySeat) return;
        String top = engine.log.isEmpty() ? "" : engine.log.get(0);
        String sig = engine.roundNo + "/" + engine.turn + "/" + top + "/"
                + (engine.lastPlay == null ? "-" : (engine.lastPlay.type + "" + engine.lastPlay.key));
        if (sig.equals(turnPromptSig)) return;
        turnPromptSig = sig;
        voice.beep();
        voice.speak("轮到你出牌");
    }

    private String playText(Play p) {
        String r = p.cards.isEmpty() ? "" : rankText(p.cards.get(0).rank);
        switch (p.type) {
            case 1: return "单张 " + r;
            case 2: return "一对 " + r;
            case 3: return "三张 " + r;
            case 4: return "三带二，三张 " + r;
            case 5: return "顺子";
            case 6: return "三连对";
            case 7: return "钢板";
            case 8: return "同花顺";
            case 10: return "天王炸";
            default: return p.size + " 张炸弹";
        }
    }

    private String rankText(int rank) {
        if (rank == 11) return "J";
        if (rank == 12) return "Q";
        if (rank == 13) return "K";
        if (rank == 14) return "A";
        if (rank == 15) return "小王";
        if (rank == 16) return "大王";
        return String.valueOf(rank);
    }

    private void armTurnTimer() {
        handler.removeCallbacks(turnTimeout);
        handler.removeCallbacks(turnTicker);
        turnDeadline = 0;
        if (!netMode || !isHost) return;
        if (engine.roundOver) return;
        int seat = engine.turn;
        if (seat == mySeat || engine.isAi[seat]) return;
        turnDeadline = System.currentTimeMillis() + TURN_MS;
        handler.postDelayed(turnTimeout, TURN_MS);
        handler.post(turnTicker);
    }

    private final Runnable turnTicker = new Runnable() {
        public void run() {
            if (turnDeadline == 0) return;
            invalidate();
            if (System.currentTimeMillis() < turnDeadline) handler.postDelayed(this, 1000);
        }
    };

    private final Runnable turnTimeout = new Runnable() {
        public void run() {
            if (engine.roundOver || turnDeadline == 0) return;
            int seat = engine.turn;
            if (seat == mySeat || engine.isAi[seat]) { turnDeadline = 0; return; }
            turnDeadline = 0;
            if (engine.lastPlay == null) {
                List<Card> h = engine.hands[seat];
                if (!h.isEmpty()) {
                    Card low = h.get(0);
                    for (Card c : h) {
                        if (Rules.weight(c.rank, engine.level) < Rules.weight(low.rank, engine.level)) low = c;
                    }
                    List<Card> one = new ArrayList<Card>();
                    one.add(low);
                    engine.playCards(seat, one);
                    engine.addLog(Engine.seatName(seat) + " 超时，系统代出");
                }
            } else {
                engine.doPass(seat);
                engine.addLog(Engine.seatName(seat) + " 超时，系统代打");
            }
            broadcastState();
            if (engine.lastPlay == null) speakLastPlay();
            persist();
            invalidate();
            armTurnTimer();
            scheduleAi();
        }
    };

    private void scheduleReconnect() {
        if (lastSeat <= 0) return;
        retryLeft = 6;
        rejoining = true;
        handler.removeCallbacks(reconnectRunnable);
        handler.postDelayed(reconnectRunnable, 2500);
    }

    private final Runnable reconnectRunnable = new Runnable() {
        public void run() {
            if (!netMode || isHost) return;
            if (retryLeft <= 0) {
                rejoining = false;
                netInfo = "重连失败，请重新加入";
                invalidate();
                return;
            }
            retryLeft--;
            netInfo = "重连中…（剩 " + retryLeft + " 次）";
            invalidate();
            try {
                netClient = new Net.Client(clientCB);
                netClient.connect(lastIp, Net.PORT, lastRoom, lastSeat);
            } catch (Exception e) {
                handler.postDelayed(this, 3000);
            }
        }
    };

    private void scheduleAi() {
        handler.removeCallbacks(aiRunnable);
        if (engine.roundOver) return;
        if (engine.turn == mySeat) return;
        if (netMode && !isHost) return;
        if (!engine.isAi[engine.turn]) return;
        aiThinking = true;
        invalidate();
        int delay = thinkDelay();
        handler.postDelayed(aiRunnable, delay);
    }

    private final Runnable aiRunnable = new Runnable() {
        public void run() {
            aiThinking = false;
            if (engine.roundOver || engine.turn == mySeat) { invalidate(); return; }
            Play before = engine.lastPlay;
            int seatBefore = engine.turn;
            engine.aiStep();
            if (netMode && isHost) broadcastState();
            if (engine.lastPlay == before) speakPass(seatBefore);
            else speakLastPlay();
            persist();
            invalidate();
            if (!engine.roundOver && engine.turn != mySeat && (!netMode || isHost) && engine.isAi[engine.turn]) {
                aiThinking = true;
                int delay = thinkDelay();
                handler.postDelayed(this, delay);
            }
        }
    };
    // ===================== 联机 =====================

    private String seatLabel(int seat) {
        switch (seat) {
            case 0: return "乾位 · 你";
            case 1: return "兑位 · 右家";
            case 2: return "坤位 · 队友";
            default: return "震位 · 左家";
        }
    }

    private void stopNet() {
        handler.removeCallbacks(reconnectRunnable);
        handler.removeCallbacks(turnTimeout);
        handler.removeCallbacks(turnTicker);
        turnDeadline = 0;
        rejoining = false;
        retryLeft = 0;
        lastSeat = 0;
        lastRoom = "";
        roomId = "";
        if (netHost != null) { netHost.stop(); netHost = null; }
        if (netClient != null) { netClient.close(); netClient = null; }
        Net.Discovery.stop();
        clientSeats.clear();
        netMode = false;
        isHost = false;
    }

    private String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception e) { }
        return "127.0.0.1";
    }

    private void broadcastState() {
        if (netHost == null) return;
        for (int i = 0; i < clientSeats.size(); i++) {
            int seat = clientSeats.get(i);
            netHost.sendTo(seat, Net.stateFor(engine, seat, engine.isAi));
        }
    }

    private void startHostRoom() {
        Save.clear(getContext());
        stopNet();
        handler.removeCallbacks(aiRunnable);
        engine = new Engine();
        engine.thinkLevels = thinkLevels;
        engine.newMatch(Engine.DIFF_ADVANCED);
        mySeat = 0;
        netMode = true;
        isHost = true;
        for (int i = 0; i < 4; i++) engine.isAi[i] = (i != 0);
        accent = E_EARTH;
        names = new String[]{"震·雷动", "坤·厚德", "兑·泽悦"};
        try {
            netHost = new Net.Host(hostCB);
            netHost.start();
            roomId = netHost.roomId();
            try { Net.Discovery.startAnnounce("掼蛋房间", roomId); } catch (Exception e) { }
            netInfo = "房间已建 · IP " + localIp() + " · 房号 " + roomId;
            screen = GAME;
            selected.clear();
            matchOver = false;
            sortHand();
            invalidate();
            scheduleAi();
        } catch (Exception e) {
            netInfo = "开房失败：" + e.getMessage();
            screen = MENU;
            invalidate();
        }
    }

    private void askIp() {
        Activity act = (Activity) getContext();
        final EditText et = new EditText(act);
        et.setHint("192.168.1.8");
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        try {
            new AlertDialog.Builder(act)
                    .setTitle("局域网加入房间")
                    .setMessage("输入房主那台设备的局域网 IP")
                    .setView(et)
                    .setPositiveButton("连接", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            joinRoom(et.getText().toString().trim());
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            netInfo = "无法打开输入框：" + e.getMessage();
            invalidate();
        }
    }

    private void askVoiceEngine() {
        try {
            Activity act = (Activity) getContext();
            new AlertDialog.Builder(act)
                    .setTitle("语音引擎")
                    .setMessage("系统暂未就绪中文语音引擎。到系统语音设置安装/启用中文语音包（如讯飞语音、Google 文字转语音）后返回本应用即可播报。")
                    .setPositiveButton("去设置", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int which) { openTtsSettings(); }
                    })
                    .setNegativeButton("稍后", null)
                    .show();
        } catch (Exception e) { }
    }

    private void openTtsSettings() {
        try {
            Activity act = (Activity) getContext();
            android.content.Intent i = new android.content.Intent("com.android.settings.TTS_SETTINGS");
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Throwable e) {
            try {
                Activity act = (Activity) getContext();
                android.content.Intent i2 = new android.content.Intent("com.android.settings.TTS_SETTINGS");
                i2.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                i2.setClassName("com.android.settings", "com.android.settings.TtsSettingsActivity");
                act.startActivity(i2);
            } catch (Throwable e2) {
                showToast("请到系统设置 → 更多设置 → 语音输出 中启用中文语音");
            }
        }
    }

    private void askThinkSpeed() {
        try {
            Activity act = (Activity) getContext();
            android.widget.LinearLayout root = new android.widget.LinearLayout(act);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = (int) dp(18);
            root.setPadding(pad, pad, pad, pad);
            String[] diffNames = {"初级 · 木", "高级 · 土", "终极 · 水"};
            String[] lvNames = {"快", "标准", "深度"};
            for (int d = 0; d < 3; d++) {
                android.widget.TextView tv = new android.widget.TextView(act);
                tv.setText(diffNames[d] + "  AI 思考");
                tv.setTextSize(14);
                tv.setTextColor(0xFFC8A96A);
                root.addView(tv);
                android.widget.RadioGroup rg = new android.widget.RadioGroup(act);
                rg.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                for (int lv = 0; lv < 3; lv++) {
                    android.widget.RadioButton rb = new android.widget.RadioButton(act);
                    rb.setText(lvNames[lv]);
                    rb.setId(android.view.View.generateViewId());
                    rb.setTextSize(13);
                    rb.setChecked(thinkLevels[d] == lv);
                    final int dd = d, ll = lv;
                    rb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                            if (!checked) return;
                            thinkLevels[dd] = ll;
                            try {
                                getContext().getSharedPreferences("guandan", 0).edit()
                                        .putInt("think_" + dd, ll).commit();
                            } catch (Throwable e) { }
                            if (engine != null) engine.thinkLevels = thinkLevels;
                            invalidate();
                        }
                    });
                    rg.addView(rb);
                }
                root.addView(rg);
            }
            new AlertDialog.Builder(act)
                    .setTitle("AI 思考强度（按难度分别设置）")
                    .setView(root)
                    .setPositiveButton("完成", null)
                    .show();
        } catch (Exception e) { }
    }

    private int thinkDelay() {
        int tl = 1;
        try { tl = thinkLevels[Math.max(0, Math.min(2, engine.difficulty))]; } catch (Throwable e) { }
        if (tl == 0) return 300;
        if (tl == 2) return 1400;
        return 800;
    }

    private void startScan() {
        netInfo = "搜索局域网房间…";
        invalidate();
        new Thread(new Runnable() {
            public void run() {
                final List<Net.Room> rooms = Net.Discovery.scan(1800);
                handler.post(new Runnable() {
                    public void run() { showRooms(rooms); }
                });
            }
        }).start();
    }

    private void showRooms(final List<Net.Room> rooms) {
        if (rooms.isEmpty()) {
            netInfo = "未发现房间 · 可用「手动加入」输 IP";
            invalidate();
            return;
        }
        String[] items = new String[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) {
            items[i] = rooms.get(i).name + "   " + rooms.get(i).ip + " · 房号 " + rooms.get(i).room;
        }
        netInfo = "发现 " + rooms.size() + " 个房间";
        invalidate();
        try {
            Activity act = (Activity) getContext();
            new AlertDialog.Builder(act)
                    .setTitle("发现的房间")
                    .setItems(items, new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int which) {
                            Net.Room r = rooms.get(which);
                            joinRoom(r.ip, "", 0);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            netInfo = "无法弹出列表：" + e.getMessage();
            invalidate();
        }
    }

    private void joinRoom(String ip) {
        joinRoom(ip, "", 0);
    }

    private void joinRoom(String ip, String room, int seat) {
        Save.clear(getContext());
        if (ip == null || ip.length() < 5) { netInfo = "IP 格式不对"; invalidate(); return; }
        lastIp = ip;
        lastRoom = room == null ? "" : room;
        lastSeat = seat;
        if (seat == 0) retryLeft = 0;
        stopNet();
        handler.removeCallbacks(aiRunnable);
        engine = new Engine();
        engine.thinkLevels = thinkLevels;
        netMode = true;
        isHost = false;
        mySeat = 3;
        for (int i = 0; i < 4; i++) engine.isAi[i] = false;
        accent = E_WATER;
        netInfo = "连接中 " + ip + " …";
        screen = GAME;
        selected.clear();
        invalidate();
        netClient = new Net.Client(clientCB);
        try {
            netClient.connect(ip, Net.PORT, lastRoom, lastSeat);
        } catch (Exception e) {
            netInfo = "连接失败：" + e.getMessage();
            if (seat > 0) scheduleReconnect(); else screen = MENU;
            invalidate();
        }
    }

    private void applyState(final Map<String, Object> m) {
        int lvl = Json.i(m.get("lvl"));
        int t0 = Json.i(m.get("t0"));
        int t1 = Json.i(m.get("t1"));
        int round = Json.i(m.get("round"));
        int turnS = Json.i(m.get("turn"));
        int lastSeat = Json.i(m.get("lastSeat"));
        boolean over = Json.b(m.get("over"));
        String result = Json.s(m.get("result"));
        int gain = Json.i(m.get("gain"));

        List<Card> my = Net.cardsFrom(Json.l(m.get("hand")));
        int[] counts = new int[4];
        String[] tags = new String[4];
        boolean[] fins = new boolean[4];
        List<Object> seats = Json.l(m.get("seats"));
        for (int i = 0; i < 4 && i < seats.size(); i++) {
            Map<String, Object> sm = Json.m(seats.get(i));
            counts[i] = Json.i(sm.get("n"));
            tags[i] = Json.s(sm.get("tag"));
            fins[i] = Json.b(sm.get("fin"));
        }
        Play last = Net.playFrom(Json.m(m.get("last")));
        List<Integer> fin = new ArrayList<Integer>();
        for (Object o : Json.l(m.get("fin"))) fin.add(Json.i(o));
        List<String> logs = new ArrayList<String>();
        for (Object o : Json.l(m.get("logs"))) logs.add(Json.s(o));

        engine.applySnapshot(lvl, t0, t1, round, turnS, lastSeat, over, result, gain,
                my, mySeat, counts, tags, fins, last, fin, logs);
        selected.clear();
        sortHand();
        if (rejoining) { rejoining = false; retryLeft = 0; }
        speakLastPlay();
        for (int i = 0; i < 4; i++) {
            if (i == mySeat) { lastSeatTags[i] = tags[i]; continue; }
            if ("不要".equals(tags[i]) && !"不要".equals(lastSeatTags[i])) speakPass(i);
            lastSeatTags[i] = tags[i];
        }
        promptTurn();
        invalidate();
    }

    private final Net.HostCB hostCB = new Net.HostCB() {
        public void onClientJoined(final int seat) {
            handler.post(new Runnable() {
                public void run() {
                    if (!clientSeats.contains(seat)) clientSeats.add(seat);
                    engine.isAi[seat] = false;
                    engine.addLog("座位 " + Engine.seatName(seat) + " 已加入");
                    netInfo = "房间内 " + clientSeats.size() + " 位真人";
                    broadcastState();
                    invalidate();
                }
            });
        }

        public void onClientMsg(final int seat, final Map<String, Object> msg) {
            handler.post(new Runnable() {
                public void run() {
                    String t = Json.s(msg.get("t"));
                    if ("play".equals(t)) {
                        List<Object> ids = Json.l(msg.get("cards"));
                        List<Card> cards = new ArrayList<Card>();
                        for (Card c : engine.hands[seat]) {
                            for (Object o : ids) if (c.id == Json.i(o)) cards.add(c);
                        }
                        String err = engine.playCards(seat, cards);
                        if (err != null) engine.addLog(Engine.seatName(seat) + " 出牌无效：" + err);
                        speakLastPlay();
                        broadcastState();
                        invalidate();
                        scheduleAi();
                    } else if ("pass".equals(t)) {
                        engine.doPass(seat);
                        speakPass(seat);
                        broadcastState();
                        invalidate();
                        scheduleAi();
                    }
                }
            });
        }

        public void onClientLeft(final int seat) {
            handler.post(new Runnable() {
                public void run() {
                    clientSeats.remove(Integer.valueOf(seat));
                    engine.isAi[seat] = true;
                    engine.addLog("座位 " + Engine.seatName(seat) + " 已离开");
                    broadcastState();
                    invalidate();
                    scheduleAi();
                }
            });
        }

        public void onInfo(final String text) {
            handler.post(new Runnable() {
                public void run() { netInfo = text; invalidate(); }
            });
        }
    };

    private final Net.ClientCB clientCB = new Net.ClientCB() {
        public void onServerMsg(final Map<String, Object> msg) {
            handler.post(new Runnable() {
                public void run() {
                    String t = Json.s(msg.get("t"));
                    if ("seat".equals(t)) {
                        mySeat = Json.i(msg.get("seat"));
                        lastSeat = mySeat;
                        String rid = Json.s(msg.get("room"));
                        if (rid != null && rid.length() > 0) { lastRoom = rid; roomId = rid; }
                        retryLeft = 0;
                        rejoining = false;
                        netInfo = "已进入房间 · 座位 " + Engine.seatName(mySeat)
                                + (roomId.isEmpty() ? "" : " · 房号 " + roomId);
                        invalidate();
                    } else if ("full".equals(t)) {
                        netInfo = "房间已满";
                        screen = MENU;
                        invalidate();
                    } else if ("state".equals(t)) {
                        applyState(msg);
                    }
                }
            });
        }

        public void onConnected() {
            handler.post(new Runnable() {
                public void run() { netInfo = "已连接房主"; invalidate(); }
            });
        }

        public void onDisconnected(final String reason) {
            handler.post(new Runnable() {
                public void run() {
                    netInfo = reason;
                    invalidate();
                    if (netMode && !isHost && lastSeat > 0 && !rejoining) scheduleReconnect();
                }
            });
        }
    };
}
