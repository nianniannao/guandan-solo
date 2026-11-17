import com.guandan.solo.*;
import java.util.*;
import java.util.concurrent.*;

public class NetTest {
    public static void main(String[] a) throws Exception {
        final Engine e = new Engine();
        e.newMatch(Engine.DIFF_ADVANCED);
        e.isAi[0] = false; e.isAi[1] = false; e.isAi[2] = true; e.isAi[3] = true;

        final CountDownLatch joined = new CountDownLatch(1);
        Net.Host host = new Net.Host(new Net.HostCB() {
            public void onClientJoined(int seat) {
                System.out.println("[host] client joined seat=" + seat);
                e.isAi[seat] = false;
                hostBroadcast = true;
                joined.countDown();
            }
            public void onClientMsg(int seat, Map<String,Object> msg) {
                String t = Json.s(msg.get("t"));
                if ("play".equals(t)) {
                    List<Object> ids = Json.l(msg.get("cards"));
                    List<Card> cards = new ArrayList<Card>();
                    for (Card c : e.hands[seat]) for (Object o : ids) if (c.id == Json.i(o)) cards.add(c);
                    String err = e.playCards(seat, cards);
                    System.out.println("[host] got play seat=" + seat + " err=" + err + " turn=" + e.turn);
                } else if ("pass".equals(t)) {
                    e.doPass(seat);
                    System.out.println("[host] got pass seat=" + seat + " turn=" + e.turn);
                }
            }
            public void onClientLeft(int seat) { System.out.println("[host] left " + seat); }
            public void onInfo(String s) { System.out.println("[host] info " + s); }
        });
        host.start();
        System.out.println("[host] listening");

        final CountDownLatch gotState = new CountDownLatch(1);
        final List<Card> clientHand = new ArrayList<Card>();
        final int[] mySeat = new int[]{-1};
        Net.Client c = new Net.Client(new Net.ClientCB() {
            public void onServerMsg(Map<String,Object> m) {
                String t = Json.s(m.get("t"));
                if ("seat".equals(t)) { mySeat[0] = Json.i(m.get("seat")); System.out.println("[client] seat=" + mySeat[0]); }
                assertSt = 0;
                if ("state".equals(t) && lastState == null) {
                    lastState = m;
                    clientHand.clear();
                    clientHand.addAll(Net.cardsFrom(Json.l(m.get("hand"))));
                    System.out.println("[client] state hand=" + clientHand.size() + " turn=" + Json.i(m.get("turn")) + " lvl=" + Json.i(m.get("lvl")));
                    gotState.countDown();
                }
            }
            public void onConnected() { System.out.println("[client] connected"); }
            public void onDisconnected(String r) { System.out.println("[client] disconnected " + r); }
        });
        c.connect("127.0.0.1", Net.PORT);
        Thread.sleep(300);
        if (hostBroadcast) {
            host.sendTo(1, Net.stateFor(e, 1, e.isAi));
        }
        if (!gotState.await(5, TimeUnit.SECONDS)) { System.out.println("FAIL: no state"); host.stop(); return; }

        // 房主先出一张，再推进到座位1的回合
        if (e.turn == 0) {
            List<Card> h0 = e.hands[0];
            Card c0 = h0.get(h0.size() - 1);
            String err0 = e.playCards(0, new ArrayList<Card>(Collections.singletonList(c0)));
            System.out.println("[host] host played seat0 err=" + err0);
        }
        int guard = 0;
        while (e.turn != 1 && guard++ < 60) {
            if (e.isAi[e.turn]) e.aiStep();
            else break;
            if (e.roundOver) break;
        }
        host.sendTo(1, Net.stateFor(e, 1, e.isAi));
        System.out.println("[host] turn now " + e.turn + " seat1 hand=" + e.hands[1].size());

        if (e.turn == 1) {
            List<Card> hh = e.hands[1];
            List<Object> ids = new ArrayList<Object>();
            int handBefore = hh.size();
            boolean sentPlay = false;
            for (Card card : hh) {
                Play p = Rules.evaluateExact(Collections.singletonList(card), e.level);
                if (p != null && Rules.canBeat(e.lastPlay, p)) {
                    ids.add(card.id);
                    c.send(Json.obj("t", "play", "cards", ids));
                    sentPlay = true;
                    break;
                }
            }
            if (!sentPlay) c.send(Json.obj("t", "pass"));
            Thread.sleep(400);
            System.out.println("[host] client action sent=" + (sentPlay ? "play" : "pass") + " handBefore=" + handBefore
                    + " handAfter=" + e.hands[1].size() + " turn=" + e.turn);
        } else {
            System.out.println("SKIP play test, turn=" + e.turn);
        }
        c.close();
        host.stop();
        System.out.println("NETTEST DONE");
    }
    static volatile boolean hostBroadcast = false;
    static volatile int assertSt = 0;
    static volatile Map<String,Object> lastState = null;
}
