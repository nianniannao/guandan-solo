import com.guandan.solo.*;
import java.util.*;

public class Test2 {
    public static void main(String[] args) {
        int totalPlays = 0, errs = 0, guards = 0, roundsDone = 0, badCons = 0;
        Map<String,Integer> t = new TreeMap<String,Integer>();
        for (int m = 0; m < 3; m++) {
            for (int round = 0; round < 15; round++) {
                Engine e = new Engine();
                e.autoHuman = true;
                e.newMatch(m);
                int guard = 0;
                int played = 0;
                while (!e.roundOver && guard++ < 4000) {
                    int seat = e.turn;
                    if (seat == 0) {
                        Play p = AI.choose(e, 0, AI.candidates(e.hands[0], e.level), e.lastPlay);
                        if (p == null) {
                            if (e.lastPlay != null) e.doPass(0);
                            else {
                                List<Card> h = e.hands[0];
                                Play s = Rules.evaluateExact(Collections.singletonList(h.get(h.size()-1)), e.level);
                                e.playCards(0, new ArrayList<Card>(s.cards));
                                played++;
                            }
                        } else {
                            String err = e.playCards(0, new ArrayList<Card>(p.cards));
                            if (err != null) { errs++; System.out.println("ERR " + err); break; }
                            played++;
                            Integer n = t.get(p.typeName());
                            t.put(p.typeName(), n == null ? 1 : n + 1);
                        }
                    } else {
                        e.aiStep();
                    }
                    int sum = 0;
                    for (int i = 0; i < 4; i++) sum += e.hands[i].size();
                    if (sum > 108) { badCons++; }
                }
                if (guard >= 4000) guards++;
                if (e.roundOver) roundsDone++;
                totalPlays += played;
            }
        }
        System.out.println("roundsDone=" + roundsDone + " plays=" + totalPlays + " errors=" + errs + " guards=" + guards + " badConsistency=" + badCons);
        System.out.println("types=" + t);
    }
}
