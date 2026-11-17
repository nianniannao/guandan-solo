import com.guandan.solo.*;

public class AiTest {
    public static void main(String[] a) {
        int totalGames = 0, rounds = 0, stepsAll = 0;
        long worst = 0;
        for (int diff = 0; diff <= 2; diff++) {
            int games = 0;
            long t0 = System.currentTimeMillis();
            while (games < 40) {
                AI.thinkLevel = diff == 2 ? 2 : 1;
                Engine e = new Engine();
                e.difficulty = diff;
                e.autoHuman = true;
                e.newMatch(diff);
                int steps = 0;
                while (!e.roundOver && steps < 1200) {
                    long s0 = System.nanoTime();
                    e.aiStep();
                    long dt = (System.nanoTime() - s0) / 1000000;
                    if (dt > worst) worst = dt;
                    steps++;
                }
                if (!e.roundOver) throw new RuntimeException("对局未结束 diff=" + diff + " steps=" + steps);
                checkCards(e);
                games++;
                rounds += e.roundNo;
                stepsAll += steps;
                totalGames++;
            }
            System.out.println("diff=" + diff + " 40局完成，用时 " + (System.currentTimeMillis() - t0) + "ms");
        }
        System.out.println("总对局 " + totalGames + "，总轮次 " + rounds + "，总动作 " + stepsAll + "，单步最慢 " + worst + "ms");
    }

    static void checkCards(Engine e) {
        int total = 0;
        for (int i = 0; i < 4; i++) total += e.hands[i].size();
        if (total < 0 || total > 108) throw new RuntimeException("牌数异常 " + total);
    }
}
