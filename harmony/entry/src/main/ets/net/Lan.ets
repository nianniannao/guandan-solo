import socket from '@ohos.net.socket';

export interface SeatMsg {
  n: number;
  tag: string;
  ai: boolean;
  fin: boolean;
}

export interface LastMsg {
  type: number;
  key: number;
  power: number;
  size: number;
  cards: number[][];
}

export interface StateMsg {
  t: string;
  lvl: number;
  t0: number;
  t1: number;
  round: number;
  turn: number;
  lastSeat: number;
  over: boolean;
  result: string;
  gain: number;
  hand: number[][];
  seats: SeatMsg[];
  last: LastMsg | null;
  fin: number[];
  logs: string[];
}

export interface SeatMsgOnly {
  t: string;
  seat: number;
  room?: string;
}

export const PORT: number = 48620;
export const DISCOVER_PORT: number = 48621;

export class LanClient {
  private tcp: socket.TCPSocket | null = null;
  private room: string = '';
  private seat: number = 0;
  onState: (s: StateMsg) => void = () => {};
  onSeat: (seat: number, room: string) => void = () => {};
  onInfo: (text: string) => void = () => {};
  onDisconnect: (reason: string) => void = () => {};

  connect(ip: string, room: string, seat: number): void {
    const tcp = socket.constructTCPSocketInstance();
    this.tcp = tcp;
    this.room = room;
    this.seat = seat;
    tcp.on('message', (value: socket.SocketMessageInfo) => {
      const text: string = value.message.toString();
      const lines: string[] = text.split('\n');
      for (let i = 0; i < lines.length; i++) {
        const line: string = lines[i].trim();
        if (line.length === 0) continue;
        try {
          const obj = JSON.parse(line) as StateMsg;
          const t: string = obj.t;
          if (t === 'seat') {
            const only = obj as object as SeatMsgOnly;
            this.onSeat(only.seat, only.room === undefined ? '' : only.room);
          }
          else if (t === 'state') this.onState(obj);
        } catch (e) {
          this.onInfo('收到无法解析的消息');
        }
      }
    });
    tcp.on('close', () => { this.onDisconnect('连接已断开'); });
    tcp.connect({
      address: { address: ip, port: PORT, family: 1 },
      timeout: 5000
    }).then(() => {
      this.send({ 't': 'hello', 'room': this.room, 'seat': this.seat });
      this.onInfo('已连接房主');
    }).catch((e: Error) => {
      this.onInfo('连接失败：' + e.message);
      this.onDisconnect(e.message);
    });
  }

  send(msg: Record<string, Object>): void {
    if (this.tcp === null) return;
    this.tcp.send({ data: JSON.stringify(msg) + '\n' }).catch(() => {});
  }

  close(): void {
    if (this.tcp !== null) {
      this.tcp.close();
      this.tcp = null;
    }
  }
}

export class LanHost {
  private server: socket.TCPSocketServer | null = null;
  private udp: socket.UDPSocket | null = null;
  private clients: socket.TCPSocket[] = [];
  private seats: number[] = [];
  private reserved: Map<number, number> = new Map<number, number>();
  roomId: string = LanHost.makeId();

  static makeId(): string {
    const n: number = Math.floor(Math.random() * 0xFFFF);
    let s: string = n.toString(16).toUpperCase();
    while (s.length < 4) s = '0' + s;
    return s.substring(s.length - 4);
  }
  onJoin: (seat: number) => void = () => {};
  onPlay: (seat: number, ids: number[]) => void = () => {};
  onPass: (seat: number) => void = () => {};
  onInfo: (text: string) => void = () => {};

  listen(): void {
    const server = socket.constructTCPSocketServerInstance();
    this.server = server;
    server.on('connect', (client: socket.TCPSocket) => {
      let seat: number = -1;
      const claim: number[] = [-1];
      this.clients.push(client);
      this.seats.push(seat);
      client.on('message', (value: socket.SocketMessageInfo) => {
        const text: string = value.message.toString();
        const lines: string[] = text.split('\n');
        for (let i = 0; i < lines.length; i++) {
          const line: string = lines[i].trim();
          if (line.length === 0) continue;
          try {
            const obj = JSON.parse(line) as Record<string, Object>;
            const t: string = obj.t as string;
            if (claim[0] < 0) {
              // 首条消息：hello，决定座位
              let want: number = -1;
              if (t === 'hello' && (obj.room as string) === this.roomId) {
                want = Number(obj.seat);
              }
              if (want >= 1 && want <= 3 && this.seats.indexOf(want) < 0) {
                seat = want;
                this.reserved.delete(want);
              } else {
                for (let i2 = 1; i2 <= 3; i2++) {
                  if (this.seats.indexOf(i2) < 0 && !this.reserved.has(i2)) { seat = i2; break; }
                }
              }
              if (seat < 0) {
                client.send({ data: JSON.stringify({ 't': 'full' }) + '\n' }).catch(() => {});
                client.close();
                return;
              }
              claim[0] = seat;
              const idx: number = this.seats.indexOf(-1);
              this.seats[idx] = seat;
              client.send({
                data: JSON.stringify({ 't': 'seat', 'seat': seat, 'room': this.roomId }) + '\n'
              }).catch(() => {});
              this.onJoin(seat);
              return;
            }
            if (t === 'play') {
              const raw = obj.cards as number[];
              const ids: number[] = [];
              for (let k = 0; k < raw.length; k++) ids.push(Number(raw[k]));
              this.onPlay(seat, ids);
            } else if (t === 'pass') {
              this.onPass(seat);
            }
          } catch (e) {}
        }
      });
      client.on('close', () => {
        const s: number = claim[0];
        const idx: number = this.seats.indexOf(s);
        if (idx >= 0) {
          this.seats.splice(idx, 1);
          this.clients.splice(idx, 1);
          this.reserved.set(s, Date.now());
        }
      });
    });
    server.listen({ address: { address: '0.0.0.0', port: PORT, family: 1 } })
      .then(() => this.onInfo('房间已开启，端口 ' + PORT))
      .catch((e: Error) => this.onInfo('开房失败：' + e.message));
  }

  /** 开启 UDP 房间广播应答，加入方可在局域网内自动发现 */
  announce(): void {
    const udp = socket.constructUDPSocketInstance();
    this.udp = udp;
    udp.setExtraOptions({ broadcast: true }).catch(() => {});
    udp.bind({ address: '0.0.0.0', port: DISCOVER_PORT, family: 1 }).then(() => {
      udp.on('message', (value: socket.SocketMessageInfo) => {
        const req: string = value.message.toString();
        if (req.indexOf('GD?') === 0) {
          const ack: string = 'GD!掼蛋房间|' + PORT + '|' + this.roomId;
          udp.send({
            data: ack,
            address: { address: '255.255.255.255', port: DISCOVER_PORT, family: 1 }
          }).catch(() => {});
        }
      });
      this.onInfo('房间广播已开启 · 房号 ' + this.roomId);
    }).catch((e: Error) => this.onInfo('广播不可用：' + e.message));
  }

  sendTo(seat: number, line: string): void {
    const idx: number = this.seats.indexOf(seat);
    if (idx < 0) return;
    this.clients[idx].send({ data: line + '\n' }).catch(() => {});
  }

  broadcast(line: string): void {
    for (let i = 0; i < this.clients.length; i++) {
      this.clients[i].send({ data: line + '\n' }).catch(() => {});
    }
  }

  stop(): void {
    for (let i = 0; i < this.clients.length; i++) this.clients[i].close();
    this.clients = [];
    this.seats = [];
    if (this.server !== null) { this.server.off('connect'); this.server = null; }
    if (this.udp !== null) { this.udp.close(); this.udp = null; }
  }
}
