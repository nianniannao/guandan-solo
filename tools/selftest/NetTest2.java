package com.guandan.solo;

import java.util.List;
import java.util.Map;

public class NetTest2 {
    static int joinedSeat = -1;
    static int leftSeat = -1;
    static int seatMsg = -1;
    static boolean connected = false;

    public static void main(String[] args) throws Exception {
        Net.Host host = new Net.Host(new Net.HostCB() {
            public void onClientJoined(int seat) { joinedSeat = seat; System.out.println("[host] 加入座位 " + seat); }
            public void onClientMsg(int seat, Map<String, Object> msg) { }
            public void onClientLeft(int seat) { leftSeat = seat; System.out.println("[host] 离开座位 " + seat); }
            public void onInfo(String text) { System.out.println("[host] " + text); }
        });
        host.start();
        String room = host.roomId();
        System.out.println("房号 " + room);
        Net.Discovery.startAnnounce("掼蛋房间", room);
        Thread.sleep(300);

        // 1) 广播发现
        long t0 = System.currentTimeMillis();
        List<Net.Room> rooms = Net.Discovery.scan(1500);
        System.out.println("发现房间 " + rooms.size() + " 个，用时 " + (System.currentTimeMillis() - t0) + "ms");
        for (Net.Room r : rooms) System.out.println("  " + r.name + " / " + r.ip + ":" + r.port + " / 房号 " + r.room);
        check(rooms.size() >= 1, "发现房间数>=1");
        check(rooms.get(0).room.equals(room), "房号回传一致");

        // 2) 首次加入
        Net.Client c1 = new Net.Client(new CB());
        c1.connect("127.0.0.1", Net.PORT, "", 0);
        Thread.sleep(600);
        check(seatMsg == 1, "首次加入分配座位 1（实际 " + seatMsg + "）");
        check(joinedSeat == 1, "房主收到加入事件");

        // 3) 掉线
        c1.close();
        Thread.sleep(700);
        check(leftSeat == 1, "房主收到离开事件");

        // 4) 带房号重连，收回原座位
        seatMsg = -1;
        Net.Client c2 = new Net.Client(new CB());
        c2.connect("127.0.0.1", Net.PORT, room, 1);
        Thread.sleep(600);
        check(seatMsg == 1, "重连收回座位 1（实际 " + seatMsg + "）");
        check(joinedSeat == 1, "房主收到重连加入事件");

        // 5) 房间满
        Net.Discovery.stop();
        c2.close();
        host.stop();
        System.out.println("全部通过");
    }

    static void check(boolean ok, String label) {
        System.out.println((ok ? "OK   " : "FAIL ") + label);
        if (!ok) System.exit(1);
    }

    static class CB implements Net.ClientCB {
        public void onServerMsg(Map<String, Object> msg) {
            String t = Json.s(msg.get("t"));
            if ("seat".equals(t)) seatMsg = Json.i(msg.get("seat"));
        }
        public void onConnected() { connected = true; }
        public void onDisconnected(String reason) { }
    }
}
