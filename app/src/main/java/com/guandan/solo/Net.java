package com.guandan.solo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Net {

    public static final int PORT = 48620;
    public static final int DISCOVER_PORT = 48621;
    private static final String REQ = "GD?";
    private static final String ACK = "GD!";

    public interface HostCB {
        void onClientJoined(int seat);
        void onClientMsg(int seat, Map<String, Object> msg);
        void onClientLeft(int seat);
        void onInfo(String text);
    }

    public interface ClientCB {
        void onServerMsg(Map<String, Object> msg);
        void onConnected();
        void onDisconnected(String reason);
    }

    // ===================== 房间发现（UDP 广播） =====================

    public static class Room {
        public String name = "";
        public String ip = "";
        public int port = PORT;
        public String room = "";
    }

    public static class Discovery {
        private static DatagramSocket sock;
        private static String roomLabel = "";
        private static String roomId = "";

        /** 房主侧：监听 UDP 广播询问并应答 */
        public static void startAnnounce(final String label, final String id) throws IOException {
            stop();
            final DatagramSocket s = new DatagramSocket(DISCOVER_PORT);
            s.setBroadcast(true);
            sock = s;
            roomLabel = label;
            roomId = id;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    byte[] buf = new byte[256];
                    while (sock != null && !sock.isClosed()) {
                        try {
                            DatagramPacket p = new DatagramPacket(buf, buf.length);
                            s.receive(p);
                            String req = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                            if (req.startsWith(REQ)) {
                                String ack = ACK + roomLabel + "|" + PORT + "|" + roomId;
                                byte[] out = ack.getBytes("UTF-8");
                                s.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                            }
                        } catch (IOException e) {
                            return;
                        }
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        public static void stop() {
            if (sock != null) {
                try { sock.close(); } catch (Exception e) { }
                sock = null;
            }
        }

        /** 加入侧：广播搜索，返回局域网内所有房间 */
        public static List<Room> scan(int timeoutMs) {
            List<Room> out = new ArrayList<Room>();
            DatagramSocket s = null;
            try {
                s = new DatagramSocket();
                s.setBroadcast(true);
                s.setSoTimeout(250);
                sendAll(s);
                long end = System.currentTimeMillis() + timeoutMs;
                byte[] buf = new byte[256];
                while (System.currentTimeMillis() < end) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        s.receive(p);
                        String ack = new String(p.getData(), 0, p.getLength(), "UTF-8").trim();
                        if (!ack.startsWith(ACK)) continue;
                        String[] parts = ack.substring(ACK.length()).split("\\|");
                        Room r = new Room();
                        r.name = parts.length > 0 ? parts[0] : "掼蛋房间";
                        r.port = parts.length > 1 ? safeInt(parts[1]) : PORT;
                        r.room = parts.length > 2 ? parts[2] : "";
                        r.ip = p.getAddress().getHostAddress();
                        boolean dup = false;
                        for (Room x : out) if (x.ip.equals(r.ip) && x.port == r.port) dup = true;
                        if (!dup) out.add(r);
                    } catch (SocketTimeoutException ste) {
                        // 继续等
                    }
                }
            } catch (Exception e) {
                // 无网络等
            } finally {
                if (s != null) { try { s.close(); } catch (Exception e) { } }
            }
            return out;
        }

        private static int safeInt(String v) {
            try { return Integer.parseInt(v.trim()); } catch (Exception e) { return PORT; }
        }

        private static void sendAll(DatagramSocket s) throws IOException {
            byte[] out = REQ.getBytes("UTF-8");
            s.send(new DatagramPacket(out, out.length,
                    InetAddress.getByName("255.255.255.255"), DISCOVER_PORT));
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b != null) s.send(new DatagramPacket(out, out.length, b, DISCOVER_PORT));
                }
            }
            s.send(new DatagramPacket(out, out.length,
                    InetAddress.getByName("127.0.0.1"), DISCOVER_PORT));
        }
    }

    // ===================== 房主 =====================

    public static class Host {
        private ServerSocket server;
        private final List<Socket> socks = new ArrayList<Socket>();
        private final List<Integer> seats = new ArrayList<Integer>();
        private final Map<Integer, Long> reserved = new LinkedHashMap<Integer, Long>();
        private final HostCB cb;
        private volatile boolean running = false;
        private final String roomId;
        private static final long RESERVE_MS = 90000L;

        public Host(HostCB cb) {
            this.cb = cb;
            this.roomId = randomId();
        }

        public String roomId() { return roomId; }

        private static String randomId() {
            String s = Integer.toHexString((int) (Math.random() * 0xFFFF)).toUpperCase();
            while (s.length() < 4) s = "0" + s;
            return s.substring(s.length() - 4);
        }

        public void start() throws IOException {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
            running = true;
            Thread t = new Thread(new Runnable() {
                public void run() { acceptLoop(); }
            });
            t.setDaemon(true);
            t.start();
        }

        private boolean seatTaken(int seat) {
            synchronized (socks) {
                if (seats.contains(Integer.valueOf(seat))) return true;
                Long t = reserved.get(Integer.valueOf(seat));
                return t != null && System.currentTimeMillis() - t < RESERVE_MS;
            }
        }

        private void expireReservations() {
            long now = System.currentTimeMillis();
            List<Integer> dead = new ArrayList<Integer>();
            for (Map.Entry<Integer, Long> e : reserved.entrySet()) {
                if (now - e.getValue() >= RESERVE_MS) dead.add(e.getKey());
            }
            for (Integer s : dead) reserved.remove(s);
        }

        private void acceptLoop() {
            while (running) {
                try {
                    final Socket s = server.accept();
                    s.setTcpNoDelay(true);
                    final BufferedReader r = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), "UTF-8"));

                    // 首行必须是 hello（含房间号时表示重连）
                    String first = null;
                    try {
                        s.setSoTimeout(6000);
                        first = r.readLine();
                        s.setSoTimeout(0);
                    } catch (IOException e) {
                        try { s.close(); } catch (Exception e2) { }
                        continue;
                    }
                    if (first == null) { try { s.close(); } catch (Exception e) { } continue; }

                    int seat = -1;
                    Map<String, Object> hm = Json.m(Json.parse(first));
                    String rt = Json.s(hm.get("t"));
                    String room = Json.s(hm.get("room"));
                    int want = Json.i(hm.get("seat"));
                    boolean active = false;
                    synchronized (socks) {
                        expireReservations();
                        active = seats.contains(Integer.valueOf(want));
                    }
                    if ("hello".equals(rt) && roomId.equalsIgnoreCase(room)
                            && want >= 1 && want <= 3 && !active) {
                        seat = want;
                        synchronized (socks) { reserved.remove(Integer.valueOf(want)); }
                    }
                    if (seat < 0) {
                        synchronized (socks) {
                            expireReservations();
                            for (int i = 1; i <= 3; i++) {
                                if (seats.contains(Integer.valueOf(i))) continue;
                                if (reserved.containsKey(Integer.valueOf(i))) continue;
                                seat = i;
                                break;
                            }
                        }
                    }
                    if (seat < 0) {
                        writeRaw(s, Json.str(Json.obj("t", "full", "msg", "房间已满")));
                        s.close();
                        continue;
                    }

                    synchronized (socks) {
                        socks.add(s);
                        seats.add(seat);
                    }
                    int seatNo = seat;
                    cb.onClientJoined(seatNo);
                    writeRaw(s, Json.str(Json.obj("t", "seat", "seat", seatNo, "room", roomId)));

                    Thread rt2 = new Thread(new Runnable() {
                        public void run() { readLoop(s, seatNo, r); }
                    });
                    rt2.setDaemon(true);
                    rt2.start();
                } catch (IOException e) {
                    if (running) cb.onInfo("房间异常：" + e.getMessage());
                    return;
                }
            }
        }

        private void readLoop(Socket s, int seat, BufferedReader r) {
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    Map<String, Object> m = Json.m(Json.parse(line));
                    String t = Json.s(m.get("t"));
                    if ("ping".equals(t)) continue;
                    cb.onClientMsg(seat, m);
                }
            } catch (IOException e) {
                // 断线
            }
            synchronized (socks) {
                int idx = seats.indexOf(Integer.valueOf(seat));
                if (idx >= 0) {
                    try { socks.get(idx).close(); } catch (Exception e) { }
                    socks.remove(idx);
                    seats.remove(idx);
                }
                reserved.put(Integer.valueOf(seat), System.currentTimeMillis());
            }
            cb.onClientLeft(seat);
        }

        public void sendTo(int seat, String line) {
            synchronized (socks) {
                int idx = seats.indexOf(Integer.valueOf(seat));
                if (idx < 0) return;
                writeRaw(socks.get(idx), line);
            }
        }

        private void writeRaw(Socket s, String line) {
            try {
                OutputStream os = s.getOutputStream();
                os.write((line + "\n").getBytes("UTF-8"));
                os.flush();
            } catch (IOException e) { }
        }

        public void broadcast(String line) {
            synchronized (socks) {
                for (Socket s : socks) writeRaw(s, line);
            }
        }

        public int clientCount() {
            synchronized (socks) { return socks.size(); }
        }

        public void stop() {
            running = false;
            synchronized (socks) {
                for (Socket s : socks) { try { s.close(); } catch (Exception e) { } }
                socks.clear();
                seats.clear();
                reserved.clear();
            }
            try { if (server != null) server.close(); } catch (Exception e) { }
            Discovery.stop();
        }
    }

    // ===================== 加入方 =====================

    public static class Client {
        private Socket socket;
        private final ClientCB cb;
        private volatile boolean running = false;
        private String room = "";
        private int seat = 0;

        public Client(ClientCB cb) { this.cb = cb; }

        /** 加入房间；room 为空表示首次加入（由房主分配座位） */
        public void connect(String ip, int port, String room, int seat) throws IOException {
            this.room = room == null ? "" : room;
            this.seat = seat;
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), 4000);
            socket.setTcpNoDelay(true);
            running = true;
            send(Json.obj("t", "hello", "room", this.room, "seat", this.seat));
            Thread t = new Thread(new Runnable() {
                public void run() {
                    cb.onConnected();
                    try {
                        BufferedReader r = new BufferedReader(
                                new InputStreamReader(socket.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) cb.onServerMsg(Json.m(Json.parse(line)));
                    } catch (IOException e) { }
                    running = false;
                    cb.onDisconnected("连接已断开");
                }
            });
            t.setDaemon(true);
            t.start();
        }

        public void connect(String ip, int port) throws IOException {
            connect(ip, port, "", 0);
        }

        public void send(Map<String, Object> m) {
            if (socket == null || !running) return;
            try {
                OutputStream os = socket.getOutputStream();
                os.write((Json.str(m) + "\n").getBytes("UTF-8"));
                os.flush();
            } catch (IOException e) { }
        }

        public void close() {
            running = false;
            try { if (socket != null) socket.close(); } catch (Exception e) { }
        }
    }

    // ===================== 状态快照 =====================

    public static String stateFor(Engine e, int forSeat, boolean[] aiSeat) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("t", "state");
        m.put("lvl", e.level);
        m.put("t0", e.teamLevel[0]);
        m.put("t1", e.teamLevel[1]);
        m.put("round", e.roundNo);
        m.put("turn", e.turn);
        m.put("lastSeat", e.lastPlayer);
        m.put("over", e.roundOver);
        m.put("result", e.resultText);
        m.put("gain", e.lastGain);

        List<Object> hand = new ArrayList<Object>();
        if (forSeat >= 0) {
            for (Card c : e.hands[forSeat]) hand.add(cardArr(c));
        }
        m.put("hand", hand);

        List<Object> seats = new ArrayList<Object>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> sm = new LinkedHashMap<String, Object>();
            sm.put("n", e.hands[i].size());
            Play lp = e.seatLast[i];
            sm.put("tag", lp != null ? lp.typeName() : (e.seatPassed[i] ? "不要" : "—"));
            sm.put("ai", aiSeat != null && aiSeat[i]);
            sm.put("fin", e.isFinished(i));
            seats.add(sm);
        }
        m.put("seats", seats);

        if (e.lastPlay != null) {
            Map<String, Object> lm = new LinkedHashMap<String, Object>();
            lm.put("type", e.lastPlay.type);
            lm.put("key", e.lastPlay.key);
            lm.put("power", e.lastPlay.power);
            lm.put("size", e.lastPlay.size);
            List<Object> cs = new ArrayList<Object>();
            for (Card c : e.lastPlay.cards) cs.add(cardArr(c));
            lm.put("cards", cs);
            m.put("last", lm);
        } else {
            m.put("last", null);
        }

        List<Object> fin = new ArrayList<Object>();
        for (int s : e.finished) fin.add(s);
        m.put("fin", fin);

        List<Object> logs = new ArrayList<Object>();
        for (int i = 0; i < Math.min(3, e.log.size()); i++) logs.add(e.log.get(i));
        m.put("logs", logs);
        return Json.str(m);
    }

    private static List<Object> cardArr(Card c) {
        List<Object> a = new ArrayList<Object>();
        a.add(c.id);
        a.add(c.rank);
        a.add(c.suit);
        return a;
    }

    public static List<Card> cardsFrom(List<Object> arr) {
        List<Card> out = new ArrayList<Card>();
        for (Object o : arr) {
            List<Object> t = Json.l(o);
            if (t.size() < 3) continue;
            Card c = new Card(Json.i(t.get(2)), Json.i(t.get(1)));
            c.id = Json.i(t.get(0));
            out.add(c);
        }
        return out;
    }

    public static Play playFrom(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return null;
        Play p = new Play();
        p.type = Json.i(m.get("type"));
        p.key = Json.i(m.get("key"));
        p.power = ((Number) (m.get("power") == null ? 0 : m.get("power"))).doubleValue();
        p.size = Json.i(m.get("size"));
        p.cards = cardsFrom(Json.l(m.get("cards")));
        return p;
    }
}
