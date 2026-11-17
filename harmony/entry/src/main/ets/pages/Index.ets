import { Engine, Card, Play, Rules, Ai } from '../model/Guandan';
import { LanHost, LanClient, StateMsg, StateMsgSeat, PORT } from '../net/Lan';

interface CardView {
  id: number;
  text: string;
  red: boolean;
  wild: boolean;
}

interface SeatView {
  name: string;
  pos: string;
  count: string;
  tag: string;
  active: boolean;
}

@Entry
@Component
struct Index {
  @State screen: string = 'menu';
  @State hand: CardView[] = [];
  @State picked: number[] = [];
  @State seats: SeatView[] = [];
  @State lastCards: CardView[] = [];
  @State lastTag: string = '';
  @State headLine: string = '';
  @State logLine: string = '';
  @State tip: string = '';
  @State over: boolean = false;
  @State result: string = '';
  @State showIp: boolean = false;
  @State ipText: string = '';
  @State online: boolean = false;
  @State amHost: boolean = false;
  @State mySeat: number = 0;
  @State accent: string = '#C8A96A';
  @State thinkLevel: number = 1;

  private engine: Engine = new Engine();
  private host: LanHost | null = null;
  private client: LanClient | null = null;
  private remoteSeats: number[] = [];
  private lastIp: string = '';
  private lastRoom: string = '';
  private lastSeat: number = 0;
  private retryLeft: number = 0;

  aboutToDisappear(): void {
    if (this.host !== null) this.host.stop();
    if (this.client !== null) this.client.close();
  }

  // ---------- 本地单机 ----------
  startLocal(diff: number): void {
    this.engine = new Engine();
    this.engine.difficulty = diff;
    this.mySeat = 0;
    this.online = false;
    this.amHost = false;
    this.accent = diff === 0 ? '#4E8E6A' : (diff === 1 ? '#C8A96A' : '#4C6E96');
    this.engine.newMatch(diff);
    for (let i = 0; i < 4; i++) this.engine.isAi[i] = i !== 0;
    this.screen = 'table';
    this.picked = [];
    this.refresh();
    this.loopAi();
  }

  thinkDelay(): number {
    if (this.engine.difficulty !== 2) return 700;
    if (this.thinkLevel === 0) return 250;
    if (this.thinkLevel === 2) return 1400;
    return 800;
  }

  thinkLabel(): string {
    if (this.thinkLevel === 0) return '快';
    if (this.thinkLevel === 2) return '深度';
    return '标准';
  }

  loopAi(): void {
    if (this.engine.turn === this.mySeat || this.engine.roundOver) {
      this.refresh();
      return;
    }
    if (this.online && !this.amHost) return;
    if (!this.engine.isAi[this.engine.turn]) return;
    setTimeout(() => {
      this.engine.aiStep();
      if (this.online && this.amHost) this.pushState();
      this.refresh();
      this.loopAi();
    }, this.thinkDelay());
  }

  // ---------- 联机 ----------
  createRoom(diff: number): void {
    this.engine = new Engine();
    this.engine.difficulty = diff;
    this.mySeat = 0;
    this.online = true;
    this.amHost = true;
    this.remoteSeats = [];
    this.engine.newMatch(diff);
    for (let i = 0; i < 4; i++) this.engine.isAi[i] = i !== 0;
    const host = new LanHost();
    this.host = host;
    host.onInfo = (text: string) => { this.tip = text; };
    host.onJoin = (seat: number) => {
      this.remoteSeats.push(seat);
      this.engine.isAi[seat] = false;
      this.tip = '座位 ' + Engine.seatName(seat) + ' 已加入';
      host.sendTo(seat, JSON.stringify({ t: 'seat', seat: seat }));
      this.pushState();
      this.refresh();
    };
    host.onPlay = (seat: number, ids: number[]) => {
      const cards: Card[] = [];
      const h: Card[] = this.engine.hands[seat];
      for (let i = 0; i < h.length; i++) {
        if (ids.indexOf(h[i].id) >= 0) cards.push(h[i]);
      }
      const err: string = this.engine.playCards(seat, cards);
      if (err.length > 0) this.tip = '出牌无效：' + err;
      this.pushState();
      this.refresh();
      this.loopAi();
    };
    host.onPass = (seat: number) => {
      this.engine.doPass(seat);
      this.pushState();
      this.refresh();
      this.loopAi();
    };
    host.listen();
    host.announce();
    this.screen = 'table';
    this.picked = [];
    this.refresh();
    this.loopAi();
  }

  joinRoom(ip: string): void {
    if (ip.length < 5) { this.tip = 'IP 格式不对'; return; }
    const client = new LanClient();
    this.client = client;
    this.online = true;
    this.amHost = false;
    this.engine = new Engine();
    client.onInfo = (text: string) => { this.tip = text; this.refresh(); };
    client.onDisconnect = (reason: string) => {
      this.tip = reason;
      if (this.lastSeat > 0 && this.retryLeft < 5) {
        this.retryLeft = this.retryLeft + 1;
        setTimeout(() => {
          if (this.lastIp.length === 0) return;
          const c2 = new LanClient();
          c2.onInfo = (t2: string) => { this.tip = t2; this.refresh(); };
          c2.onSeat = (s2: number, r2: string) => {
            this.mySeat = s2;
            this.lastSeat = s2;
            if (r2.length > 0) this.lastRoom = r2;
          };
          c2.onState = (st: StateMsg) => { this.applyState(st); };
          this.client = c2;
          c2.connect(this.lastIp, this.lastRoom, this.lastSeat);
        }, 3000);
      }
    };
    client.onSeat = (seat: number, room: string) => {
      this.mySeat = seat;
      if (room.length > 0) this.lastRoom = room;
      this.lastSeat = seat;
      this.retryLeft = 0;
    };
    client.onState = (s: StateMsg) => { this.applyState(s); };
    this.lastIp = ip;
    client.connect(ip, this.lastRoom, this.lastSeat);
    this.screen = 'table';
    this.refresh();
  }

  applyState(s: StateMsg): void {
    this.engine.level = s.lvl;
    this.engine.teamLevel = [s.t0, s.t1];
    this.engine.roundNo = s.round;
    this.engine.turn = s.turn;
    this.engine.lastPlayer = s.lastSeat;
    this.engine.roundOver = s.over;
    this.engine.resultText = s.result;
    this.engine.finished = s.fin;
    this.engine.logs = s.logs;
    const myHand: Card[] = [];
    for (let i = 0; i < s.hand.length; i++) {
      const t: number[] = s.hand[i];
      const c = new Card(t[2], t[1]);
      c.id = t[0];
      c.wild = t[2] === 1 && t[1] === s.lvl;
      myHand.push(c);
    }
    for (let i = 0; i < 4; i++) {
      this.engine.hands[i] = [];
      if (i === this.mySeat) this.engine.hands[i] = myHand;
      else {
        for (let k = 0; k < s.seats[i].n; k++) this.engine.hands[i].push(new Card(0, 2));
      }
      this.engine.seatTag[i] = s.seats[i].tag;
    }
    if (s.last === null) this.engine.lastPlay = null;
    else {
      const p = new Play();
      p.type = s.last.type;
      p.key = s.last.key;
      p.power = s.last.power;
      p.size = s.last.size;
      const cs: Card[] = [];
      for (let i = 0; i < s.last.cards.length; i++) {
        const t: number[] = s.last.cards[i];
        const c = new Card(t[2], t[1]);
        c.id = t[0];
        cs.push(c);
      }
      p.cards = cs;
      this.engine.lastPlay = p;
    }
    this.refresh();
  }

  pushState(): void {
    if (this.host === null) return;
    for (let i = 0; i < this.remoteSeats.length; i++) {
      const seat: number = this.remoteSeats[i];
      this.host.sendTo(seat, this.buildState(seat));
    }
  }

  buildState(forSeat: number): string {
    const e = this.engine;
    const seats: Object[] = [];
    for (let i = 0; i < 4; i++) {
      seats.push({
        n: e.hands[i].length,
        tag: e.seatTag[i],
        ai: e.isAi[i],
        fin: e.isFinished(i)
      });
    }
    const hand: number[][] = [];
    for (let i = 0; i < e.hands[forSeat].length; i++) {
      const c: Card = e.hands[forSeat][i];
      hand.push([c.id, c.rank, c.suit]);
    }
    let last: Object | null = null;
    if (e.lastPlay !== null) {
      const cs: number[][] = [];
      for (let i = 0; i < e.lastPlay.cards.length; i++) {
        const c: Card = e.lastPlay.cards[i];
        cs.push([c.id, c.rank, c.suit]);
      }
      last = {
        type: e.lastPlay.type, key: e.lastPlay.key, power: e.lastPlay.power,
        size: e.lastPlay.size, cards: cs
      };
    }
    return JSON.stringify({
      t: 'state', lvl: e.level, t0: e.teamLevel[0], t1: e.teamLevel[1],
      round: e.roundNo, turn: e.turn, lastSeat: e.lastPlayer,
      over: e.roundOver, result: e.resultText, gain: 0,
      hand: hand, seats: seats, last: last, fin: e.finished, logs: e.logs
    });
  }

  // ---------- 界面数据 ----------
  refresh(): void {
    const e = this.engine;
    const handViews: CardView[] = [];
    const h: Card[] = e.hands[this.mySeat];
    for (let i = 0; i < h.length; i++) {
      handViews.push({
        id: h[i].id,
        text: h[i].isJoker() ? ('大'.length > 0 && h[i].rank === 16 ? '大王' : '小王')
          : (h[i].suitText() + h[i].rankText()),
        red: h[i].isRed(),
        wild: h[i].wild
      });
    }
    this.hand = handViews;
    const sv: SeatView[] = [];
    const seatDef: string[][] = [['乾·天行', '你'], ['兑·泽悦', '右家'], ['坤·厚德', '对家'], ['震·雷动', '左家']];

    for (let i = 1; i < 4; i++) {
      const seat: number = i === 2 ? 2 : (i === 1 ? 1 : 3);
      sv.push({
        name: i === 1 ? seatDef[1][0] : (i === 2 ? seatDef[2][0] : seatDef[3][0]),
        pos: i === 1 ? '右家' : (i === 2 ? '对家 · 队友' : '左家'),
        count: e.isFinished(seat) ? '已出完' : (e.hands[seat].length + ' 张'),
        tag: e.seatTag[seat],
        active: e.turn === seat
      });
    }
    this.seats = sv;

    const lc: CardView[] = [];
    if (e.lastPlay !== null) {
      for (let i = 0; i < e.lastPlay.cards.length; i++) {
        const c: Card = e.lastPlay.cards[i];
        lc.push({ id: c.id, text: c.isJoker() ? '王' : (c.suitText() + c.rankText()), red: c.isRed(), wild: c.wild });
      }
      this.lastTag = e.lastPlay.typeName() + ' · ' + Engine.seatName(e.lastPlayer);
    } else {
      this.lastTag = '';
    }
    this.lastCards = lc;
    this.headLine = '第 ' + e.roundNo + ' 局 · 打 ' + Engine.levelText(e.level)
      + '    我方 ' + Engine.levelText(e.teamLevel[0]) + ' | 对方 ' + Engine.levelText(e.teamLevel[1]);
    this.logLine = e.logs.length > 0 ? e.logs[0] : '';
    this.over = e.roundOver;
    this.result = e.resultText;
    this.tick = this.tick + 1;
  }

  toggle(id: number): void {
    if (this.engine.turn !== this.mySeat) return;
    const idx: number = this.picked.indexOf(id);
    if (idx >= 0) this.picked.splice(idx, 1);
    else this.picked.push(id);
    this.refresh();
  }

  doPlay(): void {
    if (this.picked.length === 0) { this.tip = '请先选牌'; this.refresh(); return; }
    if (this.online && !this.amHost) {
      if (this.client !== null) this.client.send({ t: 'play', cards: this.picked.slice() });
      this.picked = [];
      this.refresh();
      return;
    }
    const h: Card[] = this.engine.hands[this.mySeat];
    const cards: Card[] = [];
    for (let i = 0; i < h.length; i++) if (this.picked.indexOf(h[i].id) >= 0) cards.push(h[i]);
    const err: string = this.engine.playCards(this.mySeat, cards);
    if (err.length > 0) { this.tip = err; this.refresh(); return; }
    this.picked = [];
    if (this.online && this.amHost) this.pushState();
    this.refresh();
    this.loopAi();
  }

  doPass(): void {
    if (this.engine.lastPlay === null) { this.tip = '你是首家，必须出牌'; this.refresh(); return; }
    if (this.online && !this.amHost) {
      if (this.client !== null) this.client.send({ t: 'pass' });
      this.picked = [];
      this.refresh();
      return;
    }
    this.engine.doPass(this.mySeat);
    this.picked = [];
    if (this.online && this.amHost) this.pushState();
    this.refresh();
    this.loopAi();
  }

  doHint(): void {
    const h: Card[] = this.engine.hands[this.mySeat];
    for (let i = 0; i < h.length; i++) {
      const p = Rules.evaluateExact([h[i]], this.engine.level);
      if (p !== null && Rules.canBeat(this.engine.lastPlay, p)) {
        this.picked = [h[i].id];
        this.refresh();
        return;
      }
    }
    this.tip = '没有能压过的牌';
    this.refresh();
  }

  doSort(): void {
    const lv: number = this.engine.level;
    this.engine.hands[this.mySeat].sort((a: Card, b: Card): number => {
      const wa: number = Rules.weight(a.rank, lv);
      const wb: number = Rules.weight(b.rank, lv);
      return wa < wb ? 1 : (wa > wb ? -1 : 0);
    });
    this.refresh();
  }

  nextRound(): void {
    if (this.online && !this.amHost) { this.screen = 'menu'; return; }
    if (this.engine.resultText.indexOf('打 A 成功') >= 0) { this.screen = 'menu'; return; }
    this.engine.deal();
    this.picked = [];
    this.refresh();
    if (this.online && this.amHost) this.pushState();
    this.loopAi();
  }

  // ---------- UI ----------
  cardChip(cv: CardView): Column {
    Column() {
      Text(cv.text).fontSize(15).fontWeight(FontWeight.Medium)
        .fontColor(cv.red ? '#B4473E' : '#23282F')
      if (cv.wild) {
        Text('配').fontSize(9).fontColor('#C8A96A')
      }
    }
    .width(46).height(70)
    .justifyContent(FlexAlign.Center)
    .backgroundColor('#F3F0E8')
    .borderRadius(5)
    .borderWidth(cv.wild ? 1.5 : 1)
    .borderColor(cv.wild ? '#C8A96A' : '#00000022')
    .onClick(() => { this.toggle(cv.id); })
  }

  @Builder
  actionBtn(label: string, primary: boolean, onTap: () => void) {
    Text(label)
      .fontSize(14)
      .fontColor(primary ? '#101315' : '#E9E4D8')
      .width(primary ? 96 : 74).height(38)
      .textAlign(TextAlign.Center)
      .backgroundColor(primary ? this.accent : '#141A20')
      .borderRadius(5)
      .borderWidth(1)
      .borderColor(primary ? this.accent : '#33FFFFFF')
      .onClick(onTap)
  }

  build() {
    Column() {
      if (this.screen === 'menu') {
        Column() {
          Row().width('90%').height(1).backgroundColor('#33C8A96A').margin({ top: 10 })
          Text('掼 蛋').fontSize(34).fontColor('#C8A96A').letterSpacing(14).margin({ top: 18 })
          Text('G U A N D A N   ·   S O L O').fontSize(10).fontColor('#8A939E').margin({ top: 6 })
          Row() {
            this.menuCard('初 级', '木 · 生发', '#4E8E6A', () => { this.startLocal(0); })
            this.menuCard('高 级', '土 · 承载', '#C8A96A', () => { this.startLocal(1); })
            this.menuCard('终 极', '水 · 通玄', '#4C6E96', () => { this.startLocal(2); })
          }.width('94%').justifyContent(FlexAlign.SpaceBetween).margin({ top: 18 })

          Row() {
            Text('创建房间 · 房主').fontSize(13).fontColor('#E9E4D8')
              .width(170).height(38).textAlign(TextAlign.Center)
              .backgroundColor('#141A20').borderRadius(5).borderWidth(1).borderColor('#44000000')
              .onClick(() => { this.createRoom(1); })
            Text('加入房间').fontSize(13).fontColor('#E9E4D8')
              .width(170).height(38).textAlign(TextAlign.Center)
              .backgroundColor('#141A20').borderRadius(5).borderWidth(1).borderColor('#44000000')
              .onClick(() => { this.showIp = true; })
          }.width('94%').justifyContent(FlexAlign.SpaceAround).margin({ top: 14 })

          if (this.showIp) {
            Column() {
              TextInput({ placeholder: '房主 IP，如 192.168.1.8' })
                .onChange((v: string) => { this.ipText = v; })
                .width('70%')
              Row() {
                Text('连接').fontSize(13).fontColor('#C8A96A').onClick(() => {
                  this.showIp = false;
                  this.joinRoom(this.ipText);
                }).margin({ right: 24 })
                Text('取消').fontSize(13).fontColor('#8A939E').onClick(() => { this.showIp = false; })
              }.margin({ top: 10 })
            }.margin({ top: 10 })
          }

          if (this.tip.length > 0) {
            Text(this.tip).fontSize(11).fontColor('#88FFFFFF').margin({ top: 10 })
          }
          Text('标准江苏掼蛋 · 两副牌 · 逢人配 · 局域端口 48620')
            .fontSize(10).fontColor('#66FFFFFF').margin({ top: 12 })
          Text('AI 思考：' + this.thinkLabel() + '（点击切换）')
            .fontSize(10).fontColor('#99C8A96A').margin({ top: 8 })
            .onClick(() => {
              this.thinkLevel = (this.thinkLevel + 1) % 3;
              Ai.thinkLevel = this.thinkLevel;
              this.tip = 'AI 思考：' + this.thinkLabel();
            })
        }.width('100%').height('100%').justifyContent(FlexAlign.Center)
      } else {
        Column() {
          Row() {
            Text('掼 蛋').fontSize(14).fontColor(this.accent)
            Text(this.headLine).fontSize(12).fontColor('#E9E4D8').margin({ left: 12 }).layoutWeight(1)
            Text('退出').fontSize(12).fontColor('#8A939E')
              .onClick(() => {
                if (this.host !== null) this.host.stop();
                if (this.client !== null) this.client.close();
                this.screen = 'menu';
                this.refresh();
              })
          }.width('100%').padding({ left: 14, right: 14, top: 8, bottom: 6 })

          Row().width('100%').justifyContent(FlexAlign.SpaceAround) {
            ForEach(this.seats, (s: SeatView) => {
              Column() {
                Text(s.name).fontSize(11).fontColor('#C8A96A')
                Text(s.pos).fontSize(8).fontColor('#8A939E')
                Text(s.count).fontSize(12).fontColor('#E9E4D8')
                Text(s.tag).fontSize(9).fontColor('#88FFFFFF')
              }
              .width(110).height(62)
              .backgroundColor(s.active ? '#1B242C' : '#141A20')
              .borderRadius(5).borderWidth(1)
              .borderColor(s.active ? this.accent : '#22FFFFFF')
            })
          }.margin({ top: 4 })

          Column() {
            Text(this.lastTag).fontSize(10).fontColor('#88FFFFFF')
            Row() {
              ForEach(this.lastCards, (cv: CardView) => {
                Text(cv.text).fontSize(13).fontColor(cv.red ? '#B4473E' : '#23282F')
                  .width(34).height(48).textAlign(TextAlign.Center)
                  .backgroundColor('#F3F0E8').borderRadius(4).margin({ right: -8 })
              })
            }
            Text(this.logLine).fontSize(10).fontColor('#88FFFFFF').margin({ top: 6 })
          }.height(130).justifyContent(FlexAlign.Center)

          Row().layoutWeight(1)

          Text(this.tip).fontSize(11).fontColor('#C8A96A')
          Scroll() {
            Row() {
              ForEach(this.hand, (cv: CardView) => {
                Column() {
                  Text(cv.text).fontSize(16).fontWeight(FontWeight.Medium)
                    .fontColor(cv.red ? '#B4473E' : '#23282F')
                  if (cv.wild) Text('配').fontSize(9).fontColor('#C8A96A')
                }
                .width(58).height(86)
                .justifyContent(FlexAlign.Center)
                .backgroundColor('#F3F0E8')
                .borderRadius(6)
                .borderWidth(this.picked.indexOf(cv.id) >= 0 ? 2 : 1)
                .borderColor(this.picked.indexOf(cv.id) >= 0 ? '#C8A96A' : '#00000022')
                .margin({ right: -26 })
                .translate({ y: this.picked.indexOf(cv.id) >= 0 ? -14 : 0 })
                .onClick(() => { this.toggle(cv.id); })
              })
            }.padding({ left: 12, right: 12 })
          }.scrollable(ScrollDirection.Horizontal).height(100).width('100%')

          Row() {
            this.actionBtn('出 牌', true, () => { this.doPlay(); })
            this.actionBtn('不 出', false, () => { this.doPass(); })
            this.actionBtn('提 示', false, () => { this.doHint(); })
            this.actionBtn('排 序', false, () => { this.doSort(); })
          }.width('100%').justifyContent(FlexAlign.SpaceEvenly).padding({ bottom: 10 })
        }.width('100%').height('100%')

        if (this.over) {
          Column() {
            Text('本 局 结 束').fontSize(15).fontColor(this.accent).letterSpacing(6)
            Text(this.result).fontSize(12).fontColor('#E9E4D8').margin({ top: 10 }).textAlign(TextAlign.Center)
            Text('继续').fontSize(13).fontColor('#101315')
              .width(120).height(36).textAlign(TextAlign.Center)
              .backgroundColor(this.accent).borderRadius(5).margin({ top: 16 })
              .onClick(() => { this.nextRound(); })
          }
          .width('70%').padding(20)
          .backgroundColor('#F0141A20')
          .borderRadius(8).borderWidth(1).borderColor('#66C8A96A')
        }
      }
    }
    .width('100%').height('100%')
    .backgroundColor('#0A0C0F')
  }

  @Builder
  menuCard(title: string, sub: string, color: string, onTap: () => void) {
    Column() {
      Text(title).fontSize(17).fontColor(color).letterSpacing(4)
      Text(sub).fontSize(10).fontColor('#AAFFFFFF').margin({ top: 6 })
    }
    .width('31%').height(110).justifyContent(FlexAlign.Center)
    .backgroundColor('#141A20').borderRadius(6).borderWidth(1).borderColor(color + '80')
    .onClick(onTap)
  }
}
