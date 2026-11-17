import com.guandan.solo.*;
import java.util.*;

public class Test {
    public static void main(String[] args) {
        int totalRounds = 0, totalPlays = 0, totalPass = 0, errs = 0, guards = 0;
        Map<String,Integer> typeCount = new HashMap<String,Integer>();
        for (int m = 0; m < 6; m++) {
            Engine e = new Engine();
            e.autoHuman = true;
            e.newMatch(m);
            int guard = 0;
            while (!e.roundOver && guard++ < 3000) {
                int seat = e.turn;
                if (seat == 0) {
                    List<Play> c = AI.candidates(e.hands[0], e.level);
                    Play p = AI.choose(e, 0, c, e.lastPlay);
                    if (p == null) {
                        if (e.lastPlay != null) { e.doPass(0); totalPass++; }
                        else {
                            List<Card> h = e.hands[0];
                            Play s = Rules.evaluateExact(Collections.singletonList(h.get(h.size()-1)), e.level);
                            String err = e.playCards(0, new ArrayList<Card>(s.cards));
                            if (err != null) { errs++; System.out.println("ERR-LEAD " + err); break; }
                            totalPlays++;
                        }
                    } else {
                        String err = e.playCards(0, new ArrayList<Card>(p.cards));
                        if (err != null) {
                            errs++;
                            System.out.println("ERR " + err + " | play=" + p.typeName() + " size=" + p.cards.size() + " key=" + p.key);
                            break;
                        }
                        totalPlays++;
                        Integer n = typeCount.get(p.typeName());
                        typeCount.put(p.typeName(), n == null ? 1 : n + 1);
                    }
                } else {
                    e.aiStep();
                }
                int sum = 0;
                for (int i = 0; i < 4; i++) sum += e.hands[i].size();
                if (sum != 108 - (e.finished.size() > 0 ? 0 : 0)) { /* 略 */ }
            }
            if (guard >= 3000) { guards++; System.out.println("GUARD HIT m=" + m); }
            totalRounds++;
        }
        System.out.println("rounds=" + totalRounds + " plays=" + totalPlays + " pass=" + totalPass + " errors=" + errs + " guards=" + guards);
        System.out.println("typeStats=" + typeCount);
    }
}
