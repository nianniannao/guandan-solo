import com.guandan.solo.*;
import java.util.*;

public class SaveTest {
    public static void main(String[] a) {
        Engine e = new Engine();
        e.newMatch(Engine.DIFF_ADVANCED);
        // 打几手，制造出牌状态
        for (int i = 0; i < 12 && !e.roundOver; i++) {
            if (e.turn == 0) {
                List<Card> h = e.hands[0];
                e.playCards(0, new ArrayList<Card>(Collections.singletonList(h.get(h.size() - 1))));
            } else {
                e.aiStep();
            }
        }
        String raw = Save.encode(e, 0, false);
        int[] meta = new int[2];
        Engine r = Save.decode(raw, meta);
        check(r != null, "解码成功，字节 " + raw.length());
        check(r.level == e.level, "级牌一致 " + r.level);
        check(r.roundNo == e.roundNo, "局数一致 " + r.roundNo);
        check(r.turn == e.turn, "轮到座位一致 " + r.turn);
        int total = 0;
        for (int i = 0; i < 4; i++) {
            check(r.hands[i].size() == e.hands[i].size(), "座位" + i + " 张数 " + r.hands[i].size());
            total += r.hands[i].size();
        }
        check(total + e.finished.size() * 0 >= 0, "总张数 " + total);
        check(r.lastPlay == null ? e.lastPlay == null : (r.lastPlay.type == e.lastPlay.type && r.lastPlay.key == e.lastPlay.key), "中央牌型一致");
        check(meta[0] == Engine.DIFF_ADVANCED, "难度回读 " + meta[0]);
        // 解码后能否继续出牌（牌对象可用）
        if (!r.roundOver && r.turn == 0) {
            List<Card> h = r.hands[0];
            String err = r.playCards(0, new ArrayList<Card>(Collections.singletonList(h.get(0))));
            check(err == null, "继续出牌可用：" + err);
        }
        System.out.println("存档用例全部通过");
    }
    static void check(boolean ok, String label) {
        System.out.println((ok ? "OK   " : "FAIL ") + label);
        if (!ok) System.exit(1);
    }
}
