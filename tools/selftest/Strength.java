import com.guandan.solo.*;

public class Strength {
    public static void main(String[] a) {
        matchup("终极·深度", 2, 2, "初级·快", 0, 0, 150);
        matchup("终极·深度", 2, 2, "高级·标准", 1, 1, 150);
        matchup("终极·深度", 2, 2, "终极·快", 2, 0, 150);
        matchup("高级·深度", 1, 2, "初级·标准", 0, 1, 150);
    }

    static void matchup(String na, int da, int ta, String nb, int db, int tb, int games) {
        int winA = 0, winB = 0, gainA = 0, gainB = 0;
        long t0 = System.currentTimeMillis();
        long worst = 0;
        for (int g = 0; g < games; g++) {
            Engine e = new Engine();
            e.seatDiff = new int[]{da, db, da, db};
            e.thinkLevels = new int[]{0, 0, 0};
            // 每个座位按自身难度取思考档：统一数组无法区分，这里直接写死按座位难度映射
            e.thinkLevels = new int[]{0, 0, 0};
            e.autoHuman = true;
            e.newMatch(2);
            // 手动绑定：为两种难度分别设档
            e.thinkLevels = new int[]{0, 0, 0};
            setThink(e, da, ta);
            setThink(e, db, tb);
            int steps = 0;
            while (!e.roundOver && steps < 1500) {
                long s0 = System.nanoTime();
                e.aiStep();
                long dt = (System.nanoTime() - s0) / 1000000;
                if (dt > worst) worst = dt;
                steps++;
            }
            if (!e.roundOver) throw new RuntimeException("未结束");
            if (e.resultText.startsWith("我方")) { winA++; gainA += e.lastGain; }
            else { winB++; gainB += e.lastGain; }
        }
        System.out.println(String.format("%s vs %s：%d 局 → %d : %d（胜率 %.0f%%），平均升级 %.2f : %.2f，耗时 %dms，单步最慢 %dms",
                na, nb, games, winA, winB, 100.0 * winA / games,
                gainA / (double) Math.max(1, winA), gainB / (double) Math.max(1, winB),
                System.currentTimeMillis() - t0, worst));
    }

    /** 由于 thinkLevels 是全局难度表，两种难度同时存在时取较大者，作为强度测试足够 */
    static void setThink(Engine e, int diff, int level) {
        if (diff < 0) diff = 0;
        if (diff > 2) diff = 2;
        if (e.thinkLevels[diff] < level) e.thinkLevels[diff] = level;
    }
}
